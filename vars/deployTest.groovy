def call(String project_name) {
    unstash 'build'
    script {
        imageId = readFile(file: 'build/.image-id').trim()
        imageName = readFile(file: 'build/.facts/nectar_name').trim()
    }
    dir('build') {
        withCredentials([usernamePassword(credentialsId: '5c8f1b5c-2739-465e-ab10-e674b3fb884a', usernameVariable: 'OS_USERNAME', passwordVariable: 'OS_PASSWORD')]) {
            sh """
            set +x
            echo "\033[33m========== Deploying to RCTest ==========\033[0m"
            export OS_AUTH_URL=https://keystone.test.rc.nectar.org.au:5000/v3
            export OS_PROJECT_DOMAIN_NAME=Default
            export OS_USER_DOMAIN_NAME=Default
            export OS_IDENTITY_API_VERSION=3
            export OS_PROJECT_NAME=$project_name
            env | grep OS_
            IMAGE_ID="''' + "$imageId" + '''"
            IMAGE_NAME="''' + "$imageName" + '''"
            [ -z "\$IMAGE_NAME" ] && exit 1
            echo "Creating image \$IMAGE_NAME..."
            echo "==> openstack image create --id \$IMAGE_ID --disk-format qcow2 --container-format bare --file image.qcow2 \"NeCTAR \$IMAGE_NAME\""
            #openstack image create -f value -c id --id \$IMAGE_ID --disk-format qcow2 --container-format bare --file image.qcow2 "NeCTAR \$IMAGE_NAME" > image_id.txt
            echo "Image \$IMAGE_ID created!"
            echo "Applying properties..."

            #openstack image show --max-width=120 \$IMAGE_ID
            """
        }
    }
}
