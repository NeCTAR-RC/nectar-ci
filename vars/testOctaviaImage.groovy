def call(String cloud_env, String availability_zone) {

    git credentialsId: '4946c3a5-9f5e-4eac-9ec4-90e1e348db14', url: 'ssh://review.rc.nectar.org.au:29418/internal/nectar-testing.git'
    sh '''#!/bin/bash
    set +x
    echo "\033[33m========== Testing Image ==========\033[0m"
    . /opt/tempest/bin/activate
    tmpdir=$(mktemp -d --suffix=_tempest)
    cd $WORKSPACE/tempest
    ./setup_tempest.py -s $availability_zone -e $cloud_env -j check-octavia-smoke $tmpdir
    cd $tmpdir
    stestr run --whitelist-file $WORKSPACE/tempest/whitelists/check-octavia.yaml --serial 2>&1 | grep --line-buffered -vE ' \\w+Warning: |self._sock = None'
    RET=${PIPESTATUS[0]}
    rm -rf $tmpdir
    exit $RET
    '''
}
