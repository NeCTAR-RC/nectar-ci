def call(String project_name, String cloud_env, String zones) {
    script {
        imageId = readFile(file: 'build/.image-id').trim()
        imageName = readFile(file: 'build/.facts/nectar_name').trim()
        switch(cloud_env) {
          case "production":
            os_cred_id = '6c8091b5-0e7d-4be5-8458-4e5a999acdd6'
            os_auth_url = 'https://keystone.rc.nectar.org.au:5000/v3'
            break
          case "rctest":
            os_cred_id = 'cc826c4e-07fe-4a0c-b334-fb8100b23c7b'
            os_auth_url = 'https://keystone.test.rc.nectar.org.au:5000/v3'
            break
          case "development":
            os_cred_id = 'bcb39a6c-5aca-4900-94aa-63fb4364d8c2'
            os_auth_url = 'http://keystone.dev.rc.nectar.org.au:5000/v3'
            break
        }
    }

    withCredentials([usernamePassword(credentialsId: os_cred_id, usernameVariable: 'OS_USERNAME', passwordVariable: 'OS_PASSWORD')]) {
       sh """#!/bin/bash -ex
       echo "\033[33m========== Deploy Bumblebee volumes for $cloud_env ==========\033[0m"
       export OS_AUTH_URL=$os_auth_url
       export OS_PROJECT_DOMAIN_NAME=Default
       export OS_USER_DOMAIN_NAME=Default
       export OS_IDENTITY_API_VERSION=3
       export OS_PROJECT_NAME=$project_name
       python3 scripts/bumblebee_deploy_volume.py -w -y -z $zones $imageId
       """
    }
}
