#!/bin/bash
echo "Setting up diff:"
# GERRIT_NEWREV is empty on patchset-created events (it is only populated on
# ref-updated). Leave it unquoted so the empty value word-splits away and
# `git checkout` is a harmless no-op; quoting passes an empty pathspec, which
# git rejects with "empty string is not a valid pathspec".
# shellcheck disable=SC2086
git checkout $GERRIT_NEWREV
NEW_CONF=$(jenkins-jobs test -r data 2>&1)
git fetch
git reset --hard origin/master
OLD_CONF=$(jenkins-jobs test -r data 2>&1)
echo "diff:"
diff -U0 --color=always  <(echo "$OLD_CONF") <(echo "$NEW_CONF")
exit 0
