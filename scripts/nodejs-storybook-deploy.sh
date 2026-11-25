#!/bin/bash
# nodejs-storybook-deploy: invoked via `!include-raw-escape:` from data/builder-macros.yaml.
# set -ex preserves the `sh -xe` Jenkins applied to the previous inline step.
# pipefail makes a failed `find` fail the root-file upload below instead of
# exiting 0 through `xargs`.
# Old files are given a delete timer by the nodejs-swift-expire builder,
# which runs next.
set -ex
set -o pipefail

pnpm build-storybook

CONTAINER="$DEPLOY_CONTAINER"

export OS_AUTH_URL=https://identity.rc.nectar.org.au/v3/
export OS_AUTH_TYPE=v3applicationcredential
export OS_APPLICATION_CREDENTIAL_ID=$CREDENTIAL_ID
export OS_APPLICATION_CREDENTIAL_SECRET=$CREDENTIAL_SECRET

# `swift post` also creates the container when it is missing.
swift post "$CONTAINER" \
  --header 'X-Container-Meta-Web-Index: index.html' \
  --header 'X-Container-Read: .r:*,.rlistings'

cd "$WORKSPACE/${DEPLOY_BUILD_DIR:?must name the build output folder}"

# A build that exits 0 but leaves this folder empty must not deploy.
test -f index.html

# Storybook fingerprints its bundles into these dirs, so their objects are
# immutable; everything else (the entry HTML/JSON at the root) must
# revalidate so browsers pick up new bundle hashes.
IMMUTABLE_DIRS=(assets sb-addons sb-common-assets sb-manager addon-visual-tests-assets components)

# Upload immutable dirs first so fresh HTML never points at a file that is
# not there yet. Every file is uploaded again on every deploy, changed or
# not, because the upload clears the delete timer (X-Delete-At) on a file
# that left the build and has come back. Without it, that live file would
# still be deleted when its timer runs out. So do not add --changed or
# --skip-identical here.
SECONDS=0
UPLOAD_DIRS=()
for DIR in "${IMMUTABLE_DIRS[@]}"; do
  [ -d "$DIR" ] && UPLOAD_DIRS+=("$DIR")
done
if [ "${#UPLOAD_DIRS[@]}" -gt 0 ]; then
  swift upload "$CONTAINER" "${UPLOAD_DIRS[@]}" \
    --header 'X-Detect-Content-Type: true' \
    --header 'Cache-Control: public, max-age=31536000, immutable'
fi

# -xtype f uploads a symlink to a file, as `swift upload` does inside a dir.
PRUNE_ARGS=()
for DIR in "${IMMUTABLE_DIRS[@]}"; do
  PRUNE_ARGS+=(-path "./$DIR" -prune -o)
done
find . "${PRUNE_ARGS[@]}" -xtype f -print0 |
  xargs -0 -r swift upload "$CONTAINER" \
    --header 'X-Detect-Content-Type: true' \
    --header 'Cache-Control: public, max-age=0, must-revalidate'
echo "Uploads took $SECONDS seconds"
