def call(String cloudEnv, String active = '1') {
    unstash 'build'
    script {
        imageId = readFile(file: 'build/.image-id').trim()
        datastoreName = readFile(file: 'build/.facts/datastore_name').trim()
        datastoreType = readFile(file: 'build/.facts/datastore_type').trim()
        datastoreVersion = readFile(file: 'build/.facts/datastore_version').trim()
        switch(cloudEnv) {
          case "production":
            OSCredID = '6c8091b5-0e7d-4be5-8458-4e5a999acdd6'
            OSAuthURL = 'https://keystone.rc.nectar.org.au:5000/v3'
            break
          case "testing":
            OSCredID = 'cc826c4e-07fe-4a0c-b334-fb8100b23c7b'
            OSAuthURL = 'https://keystone.test.rc.nectar.org.au:5000/v3'
            break
          case "development":
            OSCredID = 'bcb39a6c-5aca-4900-94aa-63fb4364d8c2'
            OSAuthURL = 'http://keystone.dev.rc.nectar.org.au:5000/v3'
            break
        }
    }
    withCredentials([usernamePassword(credentialsId: OSCredID, usernameVariable: 'OS_USERNAME', passwordVariable: 'OS_PASSWORD')]) {
       sh """#!/bin/bash
       export OS_AUTH_URL=${OSAuthURL}
       export OS_PROJECT_DOMAIN_NAME=Default
       export OS_USER_DOMAIN_NAME=Default
       export OS_IDENTITY_API_VERSION=3
       export OS_PROJECT_NAME=trove
       echo "Finding previous datastore version"
       PREVIOUS_BUILD=`openstack datastore version list ${datastoreType} -c Name -f value | grep "${datastoreVersion}-" | awk -F '-' '{print \$2}' | sort -n | tail -n 1`
       if [ -n "\$PREVIOUS_BUILD" ]; then
           PREVIOUS_VERSION=${datastoreVersion}-\$PREVIOUS_BUILD
       else
           PREVIOUS_VERSION=
       fi
       mkdir -p previous-version
       echo \$PREVIOUS_VERSION > previous-version/${cloudEnv}
       echo "Previous version is: \$PREVIOUS_VERSION"
       echo "\033[35;1m========== Updating Trove datastore in ${cloudEnv} ==========\033[0m"
       echo "==> trove-manage --config-file /etc/trove/${cloudEnv}.conf datastore_version_update ${datastoreName} ${datastoreVersion}-\$BUILD_NUMBER ${datastoreType} ${imageId} '' ${active}"
       trove-manage --config-file /etc/trove/${cloudEnv}.conf datastore_version_update ${datastoreName} ${datastoreVersion}-\$BUILD_NUMBER ${datastoreType} ${imageId} '' ${active}
       echo "==> trove-manage --config-file /etc/trove/${cloudEnv}.conf db_load_datastore_config_parameters ${datastoreName} ${datastoreVersion}-\$BUILD_NUMBER /etc/trove/templates/${datastoreType}/validation-rules.json"
       trove-manage --config-file /etc/trove/${cloudEnv}.conf db_load_datastore_config_parameters ${datastoreName} ${datastoreVersion}-\$BUILD_NUMBER /etc/trove/templates/${datastoreType}/validation-rules.json
       for flavor_id in `openstack flavor list --long --all | grep "flavor_class:name='trove'" | grep db3 | awk '{print \$2}'`; do
           echo "==> trove-manage --config-file /etc/trove/${cloudEnv}.conf datastore_version_flavor_add ${datastoreName} ${datastoreVersion}-\$BUILD_NUMBER \$flavor_id"
           trove-manage --config-file /etc/trove/${cloudEnv}.conf datastore_version_flavor_add ${datastoreName} ${datastoreVersion}-\$BUILD_NUMBER \$flavor_id
       done
       """
    }
    stash includes: 'previous-version/**', name: 'previous-version'
}
