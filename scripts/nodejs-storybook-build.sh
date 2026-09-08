#!/bin/bash
# nodejs-storybook-build: invoked via `!include-raw-escape:` from data/builder-macros.yaml.
# set -ex preserves the `sh -xe` Jenkins applied to the previous inline step.
# pipefail matters for the piped upload below: without it a failed `find`
# still exits 0 through `xargs` and the job reports a preview it never made.
set -ex
set -o pipefail

export PATH=~/nodejs-bin/:$PATH

CONTAINER="review-$GERRIT_CHANGE_ID-$GERRIT_PATCHSET_NUMBER"

export OS_AUTH_URL=https://identity.rc.nectar.org.au/v3/
export OS_AUTH_TYPE=v3applicationcredential
export OS_APPLICATION_CREDENTIAL_ID=$CREDENTIAL_ID
export OS_APPLICATION_CREDENTIAL_SECRET=$CREDENTIAL_SECRET
openstack container delete --recursive "$CONTAINER" || true
AUTH=$(openstack container create "$CONTAINER" -f value -c account)
BASEURL=v1/$AUTH/$CONTAINER

pnpm build-storybook

swift post "$CONTAINER" \
  --header 'X-Container-Meta-Web-Index: index.html' \
  --header 'X-Container-Read: .r:*,.rlistings'

cd "$WORKSPACE/storybook-static"

# no-store so reviewers always see the latest patchset. One bulk upload, not
# one per file: each swift invocation is a Python start plus a Keystone auth
# (~4s), and per-file uploads of a ~400-file preview dominated the whole job.
find . -type f -print0 | xargs -0 -r \
  swift upload "$CONTAINER" \
    --header 'X-Detect-Content-Type: true' \
    --header 'Cache-Control: no-store'

set +x
SWIFTURL=https://object-store.rc.nectar.org.au/$BASEURL
curl -sSf --user "$GERRIT_API" -X POST -H 'Content-Type: application/json' -d "{'message': 'Storybook preview at: $SWIFTURL/index.html'}" https://review.rc.nectar.org.au/a/changes/"$GERRIT_CHANGE_ID"/revisions/"$GERRIT_PATCHSET_NUMBER"/review
