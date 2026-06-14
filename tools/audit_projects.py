#!/usr/bin/env python3
"""Audit and clean up Jenkins jobs against the nectar-ci JJB config.

This tool compares the set of jobs that this JJB repo would render against the
jobs actually present on the running Jenkins, and helps clean up the drift:

  * orphans  jobs in Jenkins that JJB does not produce (delete from Jenkins)
  * stale    jobs idle for longer than a cutoff (remove from JJB and Jenkins)
  * report   read-only summary of orphans, stale and missing jobs

Gerrit is not involved: the comparison is purely Jenkins vs JJB.

The tool is report-only by default. Any deletion requires the --delete flag and
an interactive confirmation. Use --dry-run to force a no-mutation run even with
--delete.

See tools/README.md for setup and usage.
"""

import argparse
import configparser
import fnmatch
import glob
import os
import re
import subprocess
import sys
import tempfile
from datetime import datetime, timedelta, timezone

import requests
from ruamel.yaml import YAML

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_DATA_DIR = os.path.join(REPO_ROOT, "data")
DEFAULT_ALLOWLIST = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                 "audit_allowlist.txt")

# Matches the Gerrit SCM URL embedded in rendered job XML, e.g.
# ssh://jenkins@review.rc.nectar.org.au:29418/NeCTAR-RC/aardvark.git
GERRIT_URL_RE = re.compile(
    r"review\.rc\.nectar\.org\.au:29418/(?P<org>[^/]+)/(?P<name>[^<]+)\.git")

# A {placeholder} in a job/template name (JJB names allow hyphens, e.g.
# {name-suffix}).
PLACEHOLDER_RE = re.compile(r"\{([\w-]+)\}")


# ---------------------------------------------------------------------------
# JJB rendering
# ---------------------------------------------------------------------------

def _jenkins_jobs_bin():
    """Locate the jenkins-jobs executable, preferring one alongside this
    interpreter (so an unactivated venv still works), then PATH."""
    import shutil
    candidate = os.path.join(os.path.dirname(sys.executable), "jenkins-jobs")
    if os.path.isfile(candidate) and os.access(candidate, os.X_OK):
        return candidate
    found = shutil.which("jenkins-jobs")
    if found:
        return found
    raise SystemExit(
        "jenkins-jobs not found. Install jenkins-job-builder (see "
        "tools/README.md) or activate the virtualenv that provides it.")


def render_jjb_jobs(data_dir):
    """Render every JJB job to XML and return {job_name: project_or_None}.

    Uses `jenkins-jobs test -r <data_dir> -o <tmp>`, which writes one XML file
    per job (filename == job name). The Gerrit project is recovered from the
    git SCM <url> in each XML; jobs without a standard git SCM (pipeline, helm,
    image builds) map to None.
    """
    with tempfile.TemporaryDirectory(prefix="jjb-render-") as outdir:
        cmd = [_jenkins_jobs_bin(), "test", "-r", data_dir, "-o", outdir]
        proc = subprocess.run(cmd, capture_output=True, text=True)
        if proc.returncode != 0:
            sys.stderr.write(proc.stderr)
            raise SystemExit(
                f"jenkins-jobs render failed (exit {proc.returncode}). "
                "Is jenkins-job-builder installed and is the data dir correct?")

        jobs = {}
        for fname in os.listdir(outdir):
            path = os.path.join(outdir, fname)
            if not os.path.isfile(path):
                continue
            project = None
            with open(path, encoding="utf-8", errors="replace") as fh:
                m = GERRIT_URL_RE.search(fh.read())
            if m:
                project = m.group("name")
            jobs[fname] = project
        return jobs


# ---------------------------------------------------------------------------
# Jenkins client
# ---------------------------------------------------------------------------

class JenkinsClient:
    """Thin Jenkins REST client built around the credentials in jenkins_jobs.ini."""

    def __init__(self, url, user, password):
        self.url = url.rstrip("/")
        self.session = requests.Session()
        self.session.auth = (user, password)
        self._crumb = None

    def _crumb_header(self):
        """Return a CSRF crumb header dict, or {} if crumbs are disabled."""
        if self._crumb is None:
            try:
                resp = self.session.get(
                    f"{self.url}/crumbIssuer/api/json", timeout=30)
                if resp.status_code == 200:
                    data = resp.json()
                    self._crumb = {data["crumbRequestField"]: data["crumb"]}
                else:
                    self._crumb = {}
            except (requests.RequestException, ValueError, KeyError):
                self._crumb = {}
        return self._crumb

    def list_jobs(self):
        """Return [{name, url, last_build}] for every top-level job.

        last_build is a tz-aware datetime, or None if the job has never built.
        A single tree query fetches names and last-build timestamps together.
        """
        tree = "jobs[name,url,lastBuild[timestamp]]"
        resp = self.session.get(
            f"{self.url}/api/json", params={"tree": tree}, timeout=120)
        resp.raise_for_status()
        out = []
        for job in resp.json().get("jobs", []):
            last = job.get("lastBuild")
            ts = None
            if last and last.get("timestamp"):
                ts = datetime.fromtimestamp(
                    last["timestamp"] / 1000.0, tz=timezone.utc)
            out.append({"name": job["name"], "url": job["url"], "last_build": ts})
        return out

    def delete_job(self, job_url):
        """Delete a job via POST <job_url>doDelete. Returns True on success."""
        resp = self.session.post(
            f"{job_url.rstrip('/')}/doDelete",
            headers=self._crumb_header(), allow_redirects=False, timeout=60)
        # Jenkins returns 302 (redirect to the now-empty parent) on success.
        return resp.status_code in (200, 302)


def load_jenkins_config(path_override=None):
    """Locate and parse jenkins_jobs.ini, returning (url, user, password)."""
    candidates = []
    if path_override:
        candidates.append(path_override)
    if os.environ.get("JENKINS_JOBS_INI"):
        candidates.append(os.environ["JENKINS_JOBS_INI"])
    candidates += [
        os.path.join(os.getcwd(), "jenkins_jobs.ini"),
        os.path.expanduser("~/.config/jenkins_jobs/jenkins_jobs.ini"),
        "/etc/jenkins_jobs/jenkins_jobs.ini",
    ]
    for path in candidates:
        if path and os.path.isfile(path):
            parser = configparser.ConfigParser()
            parser.read(path)
            if parser.has_section("jenkins"):
                sec = parser["jenkins"]
                url = sec.get("url")
                user = sec.get("user")
                password = sec.get("password")
                if url and user and password:
                    return url, user, password
            raise SystemExit(
                f"{path} has no usable [jenkins] url/user/password section.")
    raise SystemExit(
        "Could not find jenkins_jobs.ini. Pass --jenkins-config, set "
        "JENKINS_JOBS_INI, or place it at ~/.config/jenkins_jobs/ or "
        "/etc/jenkins_jobs/.")


# ---------------------------------------------------------------------------
# Allowlist
# ---------------------------------------------------------------------------

def load_allowlist(path):
    """Return a list of fnmatch globs for jobs intentionally unmanaged by JJB."""
    if not path or not os.path.isfile(path):
        return []
    globs = []
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            line = line.split("#", 1)[0].strip()
            if line:
                globs.append(line)
    return globs


def is_allowlisted(name, globs):
    return any(fnmatch.fnmatch(name, g) for g in globs)


# ---------------------------------------------------------------------------
# JJB YAML model: project -> jobs entries -> rendered job names (Case A/B)
# ---------------------------------------------------------------------------

def _subst(template, params):
    """Substitute {placeholder} tokens; leave unknown placeholders intact."""
    return PLACEHOLDER_RE.sub(
        lambda m: str(params[m.group(1)]) if m.group(1) in params
        else m.group(0),
        template)


def _entry_key_params(entry):
    """Normalise a `jobs:` list item to (template_name, params_dict)."""
    if isinstance(entry, str):
        return entry, {}
    if isinstance(entry, dict) and len(entry) == 1:
        key = next(iter(entry))
        val = entry[key]
        return key, (dict(val) if isinstance(val, dict) else {})
    return None, {}


class JjbModel:
    """Parses the JJB YAML to attribute rendered jobs to their source entry.

    For every `- project:` block, each `jobs:` entry is expanded to the set of
    concrete job names it produces (following `- job-group:` definitions one
    level deep). Expansions are cross-checked against the authoritative rendered
    set so the tool only ever edits entries whose mapping it has confirmed.
    """

    # job-template keys that are structural, not placeholder defaults.
    _RESERVED_TEMPLATE_KEYS = {"name", "id"}

    def __init__(self, data_dir):
        self.data_dir = data_dir
        self._yaml = YAML()
        self._yaml.preserve_quotes = True
        self.files = sorted(glob.glob(os.path.join(data_dir, "**", "*.yaml"),
                                      recursive=True))
        self.job_groups = {}     # group-name-template -> list of row entries
        self.templates = {}      # template id AND name -> template dict
        self.projects = []       # list of dicts: file, name, jobs (raw list)
        self._load()

    def _load(self):
        for path in self.files:
            try:
                with open(path, encoding="utf-8") as fh:
                    docs = list(self._yaml.load_all(fh))
            except Exception:  # noqa: BLE001 - skip non-JJB / unparseable YAML
                continue
            for doc in docs:
                if not isinstance(doc, list):
                    continue
                for item in doc:
                    if not isinstance(item, dict):
                        continue
                    if "job-group" in item:
                        grp = item["job-group"]
                        if isinstance(grp, dict) and "name" in grp:
                            self.job_groups[grp["name"]] = list(
                                grp.get("jobs", []) or [])
                    elif "job-template" in item:
                        tmpl = item["job-template"]
                        if isinstance(tmpl, dict) and "name" in tmpl:
                            # A template is referenced by its id (if any) and by
                            # its name; both resolve to the same definition.
                            self.templates[tmpl["name"]] = tmpl
                            if tmpl.get("id"):
                                self.templates[tmpl["id"]] = tmpl
                    elif "project" in item:
                        proj = item["project"]
                        if isinstance(proj, dict) and "name" in proj:
                            self.projects.append({
                                "file": path,
                                "name": proj["name"],
                                "jobs": list(proj.get("jobs", []) or []),
                            })

    def _template_defaults(self, tmpl):
        """Scalar placeholder defaults declared on a job-template (e.g.
        ``name-suffix: ''``)."""
        return {k: v for k, v in tmpl.items()
                if k not in self._RESERVED_TEMPLATE_KEYS
                and isinstance(v, (str, int, float, bool))}

    def _expand_entry(self, key, params, project_name):
        """Yield concrete job names produced by one project `jobs:` entry.

        Resolves through job-groups (one or more levels) and job-template
        id/name indirection, applying template-level placeholder defaults. The
        rendered *name* of a referenced template is what matters, which can
        differ from the id used to reference it.
        """
        base = {"name": project_name, **params}
        if key in self.job_groups:
            for row in self.job_groups[key]:
                row_key, row_params = _entry_key_params(row)
                if row_key is None:
                    continue
                yield from self._expand_entry(
                    row_key, {**params, **row_params}, project_name)
        elif key in self.templates:
            tmpl = self.templates[key]
            merged = {**self._template_defaults(tmpl), **base}
            yield _subst(tmpl["name"], merged)
        else:
            yield _subst(key, base)

    def build_attribution(self, rendered_jobs):
        """Map each rendered job name to the entries that produce it.

        Returns {job_name: [attr, ...]} where attr is a dict with keys:
            file, project, entry_index, is_group, expands_to (int)
        Only entries whose expansion is confirmed against `rendered_jobs` are
        recorded, keeping later edits safe.
        """
        rendered = set(rendered_jobs)
        attribution = {}
        for proj in self.projects:
            for idx, entry in enumerate(proj["jobs"]):
                key, params = _entry_key_params(entry)
                if key is None:
                    continue
                names = [n for n in self._expand_entry(key, params, proj["name"])
                         if "{" not in n]
                confirmed = [n for n in names if n in rendered]
                is_group = key in self.job_groups
                for name in confirmed:
                    attribution.setdefault(name, []).append({
                        "file": proj["file"],
                        "project": proj["name"],
                        "entry_index": idx,
                        "entry_key": key,
                        "is_group": is_group,
                        "expands_to": len(confirmed),
                    })
        return attribution

    @staticmethod
    def classify(job_name, attribution):
        """Return ('A', attr) if the job is safely removable from one local
        entry, else ('B', reason) for manual handling.

        Case A requires the job to be produced by exactly one project entry
        that expands to exactly one job, so removing that entry removes this
        job and nothing else. This holds even when the entry goes through a
        shared job-group: only the per-project entry is deleted, never the
        shared group definition.
        """
        attrs = attribution.get(job_name, [])
        if len(attrs) == 1 and attrs[0]["expands_to"] == 1:
            return "A", attrs[0]
        if not attrs:
            return "B", "no confirmed JJB source entry (pipeline/helm/image job?)"
        if len(attrs) > 1:
            return "B", (f"produced by {len(attrs)} entries "
                         f"({', '.join(a['project'] for a in attrs)})")
        a = attrs[0]
        return "B", (f"entry in {os.path.relpath(a['file'], REPO_ROOT)} "
                     f"(project '{a['project']}') expands to "
                     f"{a['expands_to']} jobs; removing it would drop the others")

    def remove_entry(self, attr):
        """Remove one `jobs:` entry (Case A) from its file, preserving format.

        Drops the whole `- project:` block if its `jobs:` list becomes empty.
        Returns a human-readable description of what changed.
        """
        path = attr["file"]
        with open(path, encoding="utf-8") as fh:
            doc = self._yaml.load(fh)
        # Find the matching project block by name.
        target = None
        for item in doc:
            if isinstance(item, dict) and "project" in item \
                    and item["project"].get("name") == attr["project"]:
                target = item
                break
        if target is None:
            raise RuntimeError(
                f"project '{attr['project']}' not found in {path}")
        jobs = target["project"]["jobs"]
        idx = attr["entry_index"]
        if idx >= len(jobs):
            raise RuntimeError(
                f"entry index {idx} out of range for project "
                f"'{attr['project']}' (file changed since analysis?)")
        # Self-check: the entry at this index must still be the one we mapped,
        # guarding against index drift when several entries are removed.
        actual_key, _ = _entry_key_params(jobs[idx])
        if actual_key != attr["entry_key"]:
            raise RuntimeError(
                f"entry at index {idx} in project '{attr['project']}' is "
                f"{actual_key!r}, expected {attr['entry_key']!r}; aborting to "
                "avoid removing the wrong entry")
        del jobs[idx]
        removed_block = False
        if len(jobs) == 0:
            doc.remove(target)
            removed_block = True
        with open(path, "w", encoding="utf-8") as fh:
            self._yaml.dump(doc, fh)
        rel = os.path.relpath(path, REPO_ROOT)
        if removed_block:
            return f"removed empty project '{attr['project']}' from {rel}"
        return f"removed 1 job entry from project '{attr['project']}' in {rel}"


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def prompt_each(prompt):
    """Per-item confirmation. Returns one of 'yes', 'no', 'all' or 'quit'.

    'all' approves this item and every remaining one without further prompts;
    'quit' stops, leaving all remaining items declined. Empty input defaults to
    'no'; EOF (e.g. a closed stdin) is treated as 'quit' so loops terminate.
    """
    try:
        reply = input(f"{prompt} [y]es/[n]o/[a]ll/[q]uit (default: n) ") \
            .strip().lower()
    except EOFError:
        return "quit"
    return {"y": "yes", "yes": "yes", "a": "all", "all": "all",
            "q": "quit", "quit": "quit"}.get(reply, "no")


def select_items(items, describe):
    """Prompt for each item and return the subset the user approved (in order).

    `describe(item)` builds the line shown in the prompt. 'all' approves this
    item and the rest; 'quit' declines the rest and stops.
    """
    chosen = []
    take_all = False
    for item in items:
        if take_all:
            chosen.append(item)
            continue
        action = prompt_each(f"  {describe(item)}")
        if action == "all":
            take_all = True
            chosen.append(item)
        elif action == "yes":
            chosen.append(item)
        elif action == "quit":
            break
        # 'no' -> skip this item, keep prompting
    return chosen


def delete_jobs(client, jenkins_by_name, names):
    """Delete each named job from Jenkins. Returns the number that failed."""
    failed = 0
    for name in names:
        if client.delete_job(jenkins_by_name[name]["url"]):
            print(f"  deleted {name}")
        else:
            failed += 1
            print(f"  FAILED  {name}")
    return failed


def fmt_age(dt, now):
    if dt is None:
        return "never built"
    days = (now - dt).days
    return f"{dt.date()} ({days}d ago)"


# ---------------------------------------------------------------------------
# Subcommands
# ---------------------------------------------------------------------------

def gather(args):
    """Render JJB, query Jenkins and compute the shared sets."""
    print("Rendering JJB jobs ...", file=sys.stderr)
    jjb = render_jjb_jobs(args.data_dir)
    print(f"  JJB renders {len(jjb)} jobs", file=sys.stderr)

    url, user, password = load_jenkins_config(args.jenkins_config)
    print(f"Querying Jenkins at {url} ...", file=sys.stderr)
    client = JenkinsClient(url, user, password)
    jenkins_jobs = client.list_jobs()
    print(f"  Jenkins has {len(jenkins_jobs)} jobs", file=sys.stderr)

    jjb_names = set(jjb)
    jenkins_by_name = {j["name"]: j for j in jenkins_jobs}
    jenkins_names = set(jenkins_by_name)

    return {
        "client": client,
        "jjb": jjb,
        "jjb_names": jjb_names,
        "jenkins_by_name": jenkins_by_name,
        "jenkins_names": jenkins_names,
        "orphans": sorted(jenkins_names - jjb_names),
        "missing": sorted(jjb_names - jenkins_names),
        "managed": sorted(jjb_names & jenkins_names),
    }


def cmd_report(args):
    data = gather(args)
    allowlist = load_allowlist(args.allowlist)
    now = datetime.now(timezone.utc)
    cutoff = now - timedelta(days=args.max_age_days)

    orphans = [o for o in data["orphans"] if not is_allowlisted(o, allowlist)]
    allowed = [o for o in data["orphans"] if is_allowlisted(o, allowlist)]

    stale = []
    for name in data["managed"]:
        job = data["jenkins_by_name"][name]
        last = job["last_build"]
        if last is not None and last < cutoff:
            stale.append((name, last))

    print(f"\n=== Drift report (cutoff {args.max_age_days} days) ===")
    print(f"JJB jobs:       {len(data['jjb_names'])}")
    print(f"Jenkins jobs:   {len(data['jenkins_names'])}")
    print(f"\nOrphans (in Jenkins, not in JJB): {len(orphans)}"
          f"  [+{len(allowed)} allowlisted]")
    for name in orphans:
        last = data["jenkins_by_name"][name]["last_build"]
        print(f"  - {name:60s} {fmt_age(last, now)}")
    print(f"\nStale (managed, idle > {args.max_age_days}d): {len(stale)}")
    for name, last in sorted(stale, key=lambda x: x[1]):
        print(f"  - {name:60s} {fmt_age(last, now)}")
    print(f"\nMissing (in JJB, not on Jenkins): {len(data['missing'])}")
    for name in data["missing"]:
        print(f"  - {name}")
    return 0


def cmd_orphans(args):
    data = gather(args)
    allowlist = load_allowlist(args.allowlist)
    now = datetime.now(timezone.utc)
    orphans = [o for o in data["orphans"] if not is_allowlisted(o, allowlist)]
    skipped = [o for o in data["orphans"] if is_allowlisted(o, allowlist)]

    print(f"\nOrphan jobs (in Jenkins, not produced by JJB): {len(orphans)}")
    for name in orphans:
        last = data["jenkins_by_name"][name]["last_build"]
        print(f"  - {name:60s} {fmt_age(last, now)}")
    if skipped:
        print(f"\n{len(skipped)} allowlisted job(s) skipped.")

    if not args.delete:
        print("\n(report only; pass --delete to remove these from Jenkins)")
        return 0
    if not orphans:
        print("\nNothing to delete.")
        return 0
    if args.dry_run:
        print("\n--dry-run: no jobs deleted.")
        return 0

    print("\nConfirm each orphan to delete from Jenkins (cannot be undone):")
    chosen = select_items(
        orphans,
        lambda n: "Delete {:60s} ({})?".format(
            n, fmt_age(data["jenkins_by_name"][n]["last_build"], now)))
    if not chosen:
        print("\nNo jobs selected; nothing deleted.")
        return 0
    failed = delete_jobs(data["client"], data["jenkins_by_name"], chosen)
    print(f"\nDeleted {len(chosen) - failed}/{len(chosen)} orphan job(s).")
    return 1 if failed else 0


def cmd_stale(args):
    data = gather(args)
    now = datetime.now(timezone.utc)
    cutoff = now - timedelta(days=args.max_age_days)

    stale = []
    for name in data["managed"]:
        last = data["jenkins_by_name"][name]["last_build"]
        if last is None:
            if args.include_never_built:
                stale.append((name, None))
        elif last < cutoff:
            stale.append((name, last))

    if not stale:
        print(f"No stale jobs (cutoff {args.max_age_days} days).")
        return 0

    model = JjbModel(args.data_dir)
    attribution = model.build_attribution(data["jjb_names"])

    case_a = []   # (name, last, attr)
    case_b = []   # (name, last, reason)
    for name, last in stale:
        kind, info = model.classify(name, attribution)
        if kind == "A":
            case_a.append((name, last, info))
        else:
            case_b.append((name, last, info))

    print(f"\nStale jobs (idle > {args.max_age_days}d): {len(stale)}")
    print(f"\nCase A - removable from a single JJB entry: {len(case_a)}")
    for name, last, _ in sorted(case_a, key=lambda x: (x[1] or now)):
        print(f"  - {name:60s} {fmt_age(last, now)}")
    print(f"\nCase B - manual handling (shared job-group / no local entry): "
          f"{len(case_b)}")
    for name, last, reason in sorted(case_b, key=lambda x: (x[1] or now)):
        print(f"  - {name:60s} {fmt_age(last, now)}")
        print(f"      {reason}")

    if not args.delete:
        print("\n(report only; pass --delete to remove Case A entries from JJB "
              "and delete stale jobs from Jenkins)")
        return 0
    if args.dry_run:
        print("\n--dry-run: no YAML edited, no jobs deleted.")
        return 0

    # Decide each stale job individually. A "yes" retires that job: for Case A
    # it removes the single JJB entry as well as deleting the Jenkins job; for
    # Case B only the Jenkins job goes (the JJB source needs manual editing).
    items = ([(name, last, "A", info) for name, last, info in case_a]
             + [(name, last, "B", info) for name, last, info in case_b])
    print("\nConfirm each stale job to retire (cannot be undone). For Case A "
          "this also removes its JJB entry.")
    chosen = select_items(
        items,
        lambda it: "Retire {:60s} ({}, Case {})?".format(
            it[0], fmt_age(it[1], now), it[2]))
    if not chosen:
        print("\nNothing selected.")
        return 0

    # 1) Edit YAML for the chosen Case A jobs. Remove higher entry indices first
    # so earlier removals do not shift the indices of later ones in the file.
    chosen_a = [(name, info) for name, _, kind, info in chosen if kind == "A"]
    for name, attr in sorted(chosen_a, key=lambda x: x[1]["entry_index"],
                             reverse=True):
        try:
            print(f"  {model.remove_entry(attr)}  ({name})")
        except Exception as exc:  # noqa: BLE001
            print(f"  FAILED to edit YAML for {name}: {exc}")
    if chosen_a:
        print("\nReview the YAML changes, then run:\n"
              "  pre-commit run --all-files\n"
              "  jenkins-jobs test -r data\n"
              "and submit with: git review")

    # 2) Delete the chosen jobs from Jenkins (both cases).
    chosen_names = [name for name, _, _, _ in chosen]
    failed = delete_jobs(data["client"], data["jenkins_by_name"], chosen_names)
    print(f"\nDeleted {len(chosen_names) - failed}/{len(chosen_names)} job(s).")

    chosen_b = [name for name, _, kind, _ in chosen if kind == "B"]
    if chosen_b:
        print(f"\nNote: {len(chosen_b)} Case B job(s) were deleted from Jenkins "
              "but NOT removed from JJB. They come from shared job-groups "
              "(usually an old release row) or have no local entry, so puppet "
              "will recreate them. Edit the job-group/template by hand if the "
              "whole release should be retired.")
    return 0


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def build_parser():
    parser = argparse.ArgumentParser(description=(__doc__ or "").splitlines()[0])
    parser.add_argument("--data-dir", default=DEFAULT_DATA_DIR,
                        help="JJB data directory (default: repo data/)")
    parser.add_argument("--jenkins-config",
                        help="Path to jenkins_jobs.ini (overrides search)")
    parser.add_argument("--allowlist", default=DEFAULT_ALLOWLIST,
                        help="Allowlist file of jobs not managed by JJB")
    parser.add_argument("--max-age-days", type=int, default=365,
                        help="Idle threshold for 'stale' (default: 365)")
    parser.add_argument("--dry-run", action="store_true",
                        help="Never mutate, even with --delete")

    sub = parser.add_subparsers(dest="command", required=True)

    p_report = sub.add_parser("report", help="Read-only drift summary")
    p_report.set_defaults(func=cmd_report)

    p_orphans = sub.add_parser("orphans",
                               help="Jobs in Jenkins but not in JJB")
    p_orphans.add_argument("--delete", action="store_true",
                           help="Delete orphans from Jenkins (asks first)")
    p_orphans.set_defaults(func=cmd_orphans)

    p_stale = sub.add_parser("stale", help="Jobs idle longer than the cutoff")
    p_stale.add_argument("--delete", action="store_true",
                         help="Remove Case A from JJB and delete stale jobs "
                              "from Jenkins (asks first)")
    p_stale.add_argument("--include-never-built", action="store_true",
                         help="Treat never-built jobs as stale (default: active)")
    p_stale.set_defaults(func=cmd_stale)
    return parser


def main(argv=None):
    args = build_parser().parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
