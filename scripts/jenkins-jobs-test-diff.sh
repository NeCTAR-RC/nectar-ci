#!/bin/bash
echo "Setting up diff:"
git checkout "$GERRIT_NEWREV"
NEW_CONF=$(jenkins-jobs test -r data 2>&1)
git fetch
git reset --hard origin/master
OLD_CONF=$(jenkins-jobs test -r data 2>&1)
echo "diff:"
diff -U0 --color=always  <(echo "$OLD_CONF") <(echo "$NEW_CONF")
exit 0
