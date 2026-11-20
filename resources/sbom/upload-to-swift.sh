#!/bin/bash
# Upload one Glance image SBOM and its metadata file to a Swift container.
# Loaded by the uploadSbom step via libraryResource. Inputs come from the
# pipeline environment:
#   SBOM       path to sboms/glance/<algo>/<hash>.spdx.json, as printed by
#              scan-disk-image.sh
#   CONTAINER  Swift container to upload into
#   OS_*       OpenStack credentials and project that own the container
#
# Objects are named glance/<algo>/<hash>.spdx.json and
# glance/<algo>/<hash>.meta.json, the same layout as sboms/, so an image's
# SBOM can be found from its os_hash_algo and os_hash_value. The container is
# left private.
set -euo pipefail

meta=${SBOM%.spdx.json}.meta.json
algo=$(basename "$(dirname "$SBOM")")

openstack container create "$CONTAINER" >/dev/null
for file in "$SBOM" "$meta"; do
    openstack object create --name "glance/$algo/$(basename "$file")" "$CONTAINER" "$file"
done
