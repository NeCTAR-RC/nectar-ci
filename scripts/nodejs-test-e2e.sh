#!/bin/bash
# nodejs-test-e2e: invoked via `!include-raw-escape:` from data/builder-macros.yaml.
# set -ex preserves the `sh -xe` Jenkins applied to the previous inline step.
set -ex

export PATH=~/nodejs-bin/:$PATH
pnpm build
pnpm test:e2e:install
pnpm test:e2e
