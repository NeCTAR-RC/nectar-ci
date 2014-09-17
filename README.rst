Jenkins Job Builder Repository for NeCTAR
=========================================

Example /etc/jenkins_jobs/jenkins_jobs.ini::

   [job_builder]
   ignore_cache=True
   keep_descriptions=False
   include_path=.:scripts:~/git/
   
   [jenkins]
   user=jenkins
   password=1234567890abcdef1234567890abcdef
   url=https://jenkins.mgmt.rc.nectar.org.au


To delete a single job::

   $ jenkins-jobs delete . puppet-openvpn-puppet-unit
   INFO:root:Deleting jobs in [.]
   INFO:root:Deleting jobs in [puppet-openvpn-puppet-unit]
   INFO:jenkins_jobs.builder:Deleting jenkins job puppet-openvpn-puppet-unit


To update a single job::

   $ jenkins-jobs update . puppet-openvpn-puppet-unit
   INFO:root:Updating jobs in . (['puppet-openvpn-puppet-unit'])
   INFO:jenkins_jobs.builder:Creating jenkins job puppet-openvpn-puppet-unit

To update all jobs::

   $ jenkins-jobs update .
   INFO:root:Updating jobs in . ([])
   INFO:jenkins_jobs.builder:Creating jenkins job nectar-ci-ruby-lint
   INFO:jenkins_jobs.builder:Reconfiguring jenkins job puppet-ceilometer-puppet-unit
   INFO:jenkins_jobs.builder:Reconfiguring jenkins job puppet-cinder-puppet-unit
   INFO:jenkins_jobs.builder:Reconfiguring jenkins job puppet-elk-puppet-unit
   INFO:jenkins_jobs.builder:Reconfiguring jenkins job puppet-etckeeper-puppet-unit
   INFO:jenkins_jobs.builder:Reconfiguring jenkins job puppet-glance-puppet-unit
   INFO:jenkins_jobs.builder:Reconfiguring jenkins job puppet-haproxy-puppet-unit
   INFO:jenkins_jobs.builder:Reconfiguring jenkins job puppet-kexec-puppet-unit
   INFO:jenkins_jobs.builder:Reconfiguring jenkins job puppet-manila-puppet-unit
