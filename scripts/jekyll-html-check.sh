#!/bin/bash
# jekyll-html-check: invoked via `!include-raw-escape:` from data/builder-macros.yaml.
# set -ex preserves the `sh -xe` Jenkins applied to the previous inline step.
set -ex

rake setup
rake build
rake check
