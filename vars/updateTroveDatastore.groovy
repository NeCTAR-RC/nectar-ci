def call(String cloud_env) {
    script {
        imageId = readFile(file: 'build/.image-id').trim()
        datastoreName = readFile(file: 'build/.facts/datastore_name').trim()
        datastoreType = readFile(file: 'build/.facts/datastore_type').trim()
        datastoreVersion = readFile(file: 'build/.facts/datastore_version').trim()
    }
    sh """#!/bin/bash
    set +x
    echo "\033[33m========== Updating Trove datastore in $cloud_env ==========\033[0m"
    
    trove-manage --config-file /etc/trove/${cloud_env}.conf datastore_update $datastoreName
    trove-manage --config-file /etc/trove/${cloud_env}.conf datastore_version_update $datastoreName $datastoreVersion-\$BUILD_NUMBER $datastoreType $imageId '' 1
    trove-manage --config-file /etc/trove/${cloud_env}.conf datastore_update $datastoreName ${datastoreVersion}-\$BUILD_NUMBER
    """
}
