#!/bin/bash
# markdown-lint: invoked via `!include-raw-escape:` from data/builder-macros.yaml.
# set -ex preserves the `sh -xe` Jenkins applied to the previous inline step.
set -ex

mkdir -p /tmp/vendor
export GEM_HOME=/tmp/vendor
cat > /tmp/vendor/md_style.rb << EOF
all
exclude_tag :whitespace
exclude_tag :line_length
exclude_rule 'MD002' # First header should be a h1 header
exclude_rule 'MD006' # Lists at beginning of line
exclude_rule 'MD007' # List indentation
exclude_rule 'MD014' # Dollar signs used before commands without showing output
exclude_rule 'MD033' # Inline HTML
exclude_rule 'MD034' # Bare URL used
exclude_rule 'MD040' # Fenced code blocks should have a language specified
EOF
gem install mdl
git diff --name-status HEAD~1 | grep '^[^D].*\.md' | cut -f2- | xargs -I filename /tmp/vendor/bin/mdl -s /tmp/vendor/md_style.rb "filename"
