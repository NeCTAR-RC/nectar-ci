def call(String imageName) {
    git credentialsId: '4946c3a5-9f5e-4eac-9ec4-90e1e348db14', url: 'ssh://jenkins@review.rc.nectar.org.au:29418/NeCTAR-RC/nectar-images.git'
    sh """
        set +x
        echo "\033[34m========== Building ==========\033[0m"
        NAME=$imageName
        OUTPUT_DIR=\$WORKSPACE/output-\$BUILD_TAG
        rm -fr $OUTPUT_DIR
        rm -fr build
        jq ".builders[0].name = \\"\$BUILD_TAG\\" | .builders[0].vm_name = \\"\$BUILD_TAG\\"" \$NAME.json > \$BUILD_TAG.json
        echo "Starting packer build..."
        packer build -color=true \$BUILD_TAG.json
        echo "Shrinking image..."
        echo "==> qemu-img convert -c -o compat=0.10 -O qcow2 \$BUILD_TAG \$BUILD_TAG.qcow2"
        qemu-img convert -c -o compat=0.10 -O qcow2 \$OUTPUT_DIR/\$BUILD_TAG \$OUTPUT_DIR/image.qcow2
        rm -rf \$OUTPUT_DIR/\$BUILD_TAG
        echo `uuidgen` > build/.image-id
        mv \$OUTPUT_DIR build
    """
    
    stash includes: 'build/**', name: 'build'
}
