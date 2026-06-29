#!/bin/bash
# jenkins-jobs-test: invoked via `!include-raw-escape:` from data/builder-macros.yaml.
# set -ex preserves the `sh -xe` Jenkins applied to the previous inline step.
set -ex

git checkout "$GERRIT_NEWREV"
jenkins-jobs test -r data
