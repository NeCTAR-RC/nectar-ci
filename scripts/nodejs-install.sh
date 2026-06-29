#!/bin/bash
# nodejs-install: invoked via `!include-raw-escape:` from data/builder-macros.yaml.
# set -ex preserves the `sh -xe` Jenkins applied to the previous inline step.
set -ex

mkdir -p ~/nodejs-bin
export PATH=~/nodejs-bin/:$PATH
corepack enable --install-directory ~/nodejs-bin
pnpm set registry https://verdaccio.artm.rc.nectar.org.au/
pnpm install
