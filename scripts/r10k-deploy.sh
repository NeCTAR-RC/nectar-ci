#!/bin/bash
# r10k-deploy: invoked via `!include-raw-escape:` from data/builder-macros.yaml.
# set -ex preserves the `sh -xe` Jenkins applied to the previous inline step.
set -ex

# strips 'internal/puppet-site-nectar' to 'puppet-site-nectar'
REPO=$(echo "$GERRIT_PROJECT" | cut -f2 -d'/')
# Creates repo to hold r10k branches if it doesn't exist
respcode=$(curl -H "Authorization: token $TOKEN" "https://git.rc.nectar.org.au/api/v1/repos/r10k/${REPO}" -w "%{http_code}" -o /dev/null -s)
if [ "$respcode" = '404' ]; then
  curl -H "Authorization: token $TOKEN" https://git.rc.nectar.org.au/api/v1/org/r10k/repos -X POST -d "name=$REPO"
fi
# Adds r10k remote
git remote | grep 'r10k' > /dev/null && retval=0 || retval=1
if [ "$retval" -eq 1 ]; then
  git remote add r10k "git@git.rc.nectar.org.au:r10k/$REPO"
fi
git push r10k "HEAD:refs/heads/$GERRIT_PATCHSET_REVISION"
