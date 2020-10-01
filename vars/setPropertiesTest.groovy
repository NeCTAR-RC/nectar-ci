def call(String project_name) {
    script {
        imageId = readFile(file: 'build/.image-id').trim()
        imageName = readFile(file: 'build/.facts/nectar_name').trim()
    }

    withCredentials([usernamePassword(credentialsId: 'cc826c4e-07fe-4a0c-b334-fb8100b23c7b', usernameVariable: 'OS_USERNAME', passwordVariable: 'OS_PASSWORD')]) {
       sh """
       set +x
       echo "\033[33m========== Set properties RCTest ==========\033[0m"
       export OS_AUTH_URL=https://keystone.test.rc.nectar.org.au:5000/v3
       export OS_PROJECT_DOMAIN_NAME=Default
       export OS_USER_DOMAIN_NAME=Default
       export OS_IDENTITY_API_VERSION=3
       export OS_PROJECT_NAME=$project_name
       IMAGE_ID="''' + "$imageId" + '''"
       IMAGE_NAME="''' + "$imageName" + '''"
       [ -z "\$IMAGE_NAME" ] && exit 1
       echo "Setting nectar_name='\$IMAGE_NAME'"
       #openstack image set --property nectar_name="\$IMAGE_NAME" \$IMAGE_ID
       echo "Setting nectar_build=\$BUILD_NUMBER"
       #openstack image set --property nectar_build=\$BUILD_NUMBER \$IMAGE_ID
       """
    }
}
