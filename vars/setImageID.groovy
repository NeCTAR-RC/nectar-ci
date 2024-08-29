def call() {
    unstash 'build'
    sh """#!/bin/bash -eu
    echo `uuidgen` > build/.image-id
    """
    stash includes: 'build/**', name: 'build'
}
