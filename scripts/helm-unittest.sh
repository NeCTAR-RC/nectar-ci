#!/bin/bash
set -ex

if ! helm plugin list | grep -qE '^unittest\b'; then
    helm plugin install https://github.com/helm-unittest/helm-unittest.git --verify=false
fi

./tests/run-tests.sh
