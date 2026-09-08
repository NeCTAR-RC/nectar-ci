#!/bin/bash
# nodejs-build-clean: invoked via `!include-raw-escape:` from data/builder-macros.yaml.
# set -ex preserves the `sh -xe` Jenkins applied to the previous inline step.
set -ex

export OS_AUTH_URL=https://identity.rc.nectar.org.au/v3/
export OS_AUTH_TYPE=v3applicationcredential
export OS_APPLICATION_CREDENTIAL_ID=$CREDENTIAL_ID
export OS_APPLICATION_CREDENTIAL_SECRET=$CREDENTIAL_SECRET

# nodejs-build creates one container per patchset (review-<change-id>-<patchset>)
# and nodejs-storybook-build one per change (review-<change-id>), so sweep every
# container of this change: the prefix without a trailing dash matches both
# forms. Change-Ids are fixed-length, so one is never a prefix of another.
for CONTAINER in $(openstack container list --prefix "review-$GERRIT_CHANGE_ID" -f value -c Name); do
  openstack container delete --recursive "$CONTAINER"
done
