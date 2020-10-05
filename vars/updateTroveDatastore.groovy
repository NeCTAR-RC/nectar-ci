def call(String cloud_env) {
    unstash 'build'
    script {
        imageId = readFile(file: 'build/.image-id').trim()
        datastoreName = readFile(file: 'build/.facts/datastore_name').trim()
        datastoreType = readFile(file: 'build/.facts/datastore_type').trim()
        datastoreVersion = readFile(file: 'build/.facts/datastore_version').trim()
        switch(cloud_env) {
          case "production":
            os_cred_id = '6c8091b5-0e7d-4be5-8458-4e5a999acdd6'
            os_auth_url = 'https://keystone.rc.nectar.org.au:5000/v3'
            flavor_id = '325c919d-b523-4960-968c-f2baffafff94'
            break
          case "testing":
            os_cred_id = 'cc826c4e-07fe-4a0c-b334-fb8100b23c7b'
            os_auth_url = 'https://keystone.test.rc.nectar.org.au:5000/v3'
            flavor_id = '12'
            break
          case "development":
            os_cred_id = 'bcb39a6c-5aca-4900-94aa-63fb4364d8c2'
            os_auth_url = 'http://keystone.dev.rc.nectar.org.au:5000/v3'
            flavor_id = '7950a727-e7c9-4833-9340-36d126c364fa'
            break
        }
    }
    withCredentials([usernamePassword(credentialsId: os_cred_id, usernameVariable: 'OS_USERNAME', passwordVariable: 'OS_PASSWORD')]) {
       sh """
       set +x
       export OS_AUTH_URL=$os_auth_url
       export OS_PROJECT_DOMAIN_NAME=Default
       export OS_USER_DOMAIN_NAME=Default
       export OS_IDENTITY_API_VERSION=3
       export OS_PROJECT_NAME=trove
       echo "Finding previous datastore version"
       PREVIOUS_BUILD=`openstack datastore version list $datastoreType -c Name -f value | grep $datastoreVersion | awk -F '-' '{print \$2}' | sort -n | tail -n 1`
       PREVIOUS_VERSION=${datastoreVersion}-\$PREVIOUS_BUILD
       mkdir -p previous-version
       echo \$PREVIOUS_VERSION > previous-version/${cloud_env}
       echo "Previous version is: \$PREVIOUS_VERSION"
       """
    }
    stash includes: 'previous-version/**', name: 'previous-version'

    sh """#!/bin/bash
    set +x
    echo "\033[33m========== Updating Trove datastore in $cloud_env ==========\033[0m"
    echo "==> trove-manage --config-file /etc/trove/${cloud_env}.conf datastore_version_update $datastoreName ${datastoreVersion}-\$BUILD_NUMBER $datastoreType $imageId '' 1"
    trove-manage --config-file /etc/trove/${cloud_env}.conf datastore_version_update $datastoreName ${datastoreVersion}-\$BUILD_NUMBER $datastoreType $imageId '' 1
    echo "==> trove-manage --config-file /etc/trove/${cloud_env}.conf db_load_datastore_config_parameters $datastoreName ${datastoreVersion}-\$BUILD_NUMBER /etc/trove/templates/${datastoreType}/validation-rules.json"
    trove-manage --config-file /etc/trove/${cloud_env}.conf db_load_datastore_config_parameters $datastoreName ${datastoreVersion}-\$BUILD_NUMBER /etc/trove/templates/${datastoreType}/validation-rules.json
    echo "==> trove-manage --config-file /etc/trove/${cloud_env}.conf datastore_version_flavor_add $datastoreName ${datastoreVersion}-\$BUILD_NUMBER $flavor_id"
    trove-manage --config-file /etc/trove/${cloud_env}.conf datastore_version_flavor_add $datastoreName ${datastoreVersion}-\$BUILD_NUMBER $flavor_id
    """
}
