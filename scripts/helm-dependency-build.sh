#!/bin/bash
# helm-dependency-build: invoked via `!include-raw-escape:` from data/builder-macros.yaml.
# set -ex preserves the `sh -xe` Jenkins applied to the previous inline step.
set -ex

# Add bintami repo as we trust it, see https://github.com/helm/helm/issues/8036 for reasons why this is needed
helm repo add bitnami https://charts.bitnami.com/bitnami
# Switch to use pull through cache to save on dockerhub reguests
sed -i 's,registry-1.docker.io,registry.rc.nectar.org.au/docker.io,g' Chart.yaml
# We need an update here as opposed to a build as the Chart.lock changes with the sed
# All charts should be pinned to a specific version so this should be ok.
helm dependency update
