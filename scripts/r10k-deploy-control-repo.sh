#!/bin/bash
# r10k-deploy-control-repo: invoked via `!include-raw-escape:` from data/builder-macros.yaml.
# set -ex preserves the `sh -xe` Jenkins applied to the previous inline step.
set -ex

# strips 'internal/puppet-site-nectar' to 'puppet-site-nectar'
REPO=$(echo "$GERRIT_PROJECT" | cut -f2 -d'/')
# strips 'puppet-site-nectar' to 'nectar'
PUPPET_ENV=$(echo "$REPO" | cut -f3 -d'-')
git remote | grep 'r10k' > /dev/null && retval=0 || retval=1
if [ "$retval" -eq 1 ]; then
  git remote add r10k "git@git.rc.nectar.org.au:r10k/$REPO"
fi
git push r10k origin/master:refs/heads/master
git push r10k "origin/master:refs/heads/r10k_diff_catalog_${PUPPET_ENV}"
git ls-remote | grep "refs/changes/.*/${GERRIT_CHANGE_NUMBER}/[0-9]" | cut -f1 | xargs -n1 -ICOMMIT git push r10k :COMMIT || true
