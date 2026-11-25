#!/bin/bash
# nodejs-swift-expire: invoked via `!include-raw-escape:` from data/builder-macros.yaml.
# Runs after a deploy builder. It gives each file that has left the build a
# delete timer of RETENTION_DAYS (X-Delete-After), once, and Swift deletes
# the file when the timer runs out. Until then, pages that are still open can
# load the old files.
#
# The saved manifest is the object .deploy-manifest at the root of the same
# container: a plain list, one name per line, of the files in scope of the
# last deploy that finished this step. A file gets the timer now when it is
# in scope, is in the container listing, is not in this build, and either:
#   * is named in the manifest: it was live until this deploy; or
#   * is newer in the listing than the manifest: a deploy that failed after
#     its uploads left it behind. Everything a finished deploy uploaded or
#     gave a timer is older than the manifest that deploy then wrote.
# With no manifest (the first run, or a lost one), every file in scope that
# is not in this build gets the timer, and the new manifest repairs the gap.
# A file that got its timer at an earlier deploy is in neither group later,
# so its timer is never set again and keeps running down. A deploy that puts
# the file back in the build uploads it again, which clears the timer.
#
# The new manifest is written only after every timer is set. If this step
# fails earlier, the old manifest stays and the next deploy finds the same
# files through the two rules above.
#
# Inputs, set by the job template:
#   DEPLOY_CONTAINER     the Swift container.
#   DEPLOY_BUILD_DIR     the build output folder, relative to $WORKSPACE.
#   DEPLOY_EXPIRE_SCOPE  which files may get a timer: a folder prefix ending
#                        in `/` (such as `assets/`), or the word `container`
#                        for the whole container.
# The nodejs-deploy job uses `assets/` because its containers can hold files
# that no build contains, uploaded by hand, such as a live config file. The
# whole container is an explicit word, not an empty value, because Jenkins
# drops empty environment variables: a forgotten or empty setting fails here
# instead of widening the scope.
#
# Known limits:
#   * Within scope, a file uploaded by hand after the last deploy is not in
#     the build and is newer than the manifest, so it gets the timer too.
#     With scope `container`, that is any file uploaded by hand.
#   * "Older than the manifest" relies on Swift dating the manifest after the
#     timers set just before it. Swift dates a request with the clock of the
#     proxy that serves it, and the manifest upload starts a new `swift`
#     call, about a second later. If the proxies' clocks ever differed by
#     more than that, the next deploy would set those few timers once more.
#   * A file whose timer has run out can stay in the listing for a short
#     time before Swift removes it, and a post to it answers 404. That fails
#     this step until the file is gone. It needs a file with an old timer to
#     be due for a new one, which happens only with no manifest, or after a
#     failed run of this step and then RETENTION_DAYS without a deploy.
set -ex
set -o pipefail

CONTAINER="${DEPLOY_CONTAINER:?must name the Swift container}"
BUILD_DIR="${DEPLOY_BUILD_DIR:?must name the build output folder}"
SCOPE="${DEPLOY_EXPIRE_SCOPE:?must be a folder prefix ending in / or the word container}"
RETENTION_DAYS=90
RETENTION_SECONDS=$((RETENTION_DAYS * 24 * 60 * 60))
MANIFEST=.deploy-manifest

case "$SCOPE" in
  container) PREFIX="" ;;
  */) PREFIX="$SCOPE" ;;
  *)
    echo "DEPLOY_EXPIRE_SCOPE must be a folder prefix ending in / or the word container, not '$SCOPE'" >&2
    exit 1
    ;;
esac

export OS_AUTH_URL=https://identity.rc.nectar.org.au/v3/
export OS_AUTH_TYPE=v3applicationcredential
export OS_APPLICATION_CREDENTIAL_ID=$CREDENTIAL_ID
export OS_APPLICATION_CREDENTIAL_SECRET=$CREDENTIAL_SECRET

cd "$WORKSPACE/$BUILD_DIR"

# A build that exits 0 but leaves this folder empty would make every file in
# the container look as if it had left the build.
test -f index.html

WORK_DIR=$(mktemp -d)
trap 'rm -rf "$WORK_DIR"' EXIT

# -xtype f counts a symlink to a file, as `swift upload` uploads its target.
find . -xtype f -printf '%P\n' |
  awk -v prefix="$PREFIX" 'substr($0, 1, length(prefix)) == prefix' |
  LC_ALL=C sort >"$WORK_DIR/build.txt"

LIST_ARGS=()
if [ -n "$PREFIX" ]; then
  LIST_ARGS=(--prefix "$PREFIX")
fi
swift list "$CONTAINER" "${LIST_ARGS[@]}" --json >"$WORK_DIR/listing.json"

# With scope `container` the listing also holds the manifest itself, which
# must never get a timer. `.[]` reads the listing whether the client prints
# it as one array or one per page.
jq -r --arg manifest "$MANIFEST" '.[] | select(.name != $manifest) | .name' "$WORK_DIR/listing.json" |
  LC_ALL=C sort >"$WORK_DIR/listed.txt"
LC_ALL=C comm -13 "$WORK_DIR/build.txt" "$WORK_DIR/listed.txt" >"$WORK_DIR/left.txt"

swift list "$CONTAINER" --prefix "$MANIFEST" --json >"$WORK_DIR/manifest-listing.json"
MANIFEST_DATE=$(jq -r --arg manifest "$MANIFEST" '.[] | select(.name == $manifest) | .last_modified' "$WORK_DIR/manifest-listing.json")

if [ -n "$MANIFEST_DATE" ]; then
  swift download "$CONTAINER" "$MANIFEST" -o "$WORK_DIR/manifest.txt"
  LC_ALL=C sort -u "$WORK_DIR/manifest.txt" >"$WORK_DIR/was-live.txt"
  # Listing dates are UTC in ISO 8601 form, so a plain string comparison
  # orders them correctly.
  jq -r --arg since "$MANIFEST_DATE" '.[] | select(.last_modified > $since) | .name' "$WORK_DIR/listing.json" |
    LC_ALL=C sort >"$WORK_DIR/newer.txt"
  LC_ALL=C sort -u "$WORK_DIR/was-live.txt" "$WORK_DIR/newer.txt" >"$WORK_DIR/due.txt"
  LC_ALL=C comm -12 "$WORK_DIR/left.txt" "$WORK_DIR/due.txt" >"$WORK_DIR/timer.txt"
  MANIFEST_STATE="found, dated $MANIFEST_DATE"
else
  cp "$WORK_DIR/left.txt" "$WORK_DIR/timer.txt"
  MANIFEST_STATE="not found, so every file not in this build gets the timer"
fi

echo "Files in this build (scope $SCOPE): $(wc -l <"$WORK_DIR/build.txt")"
echo "Files in the container (scope $SCOPE): $(wc -l <"$WORK_DIR/listed.txt")"
echo "Files not in this build: $(wc -l <"$WORK_DIR/left.txt")"
echo "Saved manifest: $MANIFEST_STATE"
echo "Files that get the $RETENTION_DAYS-day delete timer now: $(wc -l <"$WORK_DIR/timer.txt")"

# `swift post` takes one object per call, and each call is a fresh Python
# start plus a Keystone login, so the posts run in parallel. Any failed post
# fails the step before the new manifest is written.
xargs -r -d '\n' -P 10 -I{} swift post "$CONTAINER" {} \
  --header "X-Delete-After: $RETENTION_SECONDS" <"$WORK_DIR/timer.txt"

swift upload "$CONTAINER" "$WORK_DIR/build.txt" --object-name "$MANIFEST" \
  --header 'Content-Type: text/plain' \
  --header 'Cache-Control: public, max-age=0, must-revalidate'

echo "Delete timer step took $SECONDS seconds"
