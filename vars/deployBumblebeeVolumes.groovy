def call(String projectName, String cloudEnv, String zones) {
    script {
        imageId = readFile(file: 'build/.image-id').trim()
        imageName = readFile(file: 'build/.facts/nectar_name').trim()
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
       sh """#!/bin/bash -ex
       echo "\033[35;1m========== Deploy Bumblebee volumes for $cloudEnv ==========\033[0m"
       export OS_AUTH_URL=$OSAuthURL
       export OS_PROJECT_DOMAIN_NAME=Default
       export OS_USER_DOMAIN_NAME=Default
       export OS_IDENTITY_API_VERSION=3
       export OS_PROJECT_NAME=$projectName
       python3 scripts/bumblebee_deploy_volume.py -w -y -z $zones $imageId
       """
    }
}
