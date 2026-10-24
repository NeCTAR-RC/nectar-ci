#!/bin/bash
set -euo pipefail

# Publish a rendered HTML docs tree to a Swift container. Runs post-merge
# (change-merged-event) after a builder has rendered the docs into
# $WORKSPACE/$DOCS_DIR (e.g. puppet-strings -> doc, zensical -> site).
#
# JJB params (injected by the swift-docs-publish builder macro):
#   CONTAINER - Swift container to upload into
#   DOCS_DIR  - directory under $WORKSPACE holding the rendered HTML
#   DELETE_AFTER - seconds until uploaded objects expire (empty: never)
#
# The container is intentionally left private (no `swift post` ACL/web-index
# metadata is set).

export OS_AUTH_URL=https://identity.rc.nectar.org.au/v3/
export OS_AUTH_TYPE=v3applicationcredential
export OS_APPLICATION_CREDENTIAL_ID="$CREDENTIAL_ID"
export OS_APPLICATION_CREDENTIAL_SECRET="$CREDENTIAL_SECRET"

cd "$WORKSPACE"
openstack container create "$CONTAINER"

# Upload every rendered file from $WORKSPACE so the $DOCS_DIR/ prefix is kept
# in the object name (e.g. doc/index.html).
if [ -n "${DELETE_AFTER:-}" ]; then
    swift upload "$CONTAINER" "$DOCS_DIR" \
        --header "X-Delete-After: $DELETE_AFTER"
else
    find "$DOCS_DIR" -type f | parallel -n50 openstack object create "$CONTAINER"
fi
