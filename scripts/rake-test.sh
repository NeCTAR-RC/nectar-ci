#!/bin/bash
# rake-test: invoked via `!include-raw-escape:` from data/builder-macros.yaml.
# set -ex preserves the `sh -xe` Jenkins applied to the previous inline step.
set -ex

if [ -f Gemfile ]; then
  mkdir -p /tmp/rake-test
  export GEM_HOME=/tmp/rake-test
  bundle install
  bundle exec rake test 2>&1
else
  rake test 2>&1
fi
