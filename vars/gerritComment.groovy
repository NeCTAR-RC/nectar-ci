// Post a review message (no label) on the current Gerrit patchset.
//
// Needs GERRIT_HOST, GERRIT_PROJECT, GERRIT_BRANCH, GERRIT_CHANGE_ID and
// GERRIT_PATCHSET_NUMBER in the environment: the Gerrit Trigger sets them,
// and downstream jobs receive them as parameters.
def call(String message) {
    def enc = { String s -> java.net.URLEncoder.encode(s, 'UTF-8') }
    def change = "${enc(env.GERRIT_PROJECT)}~${enc(env.GERRIT_BRANCH)}~${env.GERRIT_CHANGE_ID}"
    writeFile file: '.gerrit-comment.json',
              text: groovy.json.JsonOutput.toJson([message: message])
    withEnv(["GERRIT_REVIEW_URL=https://${env.GERRIT_HOST}/a/changes/${change}/revisions/${env.GERRIT_PATCHSET_NUMBER}/review"]) {
        withCredentials([usernameColonPassword(credentialsId: '2f00f9a5-00c3-42fe-82bf-cc787af1df8c',
                                               variable: 'GERRIT_API')]) {
            sh '''#!/bin/bash -eu
            curl -sSf --user "$GERRIT_API" -X POST -H 'Content-Type: application/json' \
              -d @.gerrit-comment.json "$GERRIT_REVIEW_URL" > /dev/null
            rm -f .gerrit-comment.json
            '''
        }
    }
}
