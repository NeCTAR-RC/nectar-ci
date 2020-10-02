def call(String project_name, String tag) {
    script {
        imageId = readFile(file: 'build/.image-id').trim()
        imageName = readFile(file: 'build/.facts/nectar_name').trim()
    }
    withCredentials([usernamePassword(credentialsId: 'cc826c4e-07fe-4a0c-b334-fb8100b23c7b', usernameVariable: 'OS_USERNAME', passwordVariable: 'OS_PASSWORD')]) {
       sh """
       set +x
       echo "\033[33m========== Promote to RCTest ==========\033[0m"
       export OS_AUTH_URL=https://keystone.test.rc.nectar.org.au:5000/v3
       export OS_PROJECT_DOMAIN_NAME=Default
       export OS_USER_DOMAIN_NAME=Default
       export OS_IDENTITY_API_VERSION=3
       export OS_PROJECT_NAME=$project_name
       IMAGE_ID="''' + "$imageId" + '''"
       echo "Setting tag..."
       echo "==> openstack image set --tag $tag \$IMAGE_ID"
       #openstack image set --tag $tag \$IMAGE_ID
       """
    }
}
