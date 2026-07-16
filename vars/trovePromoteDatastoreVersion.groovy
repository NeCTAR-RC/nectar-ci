/* Promote a tested candidate to be the family head, by rename-swap.

   Trove cannot change a datastore version's number in place -- the number is part of
   the row's lookup key -- and two rows sharing a name break DatastoreVersion.load()
   for the whole datastore. So promotion is done by renaming instead: the outgoing
   head is retired to its own patch name (8.4 -> 8.4.9), then the tested candidate
   takes the family name (8.4.10 -> 8.4).

   Order matters. Retire the head FIRST so the family name is free; renaming the
   candidate first would briefly leave two rows named <family> and break the
   datastore for everyone. There is a short window with no row named <family>, so the
   two renames are kept back to back.

   Existing instances are unaffected: they reference their version by UUID, so a
   retired row keeps serving them until they are upgraded. Renaming is the only
   mutation here -- nothing is deleted (Trove refuses to delete a version referenced
   by any instance, backup or configuration).

   Needs a troveclient new enough to have `datastore version set --version-name`. */

def call(String cloudEnv, String datastore, String family, String candidateVersionName) {
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

       echo "\033[35;1m========== Promoting ${datastore} ${candidateVersionName} to ${family} in ${cloudEnv} ==========\033[0m"

       # Columns are ID, Name, Version -- one call gives us the head's id and its patch.
       LIST=\$(openstack database datastore version list ${datastore} -f value -c ID -c Name -c Version)

       CAND_ID=\$(echo "\$LIST" | awk -v c="${candidateVersionName}" '\$2 == c {print \$1}')
       HEAD_ID=\$(echo "\$LIST" | awk -v f="${family}" '\$2 == f {print \$1}')
       HEAD_VER=\$(echo "\$LIST" | awk -v f="${family}" '\$2 == f {print \$3}')

       if [ -z "\$CAND_ID" ]; then
           echo "ERROR: candidate '${candidateVersionName}' not found in ${datastore}"
           exit 1
       fi

       if [ "\$HEAD_ID" = "\$CAND_ID" ]; then
           echo "'${family}' already resolves to ${candidateVersionName}, nothing to promote"
           exit 0
       fi

       if [ -n "\$HEAD_ID" ]; then
           if [ -z "\$HEAD_VER" ] || [ "\$HEAD_VER" = "${family}" ]; then
               echo "ERROR: current '${family}' head has no distinct version number (got '\$HEAD_VER')."
               echo "Refusing to retire it -- renaming it to '${family}' would be a no-op and collide."
               exit 1
           fi
           CLASH=\$(echo "\$LIST" | awk -v v="\$HEAD_VER" '\$2 == v {print \$1}')
           if [ -n "\$CLASH" ]; then
               echo "ERROR: a version named '\$HEAD_VER' already exists (\$CLASH)."
               echo "Retiring the head would create two rows named '\$HEAD_VER'."
               exit 1
           fi
           echo "Retiring current head \$HEAD_ID: '${family}' -> '\$HEAD_VER'"
           openstack database datastore version set \$HEAD_ID --version-name \$HEAD_VER
       else
           echo "No existing '${family}' head; promoting candidate directly"
       fi

       echo "Promoting \$CAND_ID: '${candidateVersionName}' -> '${family}'"
       openstack database datastore version set \$CAND_ID --version-name ${family}

       echo "Datastore versions for ${datastore} in ${cloudEnv} now:"
       openstack database datastore version list ${datastore}
       """
    }
}
