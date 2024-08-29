def call(String cloudEnv, String availabilityZone) {
    unstash 'build'
    unstash 'previous-version'
    script {
        datastoreName = readFile(file: 'build/.facts/datastore_name').trim()
        datastoreType = readFile(file: 'build/.facts/datastore_type').trim()
        datastoreVersion = readFile(file: 'build/.facts/datastore_version').trim()
        previousFile = "previous-version/" + cloudEnv
        previousVersion = readFile(file: previousFile).trim()
    }
    git credentialsId: 'cd8b8dd3-b897-4ecb-985d-180d5b6f8498', url: 'ssh://review.rc.nectar.org.au:29418/internal/nectar-testing.git'
    sh """#!/bin/bash
    rm -rf build
    echo "\033[35;1m========== Testing Image ==========\033[0m"

    if [ -n "$previousVersion" ]; then
        TROVE_PREVIOUS_VERSION="--trove-previous-version $previousVersion"
    fi

    . /opt/tempest/bin/activate
    tmpdir=\$(mktemp -d --suffix=_tempest)
    cd \$WORKSPACE/tempest
    echo "==> ./setup_tempest.py -s $availabilityZone -e $cloudEnv -j check-trove --trove-datastore $datastoreName --trove-datastore-version ${datastoreVersion}-\$BUILD_NUMBER \$TROVE_PREVIOUS_VERSION \$tmpdir"
    ./setup_tempest.py -s $availabilityZone -e $cloudEnv -j check-trove --trove-datastore $datastoreName --trove-datastore-version ${datastoreVersion}-\$BUILD_NUMBER \$TROVE_PREVIOUS_VERSION \$tmpdir
    cd \$tmpdir
    echo "==> stestr run --include-list \$WORKSPACE/tempest/whitelists/check-trove.yaml --serial"
    stestr run --include-list \$WORKSPACE/tempest/whitelists/check-trove.yaml --serial
    """
}
