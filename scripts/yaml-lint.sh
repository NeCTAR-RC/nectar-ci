#!/bin/bash
# yaml-lint: invoked via `!include-raw-escape:` from data/builder-macros.yaml.
# set -ex preserves the `sh -xe` Jenkins applied to the previous inline step.
set -ex

mkdir -p /tmp/yamls
export GEM_HOME=/tmp/yamls
gem install --no-document yaml-lint
# shellcheck disable=SC2038  # xargs -d '\n' already handles newlines; yaml config names have none
find . -type f -iname "*.yml" -o -iname "*.yaml" -o -iname "*.eyaml" | xargs -d '\n' /tmp/yamls/bin/yaml-lint
