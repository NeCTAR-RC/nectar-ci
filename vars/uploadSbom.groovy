// Upload an SBOM and its .meta.json to the sboms Swift container of
// NeCTAR-Images in the given cloud, as <algo>/<hash>.spdx.json and
// <algo>/<hash>.meta.json.
def call(String sbom, String cloudEnv) {
    def OSCredID
    def OSAuthURL
    def sbomProject = 'NeCTAR-Images'
    def container = 'sboms'
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
    withCredentials([usernamePassword(credentialsId: OSCredID, usernameVariable: 'OS_USERNAME', passwordVariable: 'OS_PASSWORD')]) {
        withEnv(["OS_AUTH_URL=$OSAuthURL",
                 "OS_PROJECT_DOMAIN_NAME=Default",
                 "OS_USER_DOMAIN_NAME=Default",
                 "OS_IDENTITY_API_VERSION=3",
                 "OS_PROJECT_NAME=$sbomProject",
                 "SBOM=$sbom",
                 "CONTAINER=$container"]) {
            echo "\033[35;1m========== Uploading to $container in $sbomProject ($cloudEnv) ==========\033[0m"
            sh libraryResource('sbom/upload-to-swift.sh')
        }
    }
}
