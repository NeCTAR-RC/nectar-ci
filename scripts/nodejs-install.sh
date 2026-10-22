#!/bin/bash
# nodejs-install: invoked via `!include-raw-escape:` from data/builder-macros.yaml.
# set -ex preserves the `sh -xe` Jenkins applied to the previous inline step.
set -ex

# The agent image provides pnpm only as a launcher: pnpm switches itself to
# the version the project pins in package.json (packageManager) and runs the
# project's scripts on the Node.js pinned there (devEngines.runtime), or on
# the agent's Node when the project pins none. Node 25 and later ship no
# corepack, so nothing is enabled or installed here.
pnpm set registry https://verdaccio.artm.rc.nectar.org.au/
pnpm install
