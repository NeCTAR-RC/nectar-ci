def call(Map pipelineParams) {
    unstash 'build'
    dir('build') {
        withCredentials([usernamePassword(credentialsId: '5c8f1b5c-2739-465e-ab10-e674b3fb884a', usernameVariable: 'OS_USERNAME', passwordVariable: 'OS_PASSWORD')]) {
            sh '''
            #!/bin/bash
            set -x
            ls -l
            echo "${imageId}"
            '''

        }    
    }
}
