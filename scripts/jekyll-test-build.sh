#!/bin/bash
# jekyll-test-build: invoked via `!include-raw-escape:` from data/builder-macros.yaml.
# set -ex preserves the `sh -xe` Jenkins applied to the previous inline step.
set -ex

export OS_AUTH_URL=https://identity.rc.nectar.org.au/v3/
export OS_AUTH_TYPE=v3applicationcredential
export OS_APPLICATION_CREDENTIAL_ID=$CREDENTIAL_ID
export OS_APPLICATION_CREDENTIAL_SECRET=$CREDENTIAL_SECRET
CONTAINER="jekyll-test-build-$GERRIT_CHANGE_ID-$GERRIT_PATCHSET_NUMBER"
openstack container delete --recursive "$CONTAINER" || true
AUTH=$(openstack container create "$CONTAINER" -f value -c account)
BASEURL=v1/$AUTH/$CONTAINER
bundle install --path vendor/bundle
sed -i 's#^    permalink:.*#    permalink: /:collection/:name:output_ext#g' _config.yml  # hack for .html ext in swift
bundle exec jekyll build --baseurl="/$BASEURL"
set +x
OS_TOKEN=$(openstack token issue -c id -f value)
SWIFTURL=https://object-store.rc.nectar.org.au/$BASEURL
tar -C "$WORKSPACE/_site" -zcf - . | curl -sSf -X PUT -H "X-Auth-Token: $OS_TOKEN" -H "X-Detect-Content-Type: true" -H "X-Delete-After: 7776000" --data-binary @- "$SWIFTURL/?extract-archive=tar.gz"
curl -sSf -X POST -H "X-Auth-Token: $OS_TOKEN" -H 'X-Container-Meta-Web-Index: index.html' "$SWIFTURL"
curl -sSf -X POST -H "X-Auth-Token: $OS_TOKEN" -H 'X-Container-Meta-Web-Listings: true' "$SWIFTURL"
curl -sSf -X POST -H "X-Auth-Token: $OS_TOKEN" -H 'X-Container-Read: .r:*,.rlistings' "$SWIFTURL"
curl -sSf --user "$GERRIT_API" -X POST -H 'Content-Type: application/json' -d "{'message': 'Test at: $SWIFTURL'}" https://review.rc.nectar.org.au/a/changes/"$GERRIT_CHANGE_ID"/revisions/"$GERRIT_PATCHSET_NUMBER"/review
