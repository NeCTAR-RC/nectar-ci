#!/bin/bash
rm -rf ./*.tgz
git fetch --tags
#get highest tag number
TAG_VERSION=$(git describe --abbrev=0 --tags)
# Project version
PROJECT_VERSION=$(grep "^version" Chart.yaml | awk '{print $2}')
if [[ "$PROJECT_VERSION" == "$TAG_VERSION" ]]; then
    echo "No change in version"
    exit 0
fi
echo "Tagging release to $PROJECT_VERSION"
#get current hash and see if it already has a tag
GIT_COMMIT=$(git rev-parse HEAD)
NEEDS_TAG=$(git describe --contains "$GIT_COMMIT" 2>/dev/null)
#only tag if no tag already
if [ -z "$NEEDS_TAG" ]; then
    git tag -a "$PROJECT_VERSION" -m "New auto tagged release ${PROJECT_VERSION}"
    echo "Tagged with $PROJECT_VERSION"
    helm package .
    git push origin --tags
else
    echo "Already a tag on this commit"
fi
