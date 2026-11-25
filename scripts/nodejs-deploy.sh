#!/bin/bash
# nodejs-deploy: invoked via `!include-raw-escape:` from data/builder-macros.yaml.
# set -ex preserves the `sh -xe` Jenkins applied to the previous inline step.
# pipefail makes a failed `find` fail the root-file upload below instead of
# exiting 0 through `xargs`.
# Old files are given a delete timer by the nodejs-swift-expire builder,
# which runs next.
set -ex
set -o pipefail

# build:geo is nectar-eligibility's post-build step for generative engine
# optimization (GEO), run after the bundle is built. Other projects have no
# such script, so it runs only if present.
pnpm build
pnpm run --if-present build:geo

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

# Upload assets first so new HTML below references files that already exist.
# Every file is uploaded again on every deploy, changed or not, because the
# upload clears the delete timer (X-Delete-At) on a file that left the build
# and has come back. Without it, that live file would still be deleted when
# its timer runs out. So do not add --changed or --skip-identical here.
SECONDS=0
swift upload "$CONTAINER" assets \
  --header 'X-Detect-Content-Type: true' \
  --header 'Cache-Control: public, max-age=31536000, immutable'

# must-revalidate so clients pick up fresh bundle hashes on next visit.
# -xtype f uploads a symlink to a file, as `swift upload` does inside a dir.
find . -maxdepth 1 -xtype f -print0 |
  xargs -0 -r swift upload "$CONTAINER" \
    --header 'X-Detect-Content-Type: true' \
    --header 'Cache-Control: public, max-age=0, must-revalidate'
echo "Uploads took $SECONDS seconds"
