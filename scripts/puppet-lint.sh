#!/bin/bash
# Run flake8-diff puppet-lint against the module under review.
# No JJB params: invoked via `!include-raw-escape:`. set -ex matches the
# `sh -xe` Jenkins applied to the previous inline shell step.
set -ex

MODULE=$(basename "$(pwd)" | sed 's/puppet-\(.*\)-puppet-unit/\1/')
if [ -n "$MODULE" ]; then
  rm -rf "$MODULE"
  git clone . "$MODULE"
  cd "$MODULE" || exit
fi

wget https://raw.githubusercontent.com/NeCTAR-RC/flake8-diff/master/lint.py
chmod +x lint.py

if [ -f Gemfile ]; then
  mkdir .bundled_gems
  GEM_HOME="$(pwd)/.bundled_gems"
  export GEM_HOME
  bundle install
  bundle exec ./lint.py rake lint 2>&1
else
  ./lint.py rake lint 2>&1
fi
