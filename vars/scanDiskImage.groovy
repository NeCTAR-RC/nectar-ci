// Generate an SPDX SBOM for a disk image in the workspace with Trivy.
// The Glance details downloadGlanceImage wrote beside the image (the same
// name with .json) name the SBOM by Glance's hash and are added to the
// .meta.json. Returns the path of sboms/glance/<algo>/<hash>.spdx.json.
def call(String image) {
    def glanceJson = image.take(image.lastIndexOf('.')) + '.json'
    writeFile file: 'bin/scan-disk-image.sh', text: libraryResource('sbom/scan-disk-image.sh')
    def sbom
    withEnv(["IMAGE=$image", "GLANCE_JSON=$glanceJson", "TRIVY_TIMEOUT=${params.TRIVY_TIMEOUT ?: ''}"]) {
        echo "\033[35;1m========== Scanning $image ==========\033[0m"
        // The script prints only the SBOM path on stdout.
        sbom = sh(script: 'bash bin/scan-disk-image.sh "$IMAGE" "$GLANCE_JSON"', returnStdout: true).trim()
    }
    return sbom
}
