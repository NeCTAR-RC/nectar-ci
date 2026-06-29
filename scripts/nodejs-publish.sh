#!/bin/bash
# nodejs-publish: invoked via `!include-raw-escape:` from data/builder-macros.yaml.
# set -ex preserves the `sh -xe` Jenkins applied to the previous inline step.
set -ex

export PATH=~/nodejs-bin/:$PATH

pnpm build

# Basic auth (registry-scoped _auth = base64 of user:password), bound from the
# Jenkins username/password credential. Written fresh into the ephemeral build
# container each run and never committed. Mirrors the previous Bamboo pipeline.
REGISTRY=verdaccio.artm.rc.nectar.org.au
echo "//$REGISTRY/:_auth=$(echo -n "$VERDACCIO_USR:$VERDACCIO_PSW" | base64)" > "$HOME/.npmrc"

# Publish only when package.json carries a version not yet on the registry.
# Versions are bumped on release, not every commit, so most merges (deps,
# tooling, CI) reuse the current version — publishing those would 403 on the
# existing version and red-fail the job. Skip them instead; a real release
# bumps the version and falls through to publish.
PKG=$(node -p "require('./package.json').name")
VER=$(node -p "require('./package.json').version")
if pnpm view "$PKG@$VER" version --registry "https://$REGISTRY" >/dev/null 2>&1; then
  echo "$PKG@$VER already on $REGISTRY; nothing to publish."
else
  # --ignore-scripts: the prepare hook (git-hooks installer) is irrelevant here.
  # --no-git-checks: publishing from a detached Gerrit checkout, not a branch tip.
  pnpm publish --no-git-checks --ignore-scripts
fi
