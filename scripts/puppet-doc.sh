#!/bin/bash
# Generate rdoc puppet docs and strip the workspace prefix from file paths.
# No JJB params: invoked via `!include-raw-escape:`. set -ex (no pipefail) matches
# the `sh -xe` Jenkins applied to the previous inline shell step; pipefail would
# break the final `grep ... | while read` when grep matches nothing.
set -ex

# Cleanup old docs.
[ -d doc/ ] && rm -rf doc/
## Dummy manifests folder.
! [ -d manifests/ ] && mkdir manifests/
## Generate docs
puppet doc --mode rdoc --manifestdir manifests/ --modulepath ./modules/ --outputdir doc
## Fix docs to how I want them, I don't like that the complete workspace is included in all file paths.
if [ -d "${WORKSPACE}/doc/files/${WORKSPACE}/modules" ]; then
  mv -v "${WORKSPACE}/doc/files/${WORKSPACE}/modules" "${WORKSPACE}/doc/files/modules"
fi
grep -l -R "${WORKSPACE}" ./* | while read -r fname; do sed -i "s@${WORKSPACE}/@/@g" "$fname"; done
