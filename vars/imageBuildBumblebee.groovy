def call(String imageName, String projectName) {
    withCredentials([usernamePassword(credentialsId: '7a2e4b77-a292-47a1-b852-c0cfd9c1c383', usernameVariable: 'OS_USERNAME', passwordVariable: 'OS_PASSWORD')]) {
        sh """#!/bin/bash -eu
        echo "\033[35;1m========== Building ==========\033[0m"
        export OS_AUTH_URL=https://identity.rc.nectar.org.au/v3
        export OS_IDENTITY_API_VERSION=3
        export OS_PROJECT_NAME=$projectName
        export OS_PROJECT_DOMAIN_NAME=Default
        export OS_USER_DOMAIN_NAME=Default
        export OS_TENANT_NAME=\$OS_PROJECT_NAME  # packer fix
        export OS_DOMAIN_NAME=\$OS_PROJECT_DOMAIN_NAME  # packer fix
        rm -fr build; mkdir build
        rm -fr raw_image; mkdir raw_image
        NAME=$imageName
        SOURCE_NAME=\$(jq -r '.builders[0].source_image' \$NAME.json)
        echo "Finding image ID for \$SOURCE_NAME..."
        SOURCE_ID=\$(openstack image list -c ID -f value --public --name "\$SOURCE_NAME")
        echo "Found base image \$SOURCE_NAME (\$SOURCE_ID)"
        jq ".builders[0].source_image = \\"\$SOURCE_ID\\" | .builders[0].image_name = \\"\$BUILD_TAG\\"" \$NAME.json > \$BUILD_TAG.json
        echo "Starting packer build..."
        chmod 600 packer-ssh-key
        export PACKER_PLUGIN_PATH=`which packer | xargs dirname`
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
