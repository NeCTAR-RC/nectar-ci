def call(String imageName, String kubernetesVersion) {
    git branch: 'main', url: 'https://github.com/kubernetes-sigs/image-builder'
    sh """#!/bin/bash -eux
        echo "\033[34m========== Building ==========\033[0m"

        IMAGE_NAME=$imageName
        KUBERNETES_VERSION=$kubernetesVersion

        OUTPUT_NAME="\$IMAGE_NAME-kube-v\$KUBERNETES_VERSION"
        OUTPUT_DIR="\$WORKSPACE/images/capi/output/\$FILE_NAME/"

        # Clean up any left over builds
        if [ -d \$OUTPUT_DIR ]; then
            echo "Cleaning up output dir..."
            rm -fr \$OUTPUT_DIR
            rm -fr build
        fi

        echo "Starting build..."
        cd \$WORKSPACE/images/capi
        PACKER_FLAGS="\
        --var 'kubernetes_rpm_version=\${KUBERNETES_RPM_VERSION:-\$KUBERNETES_VERSION}' \
        --var 'kubernetes_semver=v\${KUBERNETES_SEMVER:-\$KUBERNETES_VERSION}' \
        --var 'kubernetes_series=v\${KUBERNETES_SERIES:-\${KUBERNETES_VERSION%.*}}' \
        --var 'kubernetes_deb_version=\${KUBERNETES_DEB_VERSION:-\${KUBERNETES_VERSION}-1.1}' \
        --var vnc_bind_address=0.0.0.0" \
        make build-qemu-\$IMAGE_NAME

        cd \$WORKSPACE

        # Move image build files to build directory
        mv \$OUTPUT_DIR build
        mkdir -p raw_image

        echo "Compressing QCOW2 image..."
        qemu-img convert -c -O qcow2 "build/\$OUTPUT_NAME" raw_image/image.qcow2
        rm -fr build/\$OUTPUT_NAME

        mkdir -p build/.facts
        # Set Kube version as image property
        echo "v\$KUBERNETES_VERSION" > build/.facts/kube_version

        # Image name will be like: NeCTAR ubuntu-2204-kube-1.29.3-20240920-10
        echo "\$IMAGE_NAME-kube-v\$KUBERNETES_VERSION-`date '+%Y%m%d'`-\$BUILD_NUMBER" > build/.facts/nectar_name

        echo "Build complete!"
    """
    stash includes: 'build/**', name: 'build'
    stash includes: 'raw_image/**', name: 'raw_image'
}
