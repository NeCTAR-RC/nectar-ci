#!/bin/bash
# jenkins-jobs-update: invoked via `!include-raw-escape:` from data/builder-macros.yaml.
# set -ex preserves the `sh -xe` Jenkins applied to the previous inline step.
set -ex

# GERRIT_NEWREV is empty on patchset-created events (it is only populated on
# ref-updated). Leave it unquoted so the empty value word-splits away and
# `git checkout` is a harmless no-op; quoting passes an empty pathspec, which
# git rejects with "empty string is not a valid pathspec".
# shellcheck disable=SC2086
git checkout $GERRIT_NEWREV
jenkins-jobs update -r data
