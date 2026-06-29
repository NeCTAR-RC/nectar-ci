#!/bin/bash
# os-pbuilder-buildpackage: invoked via `!include-raw-escape:` from data/builder-macros.yaml.
# set -ex preserves the `sh -xe` Jenkins applied to the previous inline step.
set -ex

export DEBFULLNAME="$GIT_AUTHOR_NAME"
export DEBEMAIL="$GIT_AUTHOR_EMAIL"
rm -f ./*.deb
hivemind packaging.buildpackage
cp ../../build-area/*/*.deb .
