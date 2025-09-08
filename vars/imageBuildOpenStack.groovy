def call(String imageName, String projectName) {
    git credentialsId: 'cd8b8dd3-b897-4ecb-985d-180d5b6f8498', url: 'ssh://jenkins@review.rc.nectar.org.au:29418/NeCTAR-RC/nectar-images.git'
    withCredentials([usernamePassword(credentialsId: '7a2e4b77-a292-47a1-b852-c0cfd9c1c383', usernameVariable: 'OS_USERNAME', passwordVariable: 'OS_PASSWORD')]) {
        sh """#!/bin/bash -eu
        echo "\033[35;1m========== Building ==========\033[0m"

        export OS_AUTH_URL=https://identity.rc.nectar.org.au/v3
        export OS_IDENTITY_API_VERSION=3
        export OS_PROJECT_NAME=$projectName
        export OS_PROJECT_DOMAIN_NAME=Default
        export OS_USER_DOMAIN_NAME=Default

        IMAGE_NAME=$imageName
        OUTPUT_DIR=\$WORKSPACE/builds/build_files/packer-\$IMAGE_NAME

        echo "Starting packer init..."
        make init only=openstack.vm target=\$IMAGE_NAME

        echo "Starting packer build..."
        make build only=openstack.vm target=\$IMAGE_NAME

        # Clean up any left over build
        rm -vfr build
        # Move image build files to build directory
        mv \$OUTPUT_DIR build
        mkdir -p raw_image
        mv build/image.qcow2 raw_image/
        """
    }
    stash includes: 'build/**', name: 'build'
    stash includes: 'raw_image/**', name: 'raw_image'
}
