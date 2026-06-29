#!/bin/bash
# Validate puppet manifests (.pp), plans, and ERB templates for the module
# under review. No JJB params: invoked via `!include-raw-escape:`. set -ex
# matches the `sh -xe` Jenkins applied to the previous inline shell step.
set -ex

MODULE=$(basename "$(pwd)" | sed 's/puppet-\(.*\)-puppet-unit/\1/')
cd "$MODULE" || exit
find . -iname '*.pp' -not -path "./.bundled_gems/*" -not -path "./plans/*" -print0 | xargs -0 -n1 /opt/puppetlabs/bin/puppet parser validate --modulepath="$(pwd)/modules"
find ./plans/ -iname '*.pp' -print0 | xargs -0 -n1 /opt/puppetlabs/bin/puppet parser validate --tasks
# shellcheck disable=SC2044  # .erb paths in a puppet module contain no spaces
for f in $(find . -iname '*.erb' -not -path "./.bundled_gems/*"); do
  erb -P -x -T '-' "$f" | ruby -c
done
