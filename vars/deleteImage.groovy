def call(String project_name, String cloud_env) {
    script {
        imageId = readFile(file: 'build/.image-id').trim()
        if (cloud_env == 'production') {
            os_cred_id = '7a2e4b77-a292-47a1-b852-c0cfd9c1c383'
            os_auth_url = 'https://keystone.rc.nectar.org.au:5000/v3'
        } else {
            os_cred_id = '5c8f1b5c-2739-465e-ab10-e674b3fb884a'
            os_auth_url = 'https://keystone.test.rc.nectar.org.au:5000/v3'
        }
    }
    withCredentials([usernamePassword(credentialsId: os_cred_id, usernameVariable: 'OS_USERNAME', passwordVariable: 'OS_PASSWORD')]) {
        sh """
        set +x
        echo "\033[33m========== Clean up RCTest ==========\033[0m"
        export OS_AUTH_URL=$os_auth_url
        export OS_PROJECT_DOMAIN_NAME=Default
        export OS_USER_DOMAIN_NAME=Default
        export OS_IDENTITY_API_VERSION=3
        export OS_PROJECT_NAME=$project_name
        echo "Cleaning up image (if found)..."
        echo "==> openstack image delete $imageId"
        openstack image delete $imageId || true
        """
    }
}
