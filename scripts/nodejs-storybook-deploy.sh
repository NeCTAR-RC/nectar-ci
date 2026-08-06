#!/bin/bash
# nodejs-storybook-deploy: invoked via `!include-raw-escape:` from data/builder-macros.yaml.
# set -ex preserves the `sh -xe` Jenkins applied to the previous inline step.
# pipefail matters for the piped listings and uploads below: without it a
# failed `find` or `swift list` still exits 0 through `sort`/`xargs`, and an
# empty listing reaching the expiry pass would schedule live files for
# deletion.
set -ex
set -o pipefail

export PATH=~/nodejs-bin/:$PATH

pnpm build-storybook

CONTAINER="$DEPLOY_CONTAINER"

export OS_AUTH_URL=https://identity.rc.nectar.org.au/v3/
export OS_AUTH_TYPE=v3applicationcredential
export OS_APPLICATION_CREDENTIAL_ID=$CREDENTIAL_ID
export OS_APPLICATION_CREDENTIAL_SECRET=$CREDENTIAL_SECRET

openstack container create "$CONTAINER" >/dev/null
swift post "$CONTAINER" \
  --header 'X-Container-Meta-Web-Index: index.html' \
  --header 'X-Container-Read: .r:*,.rlistings'

cd "$WORKSPACE/storybook-static"

# A build that exits 0 but leaves this directory empty must not reach the
# expiry pass, which would classify the whole live site as orphaned.
test -f index.html

# Storybook fingerprints its bundles into these dirs, so their objects are
# immutable; everything else (the entry HTML/JSON at the root) must
# revalidate so browsers pick up new bundle hashes.
IMMUTABLE_DIRS=(assets sb-addons sb-common-assets sb-manager addon-visual-tests-assets components)

# Upload immutable dirs first so fresh HTML never points at a file that is
# not there yet. The re-upload of unchanged files is load-bearing: it clears
# any delete timer left on a file that was orphaned earlier and has since
# returned, so do not add --changed or --skip-identical here.
UPLOAD_DIRS=()
for DIR in "${IMMUTABLE_DIRS[@]}"; do
  [ -d "$DIR" ] && UPLOAD_DIRS+=("$DIR")
done
if [ "${#UPLOAD_DIRS[@]}" -gt 0 ]; then
  swift upload "$CONTAINER" "${UPLOAD_DIRS[@]}" \
    --header 'X-Detect-Content-Type: true' \
    --header 'Cache-Control: public, max-age=31536000, immutable'
fi

PRUNE_ARGS=()
for DIR in "${IMMUTABLE_DIRS[@]}"; do
  PRUNE_ARGS+=(-path "./$DIR" -prune -o)
done
find . "${PRUNE_ARGS[@]}" -type f -print0 |
  xargs -0 -r swift upload "$CONTAINER" \
    --header 'X-Detect-Content-Type: true' \
    --header 'Cache-Control: public, max-age=0, must-revalidate'

# Schedule for deletion in 90 days everything this build did not upload, so
# orphans from removed stories can expire once deploys stop touching them.
# This pass runs after the uploads: a failed upload aborts before any timer
# is set, and an expiry failure cannot leave the site undeployed. Each
# `swift post` is a fresh Python start plus a full Keystone auth, so the
# posts run concurrently; serially this pass dominated the whole job.
find . -type f -printf '%P\n' | LC_ALL=C sort >"$WORKSPACE/current-objects.txt"
swift list "$CONTAINER" | LC_ALL=C sort >"$WORKSPACE/existing-objects.txt"
LC_ALL=C comm -13 "$WORKSPACE/current-objects.txt" "$WORKSPACE/existing-objects.txt" |
  xargs -r -d '\n' -P 10 -I{} swift post "$CONTAINER" {} --header 'X-Delete-After: 7776000'
