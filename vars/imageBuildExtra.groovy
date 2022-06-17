def call(String imageName, String sourceImage, String gitRepo, String projectName) {
    git credentialsId: 'cd8b8dd3-b897-4ecb-985d-180d5b6f8498', url: gitRepo
    withCredentials([usernamePassword(credentialsId: '7a2e4b77-a292-47a1-b852-c0cfd9c1c383', usernameVariable: 'OS_USERNAME', passwordVariable: 'OS_PASSWORD')]) {
        sh """#!/bin/bash -eu
        echo "\033[33m========== Building ==========\033[0m"
        export OS_AUTH_URL=https://keystone.rc.nectar.org.au:5000/v3
        export OS_IDENTITY_API_VERSION=3
        export OS_PROJECT_NAME=$projectName
        export OS_PROJECT_DOMAIN_NAME=Default
        export OS_USER_DOMAIN_NAME=Default
        export OS_TENANT_NAME=\$OS_PROJECT_NAME  # packer fix
        export OS_DOMAIN_NAME=\$OS_PROJECT_DOMAIN_NAME  # packer fix
        rm -fr build; mkdir build
        rm -fr raw_image; mkdir raw_image
        SOURCE_ID=\$(openstack image list -c ID -f value --public --name '$sourceImage')
        echo "Found base image $sourceImage (\$SOURCE_ID)..."
        jq ".builders[0].source_image = \\"\$SOURCE_ID\\" | .builders[0].image_name = \\"\$BUILD_TAG\\"" packer.json > \$BUILD_TAG.json
        echo "Starting packer build..."
        chmod 600 packer-ssh-key
        packer build -color=true \$BUILD_TAG.json
        echo "Downloading built image..."
        echo "==> openstack image save --file raw_image/image-large.qcow2 \$BUILD_TAG"
        openstack image save --file raw_image/image-large.qcow2 \$BUILD_TAG
        openstack image delete \$BUILD_TAG
        echo "Shrinking image..."
        echo "==> qemu-img convert -c -o compat=0.10 -O qcow2 raw_image/image-large.qcow2 raw_image/image.qcow2"
        qemu-img convert -c -o compat=0.10 -O qcow2 raw_image/image-large.qcow2 raw_image/image.qcow2
        rm -rf raw_image/image-large.qcow2
        mv -v \$WORKSPACE/ansible/.facts build/
        # Hack to ensure guest agent works
        echo "True" > build/.facts/hw_qemu_guest_agent
        """
    }
    stash includes: 'build/**', name: 'build'
    stash includes: 'raw_image/**', name: 'raw_image'
}
