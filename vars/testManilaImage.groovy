def call(String cloud_env, String availability_zone) {

    git credentialsId: 'cd8b8dd3-b897-4ecb-985d-180d5b6f8498', url: 'ssh://review.rc.nectar.org.au:29418/internal/nectar-testing.git'
    sh """#!/bin/bash
    echo "\033[33m========== Testing Image ==========\033[0m"
    . /opt/tempest/bin/activate
    tmpdir=\$(mktemp -d --suffix=_tempest)
    cd \$WORKSPACE/tempest
    ./setup_tempest.py -s $availability_zone -e $cloud_env -j check-manila-smoke \$tmpdir
    cd \$tmpdir
    stestr run --include-list \$WORKSPACE/tempest/whitelists/check-manila-nfs.yaml --serial
    """
}
