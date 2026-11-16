// Download a Glance image into the workspace's glance/ and check its hash.
// Also writes the Glance details beside it, with the same name and .json, for
// scanDiskImage. Returns the image path.
def call(String imageId, String projectName, String cloudEnv) {
    def OSCredID
    def OSAuthURL
    script {
        switch(cloudEnv) {
          case "production":
            OSCredID = '7a2e4b77-a292-47a1-b852-c0cfd9c1c383'
            OSAuthURL = 'https://identity.rc.nectar.org.au/v3'
            break
          case "testing":
            OSCredID = '5c8f1b5c-2739-465e-ab10-e674b3fb884a'
            OSAuthURL = 'https://identity.rctest.nectar.org.au/v3'
            break
          case "development":
            OSCredID = 'bcb39a6c-5aca-4900-94aa-63fb4364d8c2'
            OSAuthURL = 'http://keystone.rcdev.nectar.org.au:5000/v3'
            break
          default:
            error "Unknown cloud environment '$cloudEnv'"
        }
    }
    writeFile file: 'bin/download-glance-image.sh', text: libraryResource('sbom/download-glance-image.sh')

    def image
    withCredentials([usernamePassword(credentialsId: OSCredID, usernameVariable: 'OS_USERNAME', passwordVariable: 'OS_PASSWORD')]) {
        withEnv(["OS_AUTH_URL=$OSAuthURL",
                 "OS_PROJECT_DOMAIN_NAME=Default",
                 "OS_USER_DOMAIN_NAME=Default",
                 "OS_IDENTITY_API_VERSION=3",
                 "OS_PROJECT_NAME=$projectName",
                 "IMAGE_ID=$imageId"]) {
            echo "\033[35;1m========== Downloading $imageId from $cloudEnv ==========\033[0m"
            // The script prints only the image path on stdout; progress goes
            // to stderr, which still reaches the console log.
            image = sh(script: 'bash bin/download-glance-image.sh "$IMAGE_ID"', returnStdout: true).trim()
        }
    }
    return image
}
