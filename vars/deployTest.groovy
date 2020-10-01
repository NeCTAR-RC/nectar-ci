def call(Map pipelineParams) {
    unstash 'build'
    script {
        imageId = sh(script: 'uuidgen', returnStdout: true).trim()
        imageName = readFile(file: 'build/.facts/nectar_name').trim()
    }
    sh "echo $imageId"
    dir('build') {
        withCredentials([usernamePassword(credentialsId: '5c8f1b5c-2739-465e-ab10-e674b3fb884a', usernameVariable: 'OS_USERNAME', passwordVariable: 'OS_PASSWORD')]) {
            sh """
            #!/bin/bash
            set -x
            find .
            echo $imageId
            """
            sh "echo $imageId"
            

        }    
    }
}
