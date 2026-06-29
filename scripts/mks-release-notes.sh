#!/bin/bash
set -euo pipefail

# Publish MKS deploy-time release notes to Swift. Runs post-merge
# (change-merged-event) on MKS/mks-argocd-{test,prod}: detects the
# targetRevision (chart/image version) bumps a merged change introduced,
# appends them to a per-environment JSON history kept in the Swift container,
# then re-renders one combined public HTML page from both environments.

export OS_AUTH_URL=https://identity.rc.nectar.org.au/v3/
export OS_AUTH_TYPE=v3applicationcredential
export OS_APPLICATION_CREDENTIAL_ID="$CREDENTIAL_ID"
export OS_APPLICATION_CREDENTIAL_SECRET="$CREDENTIAL_SECRET"

CONTAINER=mks-release-notes

# Environment derives from the Gerrit project so this macro takes no JJB
# parameters; that keeps the embedded Python heredoc free of brace-escaping.
case "${GERRIT_PROJECT:-}" in
  */mks-argocd-test) ENV="test"; OTHER="prod" ;;
  */mks-argocd-prod) ENV="prod"; OTHER="test" ;;
  *) echo "Unexpected project '${GERRIT_PROJECT:-}'"; exit 1 ;;
esac

# The diff this merged change introduced, restricted to apps/*.yaml.
REV="${GERRIT_PATCHSET_REVISION:-HEAD}"
git diff "${REV}^" "${REV}" -- 'apps/*.yaml' > "$WORKSPACE/change.diff" \
  || git diff HEAD~1 HEAD -- 'apps/*.yaml' > "$WORKSPACE/change.diff"

mkdir -p "$WORKSPACE/state"
swift download "$CONTAINER" "state/${ENV}.json" \
  -o "$WORKSPACE/state/${ENV}.json" 2>/dev/null || true
swift download "$CONTAINER" "state/${OTHER}.json" \
  -o "$WORKSPACE/state/${OTHER}.json" 2>/dev/null || true

export ENV OTHER
BUILD_TIMESTAMP="$(date -u +%Y-%m-%dT%H:%M:%SZ)"; export BUILD_TIMESTAMP

rm -f "$WORKSPACE/publish.flag"
python3 - <<'PY'
import json, os, re, sys, html

ws = os.environ["WORKSPACE"]
env = os.environ["ENV"]
other = os.environ["OTHER"]
ts = os.environ["BUILD_TIMESTAMP"]
commit = os.environ.get("GERRIT_PATCHSET_REVISION", "")
change_url = os.environ.get("GERRIT_CHANGE_URL", "")
change_no = os.environ.get("GERRIT_CHANGE_NUMBER", "")
subject = os.environ.get("GERRIT_CHANGE_SUBJECT", "")
owner = os.environ.get("GERRIT_CHANGE_OWNER_NAME", "")


def load(path):
    try:
        with open(path) as fh:
            data = json.load(fh)
            return data if isinstance(data, list) else []
    except (FileNotFoundError, ValueError):
        return []


# --- parse the unified diff for targetRevision changes ---
tr = re.compile(r'^([+-])\s*targetRevision:\s*"?([^"\s]+)"?\s*$')
cur = None
removed, added = {}, {}
with open(os.path.join(ws, "change.diff")) as fh:
    for line in fh:
        if line.startswith('+++ '):
            path = line[4:].strip()
            cur = path[2:] if path[:2] in ('a/', 'b/') else path
            continue
        m = tr.match(line.rstrip('\n'))
        if not m or cur is None:
            continue
        sign, ver = m.group(1), m.group(2)
        (removed if sign == '-' else added).setdefault(cur, []).append(ver)


def chart_info(appfile):
    """Best-effort chart + repoURL for context, by scanning the file."""
    chart = repo = ""
    try:
        with open(os.path.join(ws, appfile)) as fh:
            lines = fh.readlines()
    except FileNotFoundError:
        return chart, repo
    for i, ln in enumerate(lines):
        s = ln.strip()
        if s.startswith('- '):
            s = s[2:]
        if s.startswith('chart:') and not chart:
            chart = s.split(':', 1)[1].strip()
            for j in range(i, -1, -1):
                t = lines[j].strip()
                if t.startswith('- '):
                    t = t[2:]
                if t.startswith('repoURL:'):
                    repo = t.split(':', 1)[1].strip()
                    break
    return chart, repo


new_entries = []
for appfile in sorted(set(removed) | set(added)):
    olds = removed.get(appfile, [])
    news = added.get(appfile, [])
    seen = set()
    for old, new in zip(olds, news):
        if old == new or (old, new) in seen:
            continue
        seen.add((old, new))
        app = os.path.basename(appfile)
        app = app[:-5] if app.endswith('.yaml') else app
        chart, repo = chart_info(appfile)
        new_entries.append({
            "ts": ts, "env": env, "app": app, "chart": chart,
            "repoURL": repo, "old": old, "new": new,
            "commit": commit, "change_url": change_url,
            "change_no": change_no, "subject": subject, "owner": owner,
        })

if not new_entries:
    print("No targetRevision change in this merge; nothing to publish.")
    sys.exit(0)

state = load(os.path.join(ws, "state", env + ".json"))
existing = {(e.get("commit"), e.get("app"), e.get("new")) for e in state}
fresh = [e for e in new_entries
         if (e["commit"], e["app"], e["new"]) not in existing]
if not fresh:
    print("Entries already recorded for this commit; nothing to publish.")
    sys.exit(0)

state = fresh + state
with open(os.path.join(ws, "state", env + ".json"), "w") as fh:
    json.dump(state, fh, indent=2)

# --- render combined page from both environments ---
allrows = state + load(os.path.join(ws, "state", other + ".json"))
allrows.sort(key=lambda e: e.get("ts", ""), reverse=True)


def esc(x):
    return html.escape(str(x or ""))


rows = []
for e in allrows:
    ver = "%s &rarr; %s" % (esc(e.get("old")), esc(e.get("new")))
    chart = esc(e.get("chart")) or esc(e.get("app"))
    if e.get("repoURL"):
        chart = '<a href="%s">%s</a>' % (esc(e["repoURL"]), chart)
    change = esc(e.get("subject"))
    if e.get("change_url"):
        change = '<a href="%s">%s</a>' % (esc(e["change_url"]), change or esc(e.get("change_no")))
    rows.append(
        "<tr><td>%s</td><td><span class=\"env env-%s\">%s</span></td>"
        "<td>%s</td><td>%s</td><td class=\"ver\">%s</td><td>%s</td></tr>" % (
            esc(e.get("ts")), esc(e.get("env")), esc(e.get("env")),
            esc(e.get("app")), chart, ver, change))

TEMPLATE = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Nectar MKS deployment release notes</title>
<style>
body{font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;margin:2rem auto;max-width:80rem;padding:0 1rem;color:#1a1a1a}
h1{font-size:1.5rem} p.sub{color:#666;margin-top:-0.5rem}
table{border-collapse:collapse;width:100%}
th,td{text-align:left;padding:0.45rem 0.6rem;border-bottom:1px solid #e3e3e3;vertical-align:top;font-size:0.92rem}
th{background:#f6f8fa;position:sticky;top:0}
td.ver{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;white-space:nowrap}
.env{display:inline-block;padding:0.05rem 0.5rem;border-radius:1rem;font-size:0.8rem;font-weight:600}
.env-prod{background:#fde2e1;color:#a40000} .env-test{background:#e1ecfd;color:#0047a4}
a{color:#0047a4;text-decoration:none} a:hover{text-decoration:underline}
footer{margin-top:2rem;color:#888;font-size:0.8rem}
</style>
</head>
<body>
<h1>Nectar MKS deployment release notes</h1>
<p class="sub">Application version changes, newest first.</p>
<table>
<thead><tr><th>Deployed (UTC)</th><th>Env</th><th>Application</th><th>Chart</th><th>Version</th><th>Change</th></tr></thead>
<tbody>
__ROWS__
</tbody>
</table>
<footer>Generated automatically at deploy time. Last updated __UPDATED__.</footer>
</body>
</html>
"""

page = TEMPLATE.replace("__ROWS__", "\n".join(rows)).replace("__UPDATED__", esc(ts))
with open(os.path.join(ws, "index.html"), "w") as fh:
    fh.write(page)

open(os.path.join(ws, "publish.flag"), "w").close()
print("Recorded %d change(s); page rendered with %d total rows." % (len(fresh), len(allrows)))
PY

# Nothing to do when the merge carried no version change.
if [ ! -f "$WORKSPACE/publish.flag" ]; then
  exit 0
fi

cd "$WORKSPACE"
AUTH=$(openstack container create "$CONTAINER" -f value -c account)
swift post "$CONTAINER" \
  --header 'X-Container-Meta-Web-Index: index.html' \
  --header 'X-Container-Read: .r:*,.rlistings'
swift upload "$CONTAINER" index.html "state/${ENV}.json" \
  --header 'X-Detect-Content-Type: true' \
  --header 'Cache-Control: public, max-age=0, must-revalidate'
echo "Published: https://object-store.rc.nectar.org.au/v1/$AUTH/$CONTAINER/index.html"
