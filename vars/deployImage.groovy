def call(String project_name, String cloud_env) {
    unstash 'build'
    unstash 'raw_image'
    script {
        imageId = readFile(file: 'build/.image-id').trim()
        imageName = readFile(file: 'build/.facts/nectar_name').trim()
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
        echo "\033[33m========== Deploying to $cloud_env ==========\033[0m"
        export OS_AUTH_URL=$os_auth_url
        export OS_PROJECT_DOMAIN_NAME=Default
        export OS_USER_DOMAIN_NAME=Default
        export OS_IDENTITY_API_VERSION=3
        export OS_PROJECT_NAME=$project_name
        IMAGE_NAME=\"$imageName\"
        [ -z "\$IMAGE_NAME" ] && exit 1
        echo "Creating image \$IMAGE_NAME..."
        echo "==> openstack image create --id $imageId --disk-format qcow2 --container-format bare --file raw_image/image.qcow2 'NeCTAR $imageName'"
        openstack image create -f value -c id --id $imageId --disk-format qcow2 --container-format bare --file raw_image/image.qcow2 "NeCTAR $imageName" > image_id.txt
        [ -s image_id.txt ] || exit 1
        echo "Image $imageId created!"
        RETRIES=10
        i=1
        while [ \$i -le \$RETRIES ]; do
            STATUS=\$(openstack image show -f value -c status $imageId)
            echo "Image is \$STATUS (\$i/\$RETRIES)..."
            [ "\$STATUS" = "active" ] && break
            if [ \$i -ge \$RETRIES ]; then
                echo "ERROR: Limit reached, cleaning up image..."
                openstack image delete $imageId || true
                exit 1
            fi
            i=\$((i+1))
            sleep 30
        done
        echo "Applying properties..."
        for FACT in build/.facts/*; do
           PROP=\${FACT##*/}
           if ! echo "\$PROP" | grep -q '^nectar_'; then
               VAL=`cat \$FACT`
               echo " -> \$PROP: '\$VAL'..."
               openstack image set --property \$PROP="\$VAL" $imageId
           fi
        done
        openstack image show --max-width=120 $imageId
        """
    }
}
