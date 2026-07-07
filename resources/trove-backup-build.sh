#!/bin/bash
# Build and push one trove db-backup datastore image from the backup/ dir of a
# trove checkout. Loaded by the trove-backup-container-* pipelines via
# libraryResource. Inputs come from the pipeline environment:
#   DATASTORE          e.g. mysql, postgresql
#   DATASTORE_VERSION  e.g. 8.0, 8.4, 16
#   REGISTRY_USR / REGISTRY_PSW  registry-nectar credentials
set -euo pipefail

# Isolate the registry auth file to this workspace so concurrent jobs on the
# same agent don't clobber each other's login (the default location is shared).
export REGISTRY_AUTH_FILE="$PWD/auth.json"
trap 'rm -f "$REGISTRY_AUTH_FILE"' EXIT

export REGISTRY="registry.rc.nectar.org.au"
echo "$REGISTRY_PSW" | docker login -u "$REGISTRY_USR" --password-stdin "$REGISTRY"

image="$REGISTRY/nectartrove/db-backup-$DATASTORE:$DATASTORE_VERSION"
docker build --platform linux/amd64 \
    --build-arg "DATASTORE=$DATASTORE" \
    --build-arg "DATASTORE_VERSION=$DATASTORE_VERSION" \
    -t "$image" \
    backup
docker push "$image"
