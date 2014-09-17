

Example /etc/jenkins_jobs/jenkins_jobs.ini::

   [job_builder]
   ignore_cache=True
   keep_descriptions=False
   include_path=.:scripts:~/git/
   
   [jenkins]
   user=jenkins
   password=1234567890abcdef1234567890abcdef
   url=https://jenkins.mgmt.rc.nectar.org.au
