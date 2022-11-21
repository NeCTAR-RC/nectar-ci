def call(String project_name, String cloud_env, String availability_zone) {
    unstash 'build'
    script {
        imageId = readFile(file: 'build/.image-id').trim()
        imageName = readFile(file: 'build/.facts/nectar_name').trim()
        userAccount = readFile(file: 'build/.facts/default_user').trim()
        switch(cloud_env) {
          case "production":
            os_cred_id = '7a2e4b77-a292-47a1-b852-c0cfd9c1c383'
            os_auth_url = 'https://keystone.rc.nectar.org.au:5000/v3'
            break
          case "rctest":
            os_cred_id = '5c8f1b5c-2739-465e-ab10-e674b3fb884a'
            os_auth_url = 'https://keystone.test.rc.nectar.org.au:5000/v3'
            break
          case "development":
            os_cred_id = 'bcb39a6c-5aca-4900-94aa-63fb4364d8c2'
            os_auth_url = 'http://keystone.dev.rc.nectar.org.au:5000/v3'
            break
        }
    }
    withCredentials([usernamePassword(credentialsId: os_cred_id, usernameVariable: 'OS_USERNAME', passwordVariable: 'OS_PASSWORD'),
                     file(credentialsId: '10270abc-f1f9-47c7-ae66-114ae8246a71', variable: 'SSH_TESTING_KEY')]) {
        sh """#!/bin/bash
        echo "\033[33m========== Deploying to $cloud_env ==========\033[0m"
        export OS_AUTH_URL=$os_auth_url
        export OS_PROJECT_DOMAIN_NAME=Default
        export OS_USER_DOMAIN_NAME=Default
        export OS_IDENTITY_API_VERSION=3
        export OS_PROJECT_NAME=$project_name
        echo "Creating instance..."
        echo "==> openstack server create --image $imageId --flavor m3.xsmall --security-group image-build --key-name jenkins-image-testing --availability-zone $availability_zone 'test_$BUILD_TAG'"
        INSTANCE_ID=\$(openstack server create -f value -c id --image $imageId --flavor m3.xsmall --security-group image-build --key-name jenkins-image-testing --availability-zone $availability_zone "test_$BUILD_TAG")
        if [ -z "\$INSTANCE_ID" ]; then
            echo "Instance ID not found! Cleaning up image..."
            openstack image delete $imageId || true
            exit 1
        else
            echo "Found instance ID: \$INSTANCE_ID"
        fi
        RETRIES=20
        i=1
        while [ \$i -le \$RETRIES ]; do
            STATUS=\$(openstack server show -f value -c status \$INSTANCE_ID)
            echo "Instance is \$STATUS (\$i/\$RETRIES)..."
            [ "\$STATUS" = "ACTIVE" ] && break
            if [ \$i -ge \$RETRIES ]; then
                echo "ERROR: Limit reached, cleaning up..."
                openstack image delete $imageId || true
                openstack server delete \$INSTANCE_ID || true
                exit 1
            fi
            if [ "\$STATUS" = "ERROR" ]; then
                echo "Recreating instance due to error: \$(openstack server show -f value -c fault \$INSTANCE_ID)"
                openstack server delete \$INSTANCE_ID
                INSTANCE_ID=\$(openstack server create -f value -c id --image $imageId --flavor m3.xsmall --security-group image-build --key-name jenkins-image-testing --availability-zone $availability_zone "test_$BUILD_TAG")
                echo "Found instance ID: \$INSTANCE_ID"
            fi
            i=\$((i+1))
            sleep 60
        done
        openstack server show --max-width=120 \$INSTANCE_ID
        IP_ADDRESS=\$(openstack server show -f value -c accessIPv4 \$INSTANCE_ID)
        chmod 600 \$SSH_TESTING_KEY
        #echo "Sleeping for 60 seconds..."
        sleep 30 # time to settle for SSH key/fail2ban
        RETRIES=5
        j=1
        while [ \$j -le \$RETRIES ]; do
            echo "Checking for SSH to \$IP_ADDRESS (\$j/\$RETRIES)..."
            ssh -oLogLevel=error -oStrictHostKeyChecking=no -oUserKnownHostsFile=/dev/null -i \$SSH_TESTING_KEY ${userAccount}@\$IP_ADDRESS exit && break
            if [ \$j -ge \$RETRIES ]; then
                echo "ERROR: Limit reached. Showing console log..."
                #openstack console log show \$INSTANCE_ID
                nova console-log \$INSTANCE_ID
                echo "ERROR: Cleaning up instance and image..."
                openstack image delete $imageId || true
                openstack server delete \$INSTANCE_ID || true
                exit 1
            fi
            j=\$((j+1))
            sleep 60
        done
        echo "SSH ready. Sleeping 2 min for cloud-init to complete..."
        sleep 120
        set +e
        echo "==> ssh ${userAccount}@\$IP_ADDRESS '/bin/bash /usr/nectar/run_tests.sh'"
        ssh -oLogLevel=error -oStrictHostKeyChecking=no -oUserKnownHostsFile=/dev/null -i \$SSH_TESTING_KEY ${userAccount}@\$IP_ADDRESS '/bin/bash /usr/nectar/run_tests.sh'
        TEST_RESULT=\$?
        echo "Cleaning up test instance..."
        echo "==> openstack server delete \$INSTANCE_ID"
        openstack server delete \$INSTANCE_ID
        exit \$TEST_RESULT
        """
    }
}
