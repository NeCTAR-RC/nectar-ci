def call(String project_name, String cloud_env) {
    script {
        if ($cloud_env == 'production') {
            def os_cred_id = '7a2e4b77-a292-47a1-b852-c0cfd9c1c383'
            def os_auth_url = 'https://keystone.rc.nectar.org.au:5000/v3'
        } else {
            def os_cred_id = '5c8f1b5c-2739-465e-ab10-e674b3fb884a'
            def os_auth_url = 'https://keystone.test.rc.nectar.org.au:5000/v3'
        }
    }
    sh """#!/bin/bash -x

    echo "cloud env=$cloud_env"
    echo "creds=$os_cred_id"
    echo "$keystone=os_auth_url"
    }
}
