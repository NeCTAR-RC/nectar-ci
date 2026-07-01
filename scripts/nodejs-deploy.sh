#!/bin/bash
# nodejs-deploy: invoked via `!include-raw-escape:` from data/builder-macros.yaml.
# set -ex preserves the `sh -xe` Jenkins applied to the previous inline step.
set -ex

export PATH=~/nodejs-bin/:$PATH

# build:geo is a post-build step for generative engine optimization (GEO),
# run after the bundle is built.
pnpm build
pnpm build:geo

CONTAINER="$DEPLOY_CONTAINER"

export OS_AUTH_URL=https://identity.rc.nectar.org.au/v3/
export OS_AUTH_TYPE=v3applicationcredential
export OS_APPLICATION_CREDENTIAL_ID=$CREDENTIAL_ID
export OS_APPLICATION_CREDENTIAL_SECRET=$CREDENTIAL_SECRET

openstack container create "$CONTAINER" >/dev/null
swift post "$CONTAINER" \
  --header 'X-Container-Meta-Web-Index: index.html' \
  --header 'X-Container-Read: .r:*,.rlistings'

cd "$WORKSPACE/dist"

# Schedule existing assets for deletion in 90 days. Re-uploading below clears the timer on still-current files; orphans expire.
swift list "$CONTAINER" --prefix assets/ | while read -r OBJ; do
  swift post "$CONTAINER" "$OBJ" --header 'X-Delete-After: 7776000'
done

# Upload assets first so new HTML below references files that already exist.
swift upload "$CONTAINER" assets \
  --header 'X-Detect-Content-Type: true' \
  --header 'Cache-Control: public, max-age=31536000, immutable'

# must-revalidate so clients pick up fresh bundle hashes on next visit.
find . -maxdepth 1 -type f -print0 | xargs -0 -I{} \
  swift upload "$CONTAINER" {} \
    --header 'X-Detect-Content-Type: true' \
    --header 'Cache-Control: public, max-age=0, must-revalidate'
