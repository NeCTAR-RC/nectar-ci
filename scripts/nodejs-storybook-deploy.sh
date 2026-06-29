#!/bin/bash
# nodejs-storybook-deploy: invoked via `!include-raw-escape:` from data/builder-macros.yaml.
# set -ex preserves the `sh -xe` Jenkins applied to the previous inline step.
set -ex

export PATH=~/nodejs-bin/:$PATH

pnpm build-storybook

CONTAINER="ardc-ui"

export OS_AUTH_URL=https://identity.rc.nectar.org.au/v3/
export OS_AUTH_TYPE=v3applicationcredential
export OS_APPLICATION_CREDENTIAL_ID=$CREDENTIAL_ID
export OS_APPLICATION_CREDENTIAL_SECRET=$CREDENTIAL_SECRET

openstack container create $CONTAINER >/dev/null
swift post $CONTAINER \
  --header 'X-Container-Meta-Web-Index: index.html' \
  --header 'X-Container-Read: .r:*,.rlistings'

cd "$WORKSPACE/storybook-static"

# Schedule everything for deletion in 90 days. Re-uploading below clears the
# timer on still-current files; orphans from removed stories expire on their own.
swift list "$CONTAINER" | while read -r OBJ; do
  swift post "$CONTAINER" "$OBJ" --header 'X-Delete-After: 7776000'
done

# Storybook fingerprints its bundles into assets/, sb-*/ and components/, so
# those are immutable; the entry HTML/JSON at the root must revalidate to pick
# up new bundle hashes. Upload immutable dirs first so fresh HTML never points
# at a file that isn't there yet.
for DIR in assets sb-addons sb-common-assets sb-manager addon-visual-tests-assets components; do
  [ -d "$DIR" ] && swift upload "$CONTAINER" "$DIR" \
    --header 'X-Detect-Content-Type: true' \
    --header 'Cache-Control: public, max-age=31536000, immutable'
done

find . -path ./assets -prune -o -path ./sb-addons -prune \
  -o -path ./sb-common-assets -prune -o -path ./sb-manager -prune \
  -o -path ./addon-visual-tests-assets -prune -o -path ./components -prune \
  -o -type f -print0 | xargs -0 -I{} \
  swift upload "$CONTAINER" {} \
    --header 'X-Detect-Content-Type: true' \
    --header 'Cache-Control: public, max-age=0, must-revalidate'
