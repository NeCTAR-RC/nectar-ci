/* Deactivate a datastore version by name, if it still exists under that name.

   Used to clean up a candidate whose tests failed, so users can't create instances
   on an unvalidated patch. Deactivating only blocks new instance creation -- it does
   not touch running instances.

   Deliberately looks the row up and acts on its UUID rather than using
   `trove-manage datastore_version_update ... 0`: that call is create-or-update keyed
   on (name, version), so if the candidate had already been promoted (and therefore
   renamed to the family name) it would MINT a stray inactive row instead of finding
   anything -- which would then collide with a later retire of that same patch.

   Safe to call unconditionally from a post{} block: a missing row is a no-op. */

def call(String cloudEnv, String datastore, String versionName) {
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
       # `openstack database ...` needs python-troveclient, which only the
       # /opt/trove venv has (profile::core::trove_manage). The system
       # python3-openstackclient on the internal slaves has no database subcommand.
       . /opt/trove/bin/activate

       export OS_AUTH_URL=${OSAuthURL}
       export OS_PROJECT_DOMAIN_NAME=Default
       export OS_USER_DOMAIN_NAME=Default
       export OS_IDENTITY_API_VERSION=3
       export OS_PROJECT_NAME=trove

       echo "\033[35;1m========== Deactivating ${datastore} ${versionName} in ${cloudEnv} ==========\033[0m"

       ID=\$(openstack database datastore version list ${datastore} -f value -c ID -c Name \
             | awk -v n="${versionName}" '\$2 == n {print \$1}')

       if [ -z "\$ID" ]; then
           echo "No version named '${versionName}' in ${datastore}; nothing to deactivate"
           exit 0
       fi

       echo "==> openstack database datastore version set \$ID --disable"
       openstack database datastore version set \$ID --disable
       """
    }
}
