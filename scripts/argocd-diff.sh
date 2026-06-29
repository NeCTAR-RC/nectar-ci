#!/bin/bash
# argocd-diff: invoked via `!include-raw-escape:` from data/builder-macros.yaml.
# set -ex preserves the `sh -xe` Jenkins applied to the previous inline step.
set -ex

rm -rf base-branch
rm -rf target-branch
tmpdir=$(mktemp -d --suffix=_argocd)
mkdir target-branch
mkdir secrets
mv "$FILE" secrets/secrets.yaml
mv apps* target-branch
mkdir base-branch
git checkout -B master "origin/$GERRIT_BRANCH"
git pull
git reset --hard
mv apps* base-branch
docker run --network host -v /var/run/docker.sock:/var/run/docker.sock -v "$(pwd)/secrets:/secrets" -v "${tmpdir}:/output" -v "$(pwd)/base-branch:/base-branch" -v "$(pwd)/target-branch:/target-branch" -e "TARGET_BRANCH=$GERRIT_REFSPEC" -e "BASE_BRANCH=$GERRIT_BRANCH" -e "REPO=$GERRIT_PROJECT" registry.rc.nectar.org.au/docker.io/dagandersen/argocd-diff-preview:v0.1.18
rm -rf secrets
cat "${tmpdir}/diff.md"
