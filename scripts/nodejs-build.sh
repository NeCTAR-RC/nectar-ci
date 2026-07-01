#!/bin/bash
# nodejs-build: invoked via `!include-raw-escape:` from data/builder-macros.yaml.
# set -ex preserves the `sh -xe` Jenkins applied to the previous inline step.
set -ex

export PATH=~/nodejs-bin/:$PATH

CONTAINER="review-$GERRIT_CHANGE_ID-$GERRIT_PATCHSET_NUMBER"

export OS_AUTH_URL=https://identity.rc.nectar.org.au/v3/
export OS_AUTH_TYPE=v3applicationcredential
export OS_APPLICATION_CREDENTIAL_ID=$CREDENTIAL_ID
export OS_APPLICATION_CREDENTIAL_SECRET=$CREDENTIAL_SECRET
openstack container delete --recursive "$CONTAINER" || true
AUTH=$(openstack container create "$CONTAINER" -f value -c account)
BASEURL=v1/$AUTH/$CONTAINER

# build:geo is a post-build step for generative engine optimization (GEO),
# run after the bundle is built.
pnpm build --base="/$BASEURL/"
pnpm build:geo

swift post "$CONTAINER" \
  --header 'X-Container-Meta-Web-Index: index.html' \
  --header 'X-Container-Read: .r:*,.rlistings'

cd "$WORKSPACE/dist"

# no-store so reviewers always see the latest patchset.
swift upload "$CONTAINER" assets \
  --header 'X-Detect-Content-Type: true' \
  --header 'Cache-Control: no-store'

find . -maxdepth 1 -type f -print0 | xargs -0 -I{} \
  swift upload "$CONTAINER" {} \
    --header 'X-Detect-Content-Type: true' \
    --header 'Cache-Control: no-store'

set +x
SWIFTURL=https://object-store.rc.nectar.org.au/$BASEURL
curl -sSf --user "$GERRIT_API" -X POST -H 'Content-Type: application/json' -d "{'message': 'Test at: $SWIFTURL/'}" https://review.rc.nectar.org.au/a/changes/"$GERRIT_CHANGE_ID"/revisions/"$GERRIT_PATCHSET_NUMBER"/review
