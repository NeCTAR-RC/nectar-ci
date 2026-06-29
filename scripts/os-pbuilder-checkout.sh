#!/bin/bash
# os-pbuilder-checkout: invoked via `!include-raw-escape:` from data/builder-macros.yaml.
# set -ex preserves the `sh -xe` Jenkins applied to the previous inline step.
set -ex

# Set up remote for github or internal or uom gitlab, since we have no tags otherwise.
git remote | grep 'alt-origin' > /dev/null && retval=0 || retval=1
if [ "$retval" -ne 0 ]; then

  git remote -v | grep '29418/NeCTAR-RC' > /dev/null && retval=0 || retval=1
  if [ "$retval" -eq 0 ]; then
    URL=$(git remote -v | grep 29418 | sed 's/.*29418\/\(.*\) .*/\1/' | head -1)
    git remote add alt-origin "https://github.com/$URL"
  fi

  git remote -v | grep '29418/internal' > /dev/null && retval=0 || retval=1
  if [ "$retval" -eq 0 ]; then
    URL=$(git remote -v | grep 29418 | sed 's/.*29418\/\(.*\) .*/\1/' | head -1)
    git remote add alt-origin "git@git.rc.nectar.org.au:$URL"
  fi

  git remote -v | grep '29418/resplat-cloud' > /dev/null && retval=0 || retval=1
  if [ "$retval" -eq 0 ]; then
    URL=$(git remote -v | grep 29418 | sed 's/.*29418\/\(.*\) .*/\1/' | head -1)
    git remote add alt-origin "git@gitlab.unimelb.edu.au:$URL"
  fi
fi
git fetch --tags alt-origin
for rb in $(git branch -r --list | grep alt-origin | grep debian); do
  branch=${rb//alt-origin\//}
  git branch "$branch" "alt-origin/$branch"
done

git branch -d "$GERRIT_BRANCH" || true
git checkout -b "$GERRIT_BRANCH" "origin/$GERRIT_BRANCH"
