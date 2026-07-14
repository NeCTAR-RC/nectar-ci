/* Run the check-trove tempest suite against the throwaway test datastore version.
   The suite includes test_upgrade, which creates an instance on
   previousVersionName and upgrades it to datastoreVersionName. In the tag model
   the previous version is the active version (e.g. '8.0'): at this point the
   'trove' tag still resolves to the OLD image (we retag only in the later
   Promote stage), so the upgrade exercises OLD image -> NEW image. Pass an empty
   previousVersionName to skip the upgrade leg (e.g. the first bootstrap run when
   no active version exists yet). */

def call(String cloudEnv, String availabilityZone, String datastore, String datastoreVersionName, String previousVersionName = '') {
    git credentialsId: 'cd8b8dd3-b897-4ecb-985d-180d5b6f8498', url: 'ssh://review.rc.nectar.org.au:29418/internal/nectar-testing.git'
    sh """#!/bin/bash
    echo "\033[35;1m========== Testing Image ==========\033[0m"

    if [ -n "$previousVersionName" ]; then
        TROVE_PREVIOUS_VERSION="--trove-previous-version $previousVersionName"
    fi

    . /opt/tempest/bin/activate
    tmpdir=\$(mktemp -d --suffix=_tempest)
    cd \$WORKSPACE/tempest
    echo "==> ./setup_tempest.py -s $availabilityZone -e $cloudEnv -j check-trove --trove-datastore $datastore --trove-datastore-version $datastoreVersionName \$TROVE_PREVIOUS_VERSION \$tmpdir"
    ./setup_tempest.py -s $availabilityZone -e $cloudEnv -j check-trove --trove-datastore $datastore --trove-datastore-version $datastoreVersionName \$TROVE_PREVIOUS_VERSION \$tmpdir
    cd \$tmpdir
    echo "==> stestr run --include-list \$WORKSPACE/tempest/whitelists/check-trove.yaml --serial"
    stestr run --include-list \$WORKSPACE/tempest/whitelists/check-trove.yaml --serial
    """
}
