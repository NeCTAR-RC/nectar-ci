/* Deactivate the throwaway test datastore version. Used both to tear it down
   after a successful promote and to roll it back on failure.

   versionNumber must match what troveRegisterTestDatastore used: trove keys the
   row on (name, version), so omitting it (version would default to the name)
   would miss the registered row and create a duplicate instead of deactivating
   it. Idempotent: datastore_version_update is create-or-update, so this is safe
   even if the test version was never registered. */

def call(String cloudEnv, String datastore, String versionName, String versionNumber) {
    sh """#!/bin/bash
    # trove-manage comes from this venv (profile::core::trove_manage).
    . /opt/trove/bin/activate

    echo "\033[35;1m========== Deactivating datastore version ${datastore} ${versionName} (version ${versionNumber}) in ${cloudEnv} ==========\033[0m"
    echo "==> trove-manage --config-file /etc/trove/${cloudEnv}.conf datastore_version_update ${datastore} ${versionName} ${datastore} '' '' 0 --version ${versionNumber}"
    trove-manage --config-file /etc/trove/${cloudEnv}.conf datastore_version_update ${datastore} ${versionName} ${datastore} '' '' 0 --version ${versionNumber}
    """
}
