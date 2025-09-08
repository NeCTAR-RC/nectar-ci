def call(String projectName, String cloudEnv) {
    unstash 'build'
    script {
        switch(cloudEnv) {
          case "production":
            osCredId = '6c8091b5-0e7d-4be5-8458-4e5a999acdd6'
            osAuthUrl = 'https://identity.rc.nectar.org.au/v3'
            break
          case "testing":
            osCredId = 'cc826c4e-07fe-4a0c-b334-fb8100b23c7b'
            osAuthUrl = 'https://keystone.test.rc.nectar.org.au:5000/v3'
            break
          case "development":
            osCredId = 'bcb39a6c-5aca-4900-94aa-63fb4364d8c2'
            osAuthUrl = 'http://keystone.dev.rc.nectar.org.au:5000/v3'
            break
        }
    }
    withCredentials([usernamePassword(credentialsId: osCredId, usernameVariable: 'OS_USERNAME', passwordVariable: 'OS_PASSWORD')]) {
       sh """#!/bin/bash -eu
       echo "\033[35;1m========== Deploying for $cloudEnv ==========\033[0m"
       export OS_AUTH_URL=$osAuthUrl
       export OS_PROJECT_DOMAIN_NAME=Default
       export OS_USER_DOMAIN_NAME=Default
       export OS_IDENTITY_API_VERSION=3
       export OS_PROJECT_NAME=$projectName
       murano package-import --is-public --exists-action u package.zip
       """
    }
}
