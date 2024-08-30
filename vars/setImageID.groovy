def call() {
    unstash 'build'
    sh """#!/bin/bash -eu
    echo "\033[34m========== Image ID ==========\033[0m"
    ID=$(uuidgen)
    echo "\$ID" > build/.image-id
    echo "New image ID is: \$ID"
    """
    stash includes: 'build/**', name: 'build'
}
