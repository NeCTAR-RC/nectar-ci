#!/bin/bash
export REGISTRY="registry.rc.nectar.org.au"
echo "$REGISTRY_PSW" | helm registry login -u "$REGISTRY_USR" --password-stdin "$REGISTRY"
find . -maxdepth 1 -name '*.tgz' -print0 | xargs -0 -I{} helm push {} oci://registry.rc.nectar.org.au/nectar-helm
