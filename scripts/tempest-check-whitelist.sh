#!/bin/bash
# tempest-check-whitelist: invoked via `!include-raw-escape:` from data/builder-macros.yaml.
# CHECK and WHITELIST are injected as env vars by the macro; AVAILABILITY_ZONE and
# CLOUD come from the build environment. No `set`: faithful to the bash (no -e) the
# inline shebang gave, so RET=${PIPESTATUS[0]} and the cleanup/exit run as before.
# shellcheck disable=SC1091  # tempest venv activate script only exists at runtime
. /opt/tempest/bin/activate
tmpdir=$(mktemp -d --suffix=_tempest)
cd "$WORKSPACE/tempest/" || exit 1
./setup_tempest.py -s "$AVAILABILITY_ZONE" -e "$CLOUD" -j "check-$CHECK" "$tmpdir"
cd "$tmpdir" || exit 1
stestr run --include-list "$WORKSPACE/tempest/whitelists/check-$WHITELIST.yaml" --serial 2>&1 | grep --line-buffered -vE ' \w+Warning: |self._sock = None'
RET=${PIPESTATUS[0]}
rm -rf "$tmpdir"
exit "$RET"
