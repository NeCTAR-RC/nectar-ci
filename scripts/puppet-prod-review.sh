#!/bin/bash
# puppet-prod-review: invoked via `!include-raw-escape:` from data/builder-macros.yaml.
# set -ex preserves the `sh -xe` Jenkins applied to the previous inline step.
set -ex

git config gitreview.username jenkins
git config core.hooksPath /tmp/jenkins-git-hooks
git branch change
git checkout production
git cherry-pick change
git review production -t "$GERRIT_TOPIC"
