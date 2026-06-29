#!/bin/bash
# rake-lint: invoked via `!include-raw-escape:` from data/builder-macros.yaml.
# set -ex preserves the `sh -xe` Jenkins applied to the previous inline step.
set -ex

if [ -f Gemfile ]; then
  mkdir -p /tmp/rake-lint
  export GEM_HOME=/tmp/rake-lint
  bundle install
  bundle exec rake lint 2>&1
else
  rake lint 2>&1
fi
