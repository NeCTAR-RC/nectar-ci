def call(String profile, String tag, String config_file = "etc/kolla-build.conf") {
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
    echo "Running: kolla-build --config-file $config_file --profile $profile --skip-existing --tag \$OPENSTACK_RELEASE --registry \$REGISTRY_HOST"
    kolla-build --config-file $config_file --profile $profile --skip-existing --tag \$OPENSTACK_RELEASE --registry \$REGISTRY_HOST

    for image in `kolla-build --list-images --profile $profile --config-file $config_file | awk '{print \$3}' | egrep -v '^(base|openstack-base)\$'`
    do
      docker tag \${REGISTRY_HOST}/kolla/ubuntu-source-\${image}:\${OPENSTACK_RELEASE} \${REGISTRY_HOST}/kolla/ubuntu-source-\${image}:$tag
      docker push \${REGISTRY_HOST}/kolla/ubuntu-source-\${image}:$tag
    done
    """

}
