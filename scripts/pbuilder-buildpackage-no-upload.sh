#!/bin/bash
# pbuilder-buildpackage-no-upload: invoked via `!include-raw-escape:` from data/builder-macros.yaml.
# UBUNTU_RELEASE is injected as an env var by the macro. set -ex preserves the
# `sh -xe` Jenkins applied to the previous inline step.
set -ex

export DEBFULLNAME="$GIT_AUTHOR_NAME"
export DEBEMAIL="$GIT_AUTHOR_EMAIL"
rm -f ./*.deb
hivemind packaging.buildpackage --ubuntu-release "$UBUNTU_RELEASE" --no-upload
cp ../../build-area/*/*.deb .
