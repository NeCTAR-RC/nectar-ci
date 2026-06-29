#!/bin/bash
# nodejs-build-clean: invoked via `!include-raw-escape:` from data/builder-macros.yaml.
# set -ex preserves the `sh -xe` Jenkins applied to the previous inline step.
set -ex

export OS_AUTH_URL=https://identity.rc.nectar.org.au/v3/
export OS_AUTH_TYPE=v3applicationcredential
export OS_APPLICATION_CREDENTIAL_ID=$CREDENTIAL_ID
export OS_APPLICATION_CREDENTIAL_SECRET=$CREDENTIAL_SECRET

# nodejs-build creates one container per patchset (review-<change-id>-<patchset>),
# so clean up every patchset's container for this change, not just the latest.
for CONTAINER in $(openstack container list --prefix "review-$GERRIT_CHANGE_ID-" -f value -c Name); do
  openstack container delete --recursive "$CONTAINER"
done
