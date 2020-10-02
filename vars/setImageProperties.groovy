def call(String project_name, String cloud_env) {
    script {
        imageId = readFile(file: 'build/.image-id').trim()
        imageName = readFile(file: 'build/.facts/nectar_name').trim()
        if (cloud_env == 'production') {
            os_cred_id = '6c8091b5-0e7d-4be5-8458-4e5a999acdd6'
            os_auth_url = 'https://keystone.rc.nectar.org.au:5000/v3'
        } else {
            os_cred_id = 'cc826c4e-07fe-4a0c-b334-fb8100b23c7b'
            os_auth_url = 'https://keystone.test.rc.nectar.org.au:5000/v3'
        }
    }

    withCredentials([usernamePassword(credentialsId: os_cred_id, usernameVariable: 'OS_USERNAME', passwordVariable: 'OS_PASSWORD')]) {
       sh """
       set +x
       echo "\033[33m========== Set properties for $cloud_env ==========\033[0m"
       export OS_AUTH_URL=$os_auth_url
       export OS_PROJECT_DOMAIN_NAME=Default
       export OS_USER_DOMAIN_NAME=Default
       export OS_IDENTITY_API_VERSION=3
       export OS_PROJECT_NAME=$project_name
       IMAGE_NAME=\"'$imageName'\"
       [ -z "\$IMAGE_NAME" ] && exit 1
       echo "Setting nectar_name=\$IMAGE_NAME"
       openstack image set --property nectar_name="\$IMAGE_NAME" $imageId
       echo "Setting nectar_build=\$BUILD_NUMBER"
       openstack image set --property nectar_build=\$BUILD_NUMBER $imageId
       """
    }
}
