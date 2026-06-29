#!/bin/bash
# jekyll-test-build-cleanup: invoked via `!include-raw-escape:` from data/builder-macros.yaml.
# set -ex preserves the `sh -xe` Jenkins applied to the previous inline step.
set -ex

export OS_AUTH_URL=https://identity.rc.nectar.org.au/v3/
export OS_AUTH_TYPE=v3applicationcredential
export OS_APPLICATION_CREDENTIAL_ID=$CREDENTIAL_ID
export OS_APPLICATION_CREDENTIAL_SECRET=$CREDENTIAL_SECRET
CONTAINER="jekyll-test-build-$GERRIT_CHANGE_ID-$GERRIT_PATCHSET_NUMBER"
openstack container delete --recursive "$CONTAINER"
