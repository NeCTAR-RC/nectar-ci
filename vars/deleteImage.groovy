def call(String project_name) {
    script {
        imageId = readFile(file: 'build/.image-id').trim()
    }
    withCredentials([usernamePassword(credentialsId: '5c8f1b5c-2739-465e-ab10-e674b3fb884a', usernameVariable: 'OS_USERNAME', passwordVariable: 'OS_PASSWORD')]) {
        sh """
        set +x
        echo "\033[33m========== Clean up RCTest ==========\033[0m"
        export OS_AUTH_URL=https://keystone.test.rc.nectar.org.au:5000/v3
        export OS_PROJECT_DOMAIN_NAME=Default
        export OS_USER_DOMAIN_NAME=Default
        export OS_IDENTITY_API_VERSION=3
        export OS_PROJECT_NAME=$project_name
        echo "Cleaning up image (if found)..."
        echo "==> openstack image delete $imageId"
        openstack image delete $imageId || true
        '''
    }
    withCredentials([usernamePassword(credentialsId: '7a2e4b77-a292-47a1-b852-c0cfd9c1c383', usernameVariable: 'OS_USERNAME', passwordVariable: 'OS_PASSWORD')]) {
        sh """
        set +x
        echo "\033[31m========== Clean up Production ==========\033[0m"
        export OS_AUTH_URL=https://keystone.rc.nectar.org.au:5000/v3
        export OS_PROJECT_DOMAIN_NAME=Default
        export OS_USER_DOMAIN_NAME=Default
        export OS_IDENTITY_API_VERSION=3
        export OS_PROJECT_NAME=$project_name
        echo "Cleaning up image (if found)..."
        echo "==> openstack image delete $imageId"
        openstack image delete $imageId || true
        '''
    }
}
