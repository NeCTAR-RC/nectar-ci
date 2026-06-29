#!/bin/bash
# octocatalog-diff-rake-test: invoked via `!include-raw-escape:` from data/builder-macros.yaml.
# set -ex preserves the `sh -xe` Jenkins applied to the previous inline step.
set -ex

export PUPPET_VERSION="7.3.0"
export PUPPET_VERSIONS="7.3.0"
export RUBOCOP_TEST=false
export RSPEC_TEST=true
docker build . --file Dockerfile --tag octocatalog-diff:ruby2.6 --build-arg RUBY_VERSION=2.6-buster
docker run -e PUPPET_VERSION -e PUPPET_VERSIONS -e RSPEC_TEST -e RUBOCOP_TEST -e ENFORCE_COVERAGE octocatalog-diff:ruby2.6 /app/script/cibuild
