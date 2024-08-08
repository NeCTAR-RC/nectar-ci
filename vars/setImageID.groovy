def call() {
    unstash 'build'
    sh """#!/bin/bash
    echo `uuidgen` > build/.image-id
    """
    stash includes: 'build/**', name: 'build'
}
