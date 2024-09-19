def call(String imageName, String kubernetesVersion) {
    git branch: 'main', url: 'https://github.com/kubernetes-sigs/image-builder'
    sh """#!/bin/bash -eu
        echo "\033[34m========== Building ==========\033[0m"

        IMAGE_NAME=$imageName
        KUBERNETES_VERSION=$kubernetesVersion

        FILE_NAME="\${IMAGE_NAME}-kube-\${KUBERNETES_VERSION}"
        OUTPUT_DIR="\$WORKSPACE/images/capi/output/\$FILE_NAME/"

        PACKER_FLAGS="
        --var 'kubernetes_rpm_version=\${KUBERNETES_RPM_VERSION:-\$KUBERNETES_VERSION}' \
        --var 'kubernetes_semver=v\${KUBERNETES_SEMVER:-\$KUBERNETES_VERSION}' \
        --var 'kubernetes_series=v\${KUBERNETES_SERIES:-\${KUBERNETES_VERSION%.*}}' \
        --var 'kubernetes_deb_version=\${KUBERNETES_DEB_VERSION:-\${KUBERNETES_VERSION}-1.1}' \
        --var vnc_bind_address=0.0.0.0"

        echo "Starting build..."
        cd \$WORKSPACE/images/capi
        make build-qemu-\${IMAGE_NAME}
        cd \$WORKSPACE

        # Clean up any left over build
        rm -vfr build
        # Move image build files to build directory
        mv \$OUTPUT_DIR build
        mkdir -p raw_image

        # Set the kube version fact here to become an image property
        mkdir -p build/.facts
        echo "v\$KUBERNETES_VERSION" >> build/.facts/kube_version

        echo "Compressing QCOW2 image..."
        qemu-img convert -c -O qcow2 "build/\$FILE_NAME" raw_image/image.qcow2
    """
    stash includes: 'build/**', name: 'build'
    stash includes: 'raw_image/**', name: 'raw_image'
}
