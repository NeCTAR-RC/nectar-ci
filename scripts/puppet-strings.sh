#!/bin/bash
set -euo pipefail

# Generate Puppet reference documentation with puppet-strings and publish the
# rendered YARD HTML tree to a Swift container. Runs post-merge
# (change-merged-event) on internal/puppet-nectar master.
#
# No JJB params: invoked via `!include-raw-escape:`. puppet-strings is assumed
# to be preinstalled on the node. The container is intentionally left private
# (no `swift post` ACL/web-index metadata is set).

# Render YARD HTML into ./doc
puppet strings generate

export OS_AUTH_URL=https://identity.rc.nectar.org.au/v3/
export OS_AUTH_TYPE=v3applicationcredential
export OS_APPLICATION_CREDENTIAL_ID="$CREDENTIAL_ID"
export OS_APPLICATION_CREDENTIAL_SECRET="$CREDENTIAL_SECRET"

CONTAINER=puppet-nectar

cd "$WORKSPACE"
AUTH=$(openstack container create "$CONTAINER" -f value -c account)

# Upload every rendered file from $WORKSPACE so the doc/ prefix is kept in the
# object name (e.g. doc/index.html).
find doc -type f | parallel -n50 openstack object create "$CONTAINER"
echo "Uploaded docs to https://object-store.rc.nectar.org.au/v1/$AUTH/$CONTAINER/doc/index.html"
