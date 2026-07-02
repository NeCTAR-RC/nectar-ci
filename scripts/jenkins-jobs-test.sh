#!/bin/bash
# jenkins-jobs-test: invoked via `!include-raw-escape:` from data/builder-macros.yaml.
# set -ex preserves the `sh -xe` Jenkins applied to the previous inline step.
set -ex

# Isolate JJB's cache per build so concurrent test/update runs on the shared
# 'internal' node never contend for the same cache lock (that race fails the
# build with "Unable to lock cache"). test needs no persistent cache, so a
# throwaway per-workspace path is safe.
export XDG_CACHE_HOME="${WORKSPACE:-$PWD}/.cache"

# GERRIT_NEWREV is empty on patchset-created events (it is only populated on
# ref-updated). Leave it unquoted so the empty value word-splits away and
# `git checkout` is a harmless no-op; quoting passes an empty pathspec, which
# git rejects with "empty string is not a valid pathspec".
# shellcheck disable=SC2086
git checkout $GERRIT_NEWREV
jenkins-jobs test -r data
