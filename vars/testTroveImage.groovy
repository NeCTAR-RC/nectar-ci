def call(String cloud_env, String availability_zone) {
    unstash 'build'
    unstash 'previous-version'
    script {
        datastoreName = readFile(file: 'build/.facts/datastore_name').trim()
        datastoreType = readFile(file: 'build/.facts/datastore_type').trim()
        datastoreVersion = readFile(file: 'build/.facts/datastore_version').trim()
        previous_file = "previous-version/" + cloud_env
        previousVersion = readFile(file: previous_file).trim()
    }
    git credentialsId: '4946c3a5-9f5e-4eac-9ec4-90e1e348db14', url: 'ssh://review.rc.nectar.org.au:29418/internal/nectar-testing.git'
    sh """
    #!/bin/bash
    set +x
    rm -rf build
    echo "\033[33m========== Testing Image ==========\033[0m"
    . /opt/tempest/bin/activate
    tmpdir=\$(mktemp -d --suffix=_tempest)
    cd \$WORKSPACE/tempest
    echo "==> ./setup_tempest.py -s $availability_zone -e $cloud_env -j check-trove --trove-datastore $datastoreName --trove-datastore-version ${datastoreVersion}-\$BUILD_NUMBER --trove-previous-version $previousVersion \$tmpdir"
    ./setup_tempest.py -s $availability_zone -e $cloud_env -j check-trove --trove-datastore $datastoreName --trove-datastore-version ${datastoreVersion}-\$BUILD_NUMBER --trove-previous-version $previousVersion \$tmpdir
    cd \$tmpdir
    echo "==> stestr run --whitelist-file \$WORKSPACE/tempest/whitelists/check-trove.yaml --serial"
    stestr run --whitelist-file \$WORKSPACE/tempest/whitelists/check-trove.yaml --serial
    stestr run trove_tempest_plugin.tests.scenario.database.test_upgrade.UpgradeInstanceScenarioTest.test_upgrade_instance
    """
}
