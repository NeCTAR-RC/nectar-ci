/* Register a datastore version candidate for a new DB container patch.

   Container model: the database runs as a docker container whose tag is the
   datastore version's *version number* (e.g. mysql:8.4.10). The guest image is
   resolved by the 'trove' glance tag rather than pinned, so this row follows guest
   image promotions like every other live version.

   The candidate is named for its own patch (name == versionNumber). Once tested,
   trovePromoteDatastoreVersion renames it to the family name (e.g. 8.4). Trove keys
   rows on (name, version), so versionNumber must be passed identically wherever this
   row is looked up again.

   Idempotent: datastore_version_update is create-or-update, so re-running repoints
   the same row rather than creating a duplicate. */

def call(String cloudEnv, String datastore, String manager, String versionNumber) {
    def OSCredID
    def OSAuthURL
    script {
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
       # trove-manage, and an openstack client carrying python-troveclient, both
       # come from this venv (profile::core::trove_manage).
       . /opt/trove/bin/activate

       export OS_AUTH_URL=${OSAuthURL}
       export OS_PROJECT_DOMAIN_NAME=Default
       export OS_USER_DOMAIN_NAME=Default
       export OS_IDENTITY_API_VERSION=3
       export OS_PROJECT_NAME=trove

       # trove's setup.cfg only installs api-paste.ini under etc/trove, so a pip
       # install leaves the datastore templates inside the package rather than in
       # /etc/trove/templates as the distro package would. Resolve them from the
       # installed package so this doesn't hardcode a python version.
       TEMPLATES=\$(python -c 'import trove, os.path; print(os.path.join(os.path.dirname(trove.__file__), "templates"))')

       echo "\033[35;1m========== Registering ${datastore} ${versionNumber} (container ${manager}:${versionNumber}) in ${cloudEnv} ==========\033[0m"

       # Empty image id + --image-tags trove: resolve the guest image by tag, so this
       # row tracks whatever image-build-trove last promoted.
       echo "==> trove-manage --config-file /etc/trove/${cloudEnv}.conf datastore_version_update ${datastore} ${versionNumber} ${manager} '' '' 1 --image-tags trove --version ${versionNumber}"
       trove-manage --config-file /etc/trove/${cloudEnv}.conf datastore_version_update ${datastore} ${versionNumber} ${manager} '' '' 1 --image-tags trove --version ${versionNumber}

       echo "==> trove-manage --config-file /etc/trove/${cloudEnv}.conf db_load_datastore_config_parameters ${datastore} ${versionNumber} \$TEMPLATES/${manager}/validation-rules.json --version ${versionNumber}"
       trove-manage --config-file /etc/trove/${cloudEnv}.conf db_load_datastore_config_parameters ${datastore} ${versionNumber} \$TEMPLATES/${manager}/validation-rules.json --version ${versionNumber}

       # Flavors are associated per row, so the candidate needs them before an
       # instance can be booted on it. They survive the later rename.
       for flavor_id in `openstack flavor list --long --all | grep "flavor_class:name='trove'" | grep db3 | awk '{print \$2}'`; do
           echo "==> trove-manage --config-file /etc/trove/${cloudEnv}.conf datastore_version_flavor_add ${datastore} ${versionNumber} \$flavor_id --version ${versionNumber}"
           trove-manage --config-file /etc/trove/${cloudEnv}.conf datastore_version_flavor_add ${datastore} ${versionNumber} \$flavor_id --version ${versionNumber}
       done
       """
    }
}
