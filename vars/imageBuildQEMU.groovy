def call(String imageName) {
    git credentialsId: '4946c3a5-9f5e-4eac-9ec4-90e1e348db14', url: 'ssh://jenkins@review.rc.nectar.org.au:29418/NeCTAR-RC/nectar-images.git'
    sh """#!/bin/bash -eu
        echo "\033[34m========== Building ==========\033[0m"
        NAME=$imageName
        OUTPUT_DIR=\$WORKSPACE/output-\$BUILD_TAG
        rm -fr \$OUTPUT_DIR
        rm -fr build
        jq ".builders[0].name = \\"\$BUILD_TAG\\" | .builders[0].vm_name = \\"\$BUILD_TAG\\"" \$NAME.json > \$BUILD_TAG.json
        echo "Starting packer build..."
        chmod 600 packer-ssh-key
        packer build -color=true \$BUILD_TAG.json
        echo "Shrinking image..."
        echo "==> qemu-img convert -c -o compat=0.10 -O qcow2 \$BUILD_TAG \$BUILD_TAG.qcow2"
        qemu-img convert -c -o compat=0.10 -O qcow2 \$OUTPUT_DIR/\$BUILD_TAG \$OUTPUT_DIR/image.qcow2
        rm -rf \$OUTPUT_DIR/\$BUILD_TAG
        mv \$OUTPUT_DIR build
        mkdir -p raw_image
        mv build/image.qcow2 raw_image/
    """
    stash includes: 'build/**', name: 'build'
    stash includes: 'raw_image/**', name: 'raw_image'
}
