def call(String project_name, String cloud_env) {
    unstash 'build'
    script {
        imageId = readFile(file: 'build/.image-id').trim()
        switch(cloud_env) {
          case "production":
            os_cred_id = '7a2e4b77-a292-47a1-b852-c0cfd9c1c383'
            os_auth_url = 'https://keystone.rc.nectar.org.au:5000/v3'
            break
          case "testing":
            os_cred_id = '5c8f1b5c-2739-465e-ab10-e674b3fb884a'
            os_auth_url = 'https://keystone.test.rc.nectar.org.au:5000/v3'
            break
          case "development":
            os_cred_id = 'bcb39a6c-5aca-4900-94aa-63fb4364d8c2'
            os_auth_url = 'http://keystone.dev.rc.nectar.org.au:5000/v3'
            break
        }
    }
    withCredentials([usernamePassword(credentialsId: os_cred_id, usernameVariable: 'OS_USERNAME', passwordVariable: 'OS_PASSWORD')]) {
        sh """#!/bin/bash
        echo "\033[33m========== Clean up $cloud_env ==========\033[0m"
        export OS_AUTH_URL=$os_auth_url
        export OS_PROJECT_DOMAIN_NAME=Default
        export OS_USER_DOMAIN_NAME=Default
        export OS_IDENTITY_API_VERSION=3
        export OS_PROJECT_NAME=$project_name
        echo "Cleaning up image (if found)..."
        echo "==> openstack image delete $imageId"
        openstack image delete $imageId || true
        """
    }
}
