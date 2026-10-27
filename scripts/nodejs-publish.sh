#!/bin/bash
# nodejs-publish: invoked via `!include-raw-escape:` from data/builder-macros.yaml.
# set -ex preserves the `sh -xe` Jenkins applied to the previous inline step.
set -ex

pnpm build

# Registry auth. Verdaccio 7 accepts only Bearer tokens on package requests
# (Basic `_auth` entries are rejected), while its login route still takes
# Basic credentials. So exchange the Jenkins username/password credential
# for a token through the login route and write the token as a
# registry-scoped `_authToken`. The credentials go in the Basic header as
# well as the body: without the header Verdaccio treats the PUT as a
# registration and answers 409 for an existing account (npm login hits
# that 409 first and then retries with the header). Works on Verdaccio 6
# and 7 alike. Written fresh into the ephemeral build container each run
# and never committed. Tracing is paused so the password never reaches
# the log.
REGISTRY=verdaccio.artm.rc.nectar.org.au
set +x
LOGIN_BODY=$(node -p 'JSON.stringify({name: process.env.VERDACCIO_USR, password: process.env.VERDACCIO_PSW})')
TOKEN=$(curl -fsS -u "$VERDACCIO_USR:$VERDACCIO_PSW" \
  -X PUT "https://$REGISTRY/-/user/org.couchdb.user:$VERDACCIO_USR" \
  -H 'Content-Type: application/json' -d "$LOGIN_BODY" \
  | node -p 'try { JSON.parse(require("fs").readFileSync(0, "utf8")).token || "" } catch { "" }')
[ -n "$TOKEN" ] || { echo "registry login failed for $VERDACCIO_USR at $REGISTRY" >&2; exit 1; }
echo "//$REGISTRY/:_authToken=$TOKEN" > "$HOME/.npmrc"
unset LOGIN_BODY TOKEN
set -x

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
