def call(String project_name) {
    unstash 'build'
    script {
        imageId = readFile(file: 'build/.image-id').trim()
        imageName = readFile(file: 'build/.facts/nectar_name').trim()
    }
    dir('build') {
        withCredentials([usernamePassword(credentialsId: '5c8f1b5c-2739-465e-ab10-e674b3fb884a', usernameVariable: 'OS_USERNAME', passwordVariable: 'OS_PASSWORD')]) {
            sh """#!/bin/bash
            set +x
            echo "\033[33m========== Deploying to RCTest ==========\033[0m"
            export OS_AUTH_URL=https://keystone.test.rc.nectar.org.au:5000/v3
            export OS_PROJECT_DOMAIN_NAME=Default
            export OS_USER_DOMAIN_NAME=Default
            export OS_IDENTITY_API_VERSION=3
            export OS_PROJECT_NAME=$project_name
            [ -z "$imageName" ] && exit 1
            echo "Creating image $imageName..."
            echo "==> openstack image create --id $imageId --disk-format qcow2 --container-format bare --file image.qcow2 \"NeCTAR $imageName\""
            #openstack image create -f value -c id --id $imageId --disk-format qcow2 --container-format bare --file image.qcow2 "NeCTAR $imageName" > image_id.txt
            echo "Image \$imageId created!"
            echo "Applying properties..."
            for FACT in .facts/*; do 
               PROP=\${FACT#*/}
               if ! echo "\$PROP" | grep -q '^nectar_'; then
                   VAL=`cat \$FACT`
                   echo " -> \$PROP: '\$VAL'..."
                   #openstack image set --property \$PROP="\$VAL" $imageId
               fi
            done
            #openstack image show --max-width=120 $imageId
            """
        }
    }
}
