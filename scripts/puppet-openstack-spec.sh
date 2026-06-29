#!/bin/bash
# puppet-openstack-spec: invoked via `!include-raw-escape:` from data/builder-macros.yaml.
# set -ex preserves the `sh -xe` Jenkins applied to the previous inline step.
set -ex

export STRICT_VARIABLES=no
export RSPEC_PUPPET_VERSION='= 2.9.0'
export PUPPET_GEM_VERSION='~> 7.10.0'
bundle install --path vendor/bundle
bundle exec rake spec
