def call(Map config) {
    git credentialsId: '4946c3a5-9f5e-4eac-9ec4-90e1e348db14', url: 'ssh://jenkins@review.rc.nectar.org.au:29418/NeCTAR-RC/nectar-images.git'
    sh '''
    #!/bin/bash -xe
    echo "\033[34m========== Building ==========\033[0m"
    echo "My shell is $SHELL"
    echo "Name = ${env.BUILD_NUMBER}"
    echo "DONE"
    '''
}
