def call(String profile, String config_file) {
    sh """#!/bin/bash -eu
    export REGISTRY_AUTH_FILE=auth.json
    echo "\$REGISTRY_PSW" | docker login -u "\$REGISTRY_USR" --password-stdin "\$REGISTRY_HOST"
    OPENSTACK_RELEASE=`echo \$GERRIT_BRANCH | awk -F '/' '{ print \$2 }'`
    virtualenv kolla
    . kolla/bin/activate
    pip install -r requirements.txt
    docker image prune --all -f

    docker pull \${REGISTRY_HOST}/kolla/ubuntu-source-base:\${OPENSTACK_RELEASE}
    docker pull \${REGISTRY_HOST}/kolla/ubuntu-source-openstack-base:\${OPENSTACK_RELEASE}
    kolla-build --config-file $config_file --profile $profile --skip-existing --tag \$OPENSTACK_RELEASE --registry \$REGISTRY_HOST
    """

}
