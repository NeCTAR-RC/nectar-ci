def call(String imageName, String sourceImage, String gitRepo) {
    git credentialsId: '4946c3a5-9f5e-4eac-9ec4-90e1e348db14', url: gitRepo
    withCredentials([usernamePassword(credentialsId: '7a2e4b77-a292-47a1-b852-c0cfd9c1c383', usernameVariable: 'OS_USERNAME', passwordVariable: 'OS_PASSWORD')]) {
        sh """#!/bin/bash -eu
        echo "\033[33m========== Building ==========\033[0m"
        export OS_AUTH_URL=https://keystone.rc.nectar.org.au:5000/v3
        export OS_PROJECT_DOMAIN_NAME=Default
        export OS_USER_DOMAIN_NAME=Default
        export OS_IDENTITY_API_VERSION=3
        export OS_PROJECT_NAME=NeCTAR-Images
        OUTPUT_DIR=\$WORKSPACE/output-\$BUILD_TAG
        rm -fr \$OUTPUT_DIR build
        SOURCE_ID=\$(openstack image show -f value -c id "$sourceImage")
        echo "Found base image $sourceImage (\$SOURCE_ID)..."
        jq ".builders[0].source_image = \\"\$SOURCE_ID\\" | .builders[0].image_name = \\"\$BUILD_TAG\\"" packer.json > \$BUILD_TAG.json
        echo "Starting packer build..."
        packer build -color=true \$BUILD_TAG.json
        echo "Downloading built image..."
        echo "==> openstack image save --file \$OUTPUT_DIR/\$BUILD_TAG-large.qcow2 \$BUILD_TAG"
        openstack image save --file \$OUTPUT_DIR/\$BUILD_TAG-large.qcow2 \$BUILD_TAG
        openstack image delete \$BUILD_TAG
        echo "Shrinking image..."
        echo "==> qemu-img convert -c -o compat=0.10 -O qcow2 \$OUTPUT_DIR/\$BUILD_TAG-large.qcow2 \$OUTPUT_DIR/image.qcow2"
        qemu-img convert -c -o compat=0.10 -O qcow2 \$OUTPUT_DIR/\$BUILD_TAG-large.qcow2 \$OUTPUT_DIR/image.qcow2
        rm -rf \$OUTPUT_DIR/\$BUILD_TAG-large.qcow2
        mv \$OUTPUT_DIR build
        mkdir -p raw_image
        mv build/image.qcow2 raw_image/
    """
    stash includes: 'build/**', name: 'build'
    stash includes: 'raw_image/**', name: 'raw_image'
}
