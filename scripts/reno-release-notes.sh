#!/bin/bash
set -euo pipefail

# Build reno release notes and publish them to a Swift static website.
# Runs on tag creation (ref-updated-event on refs/tags/*).
# `tox -e releasenotes` renders the Sphinx HTML into
# releasenotes/build/html.
#
# The Swift container is derived from the Gerrit project at runtime
# ($GERRIT_PROJECT = <org>/<name>), so this macro takes no JJB parameters.
# This mirrors the mks-release-notes pattern and reproduces the
# `{name}-release-notes` container the template used to substitute.

# reno groups notes by walking the full git history and every tag, so
# make sure all tags are present in this (freshly wiped) checkout.
git fetch --tags --force origin

tox -e releasenotes

export OS_AUTH_URL=https://identity.rc.nectar.org.au/v3/
export OS_AUTH_TYPE=v3applicationcredential
export OS_APPLICATION_CREDENTIAL_ID="$CREDENTIAL_ID"
export OS_APPLICATION_CREDENTIAL_SECRET="$CREDENTIAL_SECRET"

CONTAINER="$(basename "$GERRIT_PROJECT")-release-notes"

cd "$WORKSPACE/releasenotes/build/html"
AUTH=$(openstack container create "$CONTAINER" -f value -c account)
swift post "$CONTAINER" \
  --header 'X-Container-Meta-Web-Index: index.html' \
  --header 'X-Container-Read: .r:*,.rlistings'

# Upload every rendered file to the container root (object name == path
# relative to releasenotes/build/html) so the static website resolves.
find . -type f | sed 's#^\./##' | while read -r OBJ; do
  swift upload "$CONTAINER" "$OBJ" \
    --header 'X-Detect-Content-Type: true' \
    --header 'Cache-Control: public, max-age=0, must-revalidate'
done
echo "Published: https://object-store.rc.nectar.org.au/v1/$AUTH/$CONTAINER/index.html"
