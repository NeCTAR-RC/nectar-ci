def call(String cloudEnv) {
    unstash 'build'
    script {
        imageId = readFile(file: 'build/.image-id').trim()
        datastoreName = readFile(file: 'build/.facts/datastore_name').trim()
        datastoreType = readFile(file: 'build/.facts/datastore_type').trim()
        datastoreVersion = readFile(file: 'build/.facts/datastore_version').trim()
    }

    sh """#!/bin/bash
    echo "\033[35;1m========== Reverting Trove datastore in $cloudEnv ==========\033[0m"
    echo "Marking datastore version as inactive"
    echo "==> trove-manage --config-file /etc/trove/${cloudEnv}.conf datastore_version_update $datastoreName ${datastoreVersion}-\$BUILD_NUMBER $datastoreType $imageId '' 0"
    trove-manage --config-file /etc/trove/${cloudEnv}.conf datastore_version_update $datastoreName ${datastoreVersion}-\$BUILD_NUMBER $datastoreType $imageId '' 0
    """
}
