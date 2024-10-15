Jenkins Job Builder Repository for NeCTAR
=========================================

Installation
------------
We no longer require any extra packages to be installed, or any specific
version of JJB, tested up to v1.6.3

All you need to do is:
`pip install jenkins-job-builder`

Developing
==========
We use pre-commit for this repo for linting and formatting.
To set this up::

 pip install pre-commit
 pre-commit install


Configuration
-------------
Example /etc/jenkins_jobs/jenkins_jobs.ini::

   [job_builder]
   ignore_cache=True
   keep_descriptions=False
   include_path=.:scripts:~/git/

   [jenkins]
   user=jenkins
   password=1234567890abcdef1234567890abcdef
   url=https://jenkins.rc.nectar.org.au


Usage
-----
To delete a single job::

   $ jenkins-jobs delete -r data puppet-openvpn-puppet-unit
   INFO:root:Deleting jobs in [.]
   INFO:root:Deleting jobs in [puppet-openvpn-puppet-unit]
   INFO:jenkins_jobs.builder:Deleting jenkins job puppet-openvpn-puppet-unit


To update a single job::

   $ jenkins-jobs update -r data puppet-openvpn-puppet-unit
   INFO:root:Updating jobs in . (['puppet-openvpn-puppet-unit'])
   INFO:jenkins_jobs.builder:Creating jenkins job puppet-openvpn-puppet-unit

To update all jobs::

   $ jenkins-jobs update -r data.
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
