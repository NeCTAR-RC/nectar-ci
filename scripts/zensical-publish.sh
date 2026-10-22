#!/bin/bash
set -euo pipefail

# Build the nectar-docs Zensical site and publish the rendered HTML tree to a
# Swift container. Runs post-merge (change-merged-event) on
# internal/nectar-docs master.
#
# No JJB params: invoked via `!include-raw-escape:`. uv is assumed to be
# preinstalled on the node. The container is intentionally left private (no
# `swift post` ACL/web-index metadata is set).

# Render HTML into ./site
uv sync --frozen
uv run zensical build --clean

export OS_AUTH_URL=https://identity.rc.nectar.org.au/v3/
export OS_AUTH_TYPE=v3applicationcredential
export OS_APPLICATION_CREDENTIAL_ID="$CREDENTIAL_ID"
export OS_APPLICATION_CREDENTIAL_SECRET="$CREDENTIAL_SECRET"

CONTAINER=nectar-docs

cd "$WORKSPACE"
AUTH=$(openstack container create "$CONTAINER" -f value -c account)

# Upload every rendered file from $WORKSPACE so the site/ prefix is kept in the
# object name (e.g. site/index.html).
find site -type f | parallel -n50 openstack object create "$CONTAINER"
echo "Uploaded docs to https://object-store.rc.nectar.org.au/v1/$AUTH/$CONTAINER/site/index.html"
