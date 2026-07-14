/* Register a throwaway test datastore version pinned to the freshly built guest
   image, so the container-model image can be validated in isolation before the
   'trove' glance tag is moved to promote it.

   Container model: the DB runs as a docker container whose tag is the datastore
   version's *version number* (e.g. mysql:8.4.10), which is distinct from the
   version *name* (e.g. 8.4-test). update_datastore_version keys on (name,
   version), so versionNumber must be passed here and, identically, wherever this
   row is later found (flavor/config lookups and the deactivate in
   troveRevertDatastore) -- otherwise a mismatched lookup creates a duplicate row
   instead of updating this one. The guest image is pinned by id (not
   --image-tags) for isolation; active versions resolve it by the 'trove' tag. */

def call(String cloudEnv, String datastore, String manager, String versionName, String versionNumber) {
    unstash 'build'
    def imageId
    def OSCredID
    def OSAuthURL
    script {
        imageId = readFile(file: 'build/.image-id').trim()
        switch(cloudEnv) {
          case "production":
            OSCredID = '6c8091b5-0e7d-4be5-8458-4e5a999acdd6'
            OSAuthURL = 'https://identity.rc.nectar.org.au/v3'
            break
          case "testing":
            OSCredID = 'cc826c4e-07fe-4a0c-b334-fb8100b23c7b'
            OSAuthURL = 'https://identity.rctest.nectar.org.au/v3'
            break
          case "development":
            OSCredID = 'bcb39a6c-5aca-4900-94aa-63fb4364d8c2'
            OSAuthURL = 'http://keystone.rcdev.nectar.org.au:5000/v3'
            break
        }
    }
    withCredentials([usernamePassword(credentialsId: OSCredID, usernameVariable: 'OS_USERNAME', passwordVariable: 'OS_PASSWORD')]) {
       sh """#!/bin/bash -eu
       export OS_AUTH_URL=${OSAuthURL}
       export OS_PROJECT_DOMAIN_NAME=Default
       export OS_USER_DOMAIN_NAME=Default
       export OS_IDENTITY_API_VERSION=3
       export OS_PROJECT_NAME=trove
       echo "\033[35;1m========== Registering test datastore ${datastore} ${versionName} (version ${versionNumber}) in ${cloudEnv} ==========\033[0m"
       # Pin the test version to this image id (not --image-tags) so it is isolated
       # from whatever the 'trove' tag currently resolves to. --version is the
       # version number and selects the DB container tag (e.g. ${datastore}:${versionNumber}).
       echo "==> trove-manage --config-file /etc/trove/${cloudEnv}.conf datastore_version_update ${datastore} ${versionName} ${manager} ${imageId} '' 1 --version ${versionNumber}"
       trove-manage --config-file /etc/trove/${cloudEnv}.conf datastore_version_update ${datastore} ${versionName} ${manager} ${imageId} '' 1 --version ${versionNumber}
       echo "==> trove-manage --config-file /etc/trove/${cloudEnv}.conf db_load_datastore_config_parameters ${datastore} ${versionName} /etc/trove/templates/${manager}/validation-rules.json --version ${versionNumber}"
       trove-manage --config-file /etc/trove/${cloudEnv}.conf db_load_datastore_config_parameters ${datastore} ${versionName} /etc/trove/templates/${manager}/validation-rules.json --version ${versionNumber}
       for flavor_id in `openstack flavor list --long --all | grep "flavor_class:name='trove'" | grep db3 | awk '{print \$2}'`; do
           echo "==> trove-manage --config-file /etc/trove/${cloudEnv}.conf datastore_version_flavor_add ${datastore} ${versionName} \$flavor_id --version ${versionNumber}"
           trove-manage --config-file /etc/trove/${cloudEnv}.conf datastore_version_flavor_add ${datastore} ${versionName} \$flavor_id --version ${versionNumber}
       done
       """
    }
}
