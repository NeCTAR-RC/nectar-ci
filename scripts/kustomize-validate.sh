#!/bin/bash
kustomizatons=$(find . -name kustomization.yaml -print0 | xargs -0 -n1 dirname | sort --unique)
for kustomization in ${kustomizatons}; do
  if kustomize build "${kustomization}" -o /tmp/manifest.yaml; then
    echo "validated ${kustomization}"
  else
    echo "validation failed for ${kustomization}"
    exit 1
  fi
done
