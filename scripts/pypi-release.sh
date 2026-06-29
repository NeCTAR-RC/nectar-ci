#!/bin/bash
virtualenv /tmp/twine
# shellcheck disable=SC1091  # virtualenv activate script only exists at runtime
. /tmp/twine/bin/activate
pip install -U build twine
rm -rf dist/*
python3 -m build
twine upload --config-file "$PYPIRC" dist/*
