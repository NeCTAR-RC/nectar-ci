#!/bin/bash
# nodejs-storybook-test: invoked via `!include-raw-escape:` from data/builder-macros.yaml.
# set -ex preserves the `sh -xe` Jenkins applied to the previous inline step.
set -ex

export PATH=~/nodejs-bin/:$PATH
# `pnpm test` renders every story in a real headless chromium (Vitest browser
# mode via @storybook/addon-vitest) and fails on a11y violations. test:install
# fetches chromium into the ephemeral build container. Future Chromatic visual
# tests will hang off here.
pnpm test:install
pnpm test
