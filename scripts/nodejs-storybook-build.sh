#!/bin/bash
# nodejs-storybook-build: invoked via `!include-raw-escape:` from data/builder-macros.yaml.
# set -ex preserves the `sh -xe` Jenkins applied to the previous inline step.
# pipefail matters for the piped upload below: without it a failed `find`
# still exits 0 through `xargs` and the job reports a preview it never made.
set -ex
set -o pipefail

export PATH=~/nodejs-bin/:$PATH

# One container per change, not per patchset: the delete below wipes the
# previous patchset's preview, so an open change holds one container instead
# of one per patchset, and the preview URL stays stable across patchsets
# (no-store keeps its content current). Change-Ids are unique per review, so
# two reviews never share a container; Jenkins runs this job serially, so two
# patchsets of one change cannot upload over each other. The change-merged/
# abandoned clean job sweeps this name and the older per-patchset ones alike.
CONTAINER="review-$GERRIT_CHANGE_ID"

export OS_AUTH_URL=https://identity.rc.nectar.org.au/v3/
export OS_AUTH_TYPE=v3applicationcredential
export OS_APPLICATION_CREDENTIAL_ID=$CREDENTIAL_ID
export OS_APPLICATION_CREDENTIAL_SECRET=$CREDENTIAL_SECRET
# swift, not openstack: the per-change container really holds the previous
# patchset's ~400 objects now, and swiftclient deletes them on 10 concurrent
# threads where the openstack CLI works through them one at a time.
swift delete "$CONTAINER" || true
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
