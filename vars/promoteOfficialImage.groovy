def call(String projectName, String cloudEnv) {
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
            OSCredID = 'ddcd951d-3072-4684-a144-f244cfb705b9'
            OSAuthURL = 'http://keystone.dev.rc.nectar.org.au:5000/v3'
            break
        }
    }

    withCredentials([usernamePassword(credentialsId: OSCredID, usernameVariable: 'OS_USERNAME', passwordVariable: 'OS_PASSWORD')]) {
       sh """#!/bin/bash -eu
       echo "\033[35;1m========== Promote image for $cloudEnv ==========\033[0m"

       export OS_AUTH_URL=$OSAuthURL
       export OS_PROJECT_DOMAIN_NAME=Default
       export OS_USER_DOMAIN_NAME=Default
       export OS_IDENTITY_API_VERSION=3
       export OS_PROJECT_NAME=$projectName

       IMAGE_ID=$imageId
       IMAGE_NAME=\"$imageName\"

       echo "================================================================================"
       echo "  NeCTAR \$IMAGE_NAME v\$BUILD_NUMBER build successful!"
       echo "  Image ID: \$IMAGE_ID"
       echo "================================================================================"
       openstack image show --max-width=120 \$IMAGE_ID

       echo "Promoting image to public..."
       echo "==> scripts/aggrandise.py -e $cloudEnv -i \$IMAGE_ID promote"
       $WORKSPACE/scripts/aggrandise.py -e $cloudEnv -i \$IMAGE_ID promote
       """
    }
}
