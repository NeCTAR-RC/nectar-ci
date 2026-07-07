Jenkins Job Builder Repository for NeCTAR
=========================================

Installation
------------

All you need to do is:
`pip install -U -r requirements.txt`

This pins `jenkins-job-builder==6.5.0`.

NOTE: jenkins-slave1 must run the same version (6.5.0). The Noble distro package
is older (3.11.0-6), so install 6.5.0 with pip there too. The job XML 6.5.0
renders differs from 3.11.0 (for example the git SCM credentials format), so the
server and local dev must stay on matching versions.

Developing
==========
We use pre-commit for this repo for linting and formatting.
To set this up::

 pip install pre-commit
 pre-commit install

To run all configured checks manually::

 pre-commit run --all-files


Shell scripts in builder macros
-------------------------------
Following the `Linux Foundation global-jjb best practices
<https://docs.releng.linuxfoundation.org/projects/global-jjb/en/latest/best-practices.html>`_,
multi-line shell for a ``builder`` macro lives in its own file under
``scripts/`` rather than inlined in ``data/builder-macros.yaml``. The macro
references it with JJB's include directive::

   - builder:
       name: puppet-lint
       builders:
         - shell: !include-raw-escape: scripts/puppet-lint.sh

Why ``!include-raw-escape:`` (not ``!include-raw:``)? The scripts contain shell
``${VAR}`` and ``awk '{...}'`` braces; ``-escape`` doubles every ``{``/``}`` so
JJB's templating leaves them intact. This renders byte-identical job XML to the
old inline ``{{ }}``-escaped form, so a pure extraction is verifiable with::

   jenkins-jobs test -r data -o /tmp/after
   diff -r /tmp/before /tmp/after   # empty == XML-neutral

Conventions for these scripts:

* ``scripts/`` is already on ``include_path`` in ``jenkins_jobs.ini``.
* Start each with ``#!/bin/bash`` and a ``set`` line that preserves the
  behaviour of Jenkins' implicit ``sh -xe`` — use ``set -ex``, or
  ``set -euo pipefail`` where a script already declared it. Do not add
  ``-u``/``pipefail`` to a retrofitted script without checking it still passes
  (for example a ``grep ... | while read`` would start failing under
  ``pipefail`` when grep matches nothing).
* Pass values as environment variables, or derive them from Jenkins env
  (``$GERRIT_PROJECT``, ``$WORKSPACE``), instead of JJB ``{params}``. This keeps
  the script self-contained and lets ShellCheck analyse it.
* Trivial one-liners may stay inline in the macro.

The ShellCheck pre-commit hook lints every ``scripts/*.sh`` automatically.


Shell scripts in pipeline DSLs
------------------------------
The ``scripts/`` + ``!include-raw-escape:`` mechanism above only works for
freestyle ``builder`` macros — JJB cannot expand an include *inside* a pipeline
``dsl:`` block scalar (it renders as literal text). To keep multi-line shell out
of a pipeline ``dsl`` instead, put it under ``resources/`` and load it at
runtime with Jenkins' ``libraryResource`` (this repo is configured as an
implicit global shared library, which is also why ``vars/*.groovy`` steps like
``kollaBuild`` resolve without ``@Library``)::

    dsl: |
      pipeline {{
        agent {{ label 'docker' }}
        stages {{
          stage('Build') {{
            environment {{
              DATASTORE = '{datastore}'
              REGISTRY = credentials('registry-nectar')
            }}
            steps {{
              sh libraryResource('trove-backup-build.sh')
            }}
          }}
        }}
      }}

The ``dsl`` stays inline (so JJB ``{param}`` templating and ``environment {{
credentials() }}`` auth keep working), while the shell body lives in a linted,
reusable file. Conventions match ``scripts/`` above: start with ``#!/bin/bash``
and a ``set`` line, and pass inputs as environment variables (set them in the
stage ``environment`` block) rather than JJB ``{params}`` so ShellCheck can
analyse the script standalone. The ShellCheck pre-commit hook lints
``resources/*.sh`` too.


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
To test job definitions locally::

   $ jenkins-jobs test -r data
   INFO:jenkins_jobs.cli.subcommand.update:Updating jobs in ['data', 'data/projects'] ([])


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

   $ jenkins-jobs update -r data
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
