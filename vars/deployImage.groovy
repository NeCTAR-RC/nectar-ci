def call(String projectName, String cloudEnv) {
    unstash 'build'
    unstash 'raw_image'
    script {
        imageId = readFile(file: 'build/.image-id').trim()
        imageName = readFile(file: 'build/.facts/nectar_name').trim()
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
        echo "\033[35;1m========== Deploying to $cloudEnv ==========\033[0m"

        export OS_AUTH_URL=$OSAuthURL
        export OS_PROJECT_DOMAIN_NAME=Default
        export OS_USER_DOMAIN_NAME=Default
        export OS_IDENTITY_API_VERSION=3
        export OS_PROJECT_NAME=$projectName

        IMAGE_ID=$imageId
        IMAGE_NAME=\"$imageName\"

        if [ -z "\$IMAGE_NAME" ]; then
            echo "ERROR: Image name not found!"
            exit 1
        fi

        echo "Creating image..."
        echo "--> openstack image create --id \$IMAGE_ID --disk-format qcow2 --container-format bare --file image.qcow2 \"NeCTAR \$IMAGE_NAME\""
        openstack image create -f value -c id --id \$IMAGE_ID --disk-format qcow2 --container-format bare --file raw_image/image.qcow2 "NeCTAR \$IMAGE_NAME" > image_id.txt
        [ -s image_id.txt ] || exit 1
        echo "Image \$IMAGE_ID created!"

        RETRIES=10
        i=1
        while [ \$i -le \$RETRIES ]; do
            STATUS=\$(openstack image show -f value -c status \$IMAGE_ID)
            echo "Image is \$STATUS (\$i/\$RETRIES)..."
            [ "\$STATUS" = "active" ] && break
            if [ \$i -ge \$RETRIES ]; then
                echo "ERROR: Limit reached, cleaning up image..."
                openstack image delete \$IMAGE_ID || true
                exit 1
            fi
            i=\$((i+1))
            sleep 30
        done
        openstack image show --max-width=120 \$IMAGE_ID
        """
    }
}
