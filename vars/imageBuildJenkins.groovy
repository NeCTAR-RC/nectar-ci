def call(String imageName) {
    git credentialsId: 'cd8b8dd3-b897-4ecb-985d-180d5b6f8498', url: 'ssh://jenkins@review.rc.nectar.org.au:29418/NeCTAR-RC/nectar-images.git'
    sh """#!/bin/bash -eu
        echo "\033[34m========== Building ==========\033[0m"

        IMAGE_NAME=$imageName
        OUTPUT_DIR=\$WORKSPACE/builds/build_files/packer-\$IMAGE_NAME

        # Clean up any left over builds
        if [ -d \$OUTPUT_DIR ]; then
            echo "Cleaning up output dir..."
            rm -fr \$OUTPUT_DIR
            rm -fr build
        fi

        echo "Starting packer init..."
        make init target=\$IMAGE_NAME

        echo "Starting packer build..."
        make build target=\$IMAGE_NAME

        # Move image build files to build directory
        mv \$OUTPUT_DIR build
        mkdir -p raw_image
        mv build/image.qcow2 raw_image/

        # Image name will be like: jenkins-slave-ubuntu-20.04-v10
        echo "\$IMAGE_NAME-v\$BUILD_NUMBER" > build/.facts/nectar_name
    """
    stash includes: 'build/**', name: 'build'
    stash includes: 'raw_image/**', name: 'raw_image'
}
