def call(String projectName, String cloudEnv) {
    unstash 'build'
    script {
        imageId = readFile(file: 'build/.image-id').trim()
        switch(cloudEnv) {
          case "production":
            OSCredID = '7a2e4b77-a292-47a1-b852-c0cfd9c1c383'
            OSAuthURL = 'https://keystone.rc.nectar.org.au:5000/v3'
            break
          case "testing":
            OSCredID = '5c8f1b5c-2739-465e-ab10-e674b3fb884a'
            OSAuthURL = 'https://keystone.test.rc.nectar.org.au:5000/v3'
            break
          case "development":
            OSCredID = 'bcb39a6c-5aca-4900-94aa-63fb4364d8c2'
            OSAuthURL = 'http://keystone.dev.rc.nectar.org.au:5000/v3'
            break
        }
    }
    withCredentials([usernamePassword(credentialsId: OSCredID, usernameVariable: 'OS_USERNAME', passwordVariable: 'OS_PASSWORD')]) {
        sh """#!/bin/bash -eu
        echo "\033[35;1m========== Clean up $cloudEnv ==========\033[0m"
        export OS_AUTH_URL=$OSAuthURL
        export OS_PROJECT_DOMAIN_NAME=Default
        export OS_USER_DOMAIN_NAME=Default
        export OS_IDENTITY_API_VERSION=3
        export OS_PROJECT_NAME=$projectName
        echo "Cleaning up image (if found)..."
        echo "==> openstack image delete $imageId"
        openstack image delete $imageId || true
        """
    }
}
