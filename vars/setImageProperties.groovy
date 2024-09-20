def call(String projectName, String cloudEnv) {
    unstash 'build'
    script {
        imageId = readFile(file: 'build/.image-id').trim()
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
        echo "\033[35;1m========== Setting image properties for $cloudEnv ==========\033[0m"

        export OS_AUTH_URL=$OSAuthURL
        export OS_PROJECT_DOMAIN_NAME=Default
        export OS_USER_DOMAIN_NAME=Default
        export OS_IDENTITY_API_VERSION=3
        export OS_PROJECT_NAME=$projectName

        IMAGE_ID=$imageId
        CLOUD_ENV=$cloudEnv

        echo "Applying properties..."

        # Loop through and apply facts as image properties
        for FACT in build/.facts/*; do
            PROP=\${FACT##*/}
            VAL=`cat \$FACT`

            # Skip Windows trait in non-prod environments
            if [[ "\$PROP" == "trait:CUSTOM_NECTAR_WINDOWS" && "\$CLOUD_ENV" != "production" ]]; then
               echo " -> Skipping '\$PROP' in '\$CLOUD_ENV'"
               continue
            fi

            echo " -> \$PROP: '\$VAL'"
            openstack image set --property \$PROP="\$VAL" \$IMAGE_ID
        done

        # Set nectar_build seperately from Jenkins build number
        echo " -> nectar_build: '\$BUILD_NUMBER'"
        openstack image set --property nectar_build=\$BUILD_NUMBER \$IMAGE_ID
        """
    }
}
