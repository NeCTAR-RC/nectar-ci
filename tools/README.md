# tools/audit_projects.py

Audit and clean up the **running Jenkins** against this repo's JJB config.

The tool renders the full JJB job set (`jenkins-jobs test -r data`) and compares
it to the jobs actually present on `jenkins.rc.nectar.org.au`:

| Category    | Meaning                                            | Action |
|-------------|----------------------------------------------------|--------|
| **orphans** | in Jenkins, not produced by JJB                    | delete from Jenkins |
| **stale**   | produced by JJB and present in Jenkins, idle > cutoff | remove from JJB + Jenkins |
| **missing** | produced by JJB, absent from Jenkins               | reported only |

Gerrit is not involved: this is purely Jenkins vs JJB.

> **Safety:** the tool is report-only by default. Every deletion needs `--delete`
> **and** an interactive confirmation. `--dry-run` forces a no-mutation run even
> with `--delete`. It never commits or pushes; JJB YAML edits are left in the
> working tree for you to review and submit with `git review`.

## Setup

Use the repo virtualenv. The tool needs `requests` and `ruamel.yaml`, plus
`jenkins-job-builder` for the render step:

```bash
python3 -m venv .venv
# jenkins-job-builder 3.11.0 (the version on jenkins-slave1) caps PyYAML<6, but
# PyYAML 5.x has no Python 3.12 wheel and the Noble package actually runs JJB
# 3.11.0 against PyYAML 6. Mirror that here:
.venv/bin/pip install --no-deps jenkins-job-builder==3.11.0
.venv/bin/pip install "PyYAML>=6" six pbr stevedore python-jenkins fasteners Jinja2 "setuptools<81"
.venv/bin/pip install -r tools/requirements.txt
# JJB 3.11.0 loads its modules through pkg_resources, which would otherwise
# reject PyYAML 6 at runtime (VersionConflict). Relax the recorded cap, exactly
# as the Noble package does:
find .venv -path '*jenkins_job_builder*/METADATA' \
  -exec sed -i 's/PyYAML (<6,>=3.10.0)/PyYAML (>=3.10.0)/' {} +
```

`setuptools<81` is required because newer setuptools removed `pkg_resources`,
which JJB 3.11.0 imports. On jenkins-slave1 none of this applies: the distro
package already runs against the system PyYAML 6.

Run with the venv active, or call `.venv/bin/python` directly — the tool finds
`jenkins-jobs` next to its own interpreter.

### Jenkins credentials

Reads `[jenkins] url/user/password` from `jenkins_jobs.ini` (the `password` may
be an API token). Search order:

1. `--jenkins-config PATH`
2. `$JENKINS_JOBS_INI`
3. `./jenkins_jobs.ini`
4. `~/.config/jenkins_jobs/jenkins_jobs.ini`
5. `/etc/jenkins_jobs/jenkins_jobs.ini`

The credentials usually live on `jenkins-slave1`; reads work from anywhere with
network access to Jenkins.

## Usage

```bash
# Read-only summary of orphans / stale / missing
tools/audit_projects.py report

# Orphans: list (with each job's last build), then delete. Allowlist excludes
# intentional jobs. With --delete you confirm each job individually
# ([y]es / [n]o / [a]ll / [q]uit).
tools/audit_projects.py orphans
tools/audit_projects.py orphans --delete

# Stale: jobs idle > 365 days (configurable). Lists Case A vs Case B.
tools/audit_projects.py stale
tools/audit_projects.py stale --max-age-days 540 --delete
```

Common flags: `--data-dir`, `--jenkins-config`, `--allowlist`, `--max-age-days`,
`--dry-run`. `stale --include-never-built` treats never-built jobs as stale
(default: never-built counts as active).

With `--delete`, both `orphans` and `stale` confirm **each** job separately
rather than deleting the whole list at once. At each prompt answer `y` (delete
this one), `n` (skip it), `a` (delete this and all remaining) or `q` (stop,
leaving the rest untouched); the default for an empty answer is `n`. For a
`stale` Case A job a `y` also removes its JJB entry (see below).

### Allowlist

`tools/audit_allowlist.txt` lists jobs that exist on Jenkins but are
intentionally **not** managed by JJB, so they are never flagged as orphans. One
fnmatch glob per line; `#` comments and blank lines ignored.

## Stale removal: Case A vs Case B

The `stale` command removes individual jobs. To remove a job from JJB it must
trace the job back to the exact `jobs:` entry that produced it:

- **Case A — safely removable.** The job is produced by exactly one project
  entry that expands to exactly one job. The tool deletes that entry (and drops
  the whole `- project:` block if its `jobs:` list becomes empty). This holds
  even when the entry goes through a shared `- job-group:` — only the
  per-project entry is removed, never the shared group definition.

- **Case B — manual.** The job comes from a shared job-group entry that expands
  to several jobs (typically an old *release row*, e.g. a retired OpenStack
  release shared by many projects), is produced by more than one entry, or has
  no resolvable local entry (pipeline/helm/image jobs). The tool will delete it
  from Jenkins if you confirm, but will **not** edit JJB automatically. The
  usual fix is to drop the old release row from the `- job-group:` by hand,
  which retires it across every project at once.

Attribution is computed by expanding each project's `jobs:` entries (following
job-groups and job-template id/name indirection) and cross-checking every
expansion against the authoritative rendered set, so the tool only ever edits
entries whose mapping it has confirmed.

## After editing JJB

```bash
pre-commit run --all-files
jenkins-jobs test -r data
git review
```

Note: puppet runs `jenkins-jobs update` (without `--delete-old`), so removing a
job from JJB does **not** remove it from Jenkins on its own — that is why the
tool also deletes the job from Jenkins directly.

## Tests

```bash
tox                 # or: .venv/bin/python -m pytest tools/tests
```

Tests cover the pure logic (name expansion, Case A/B classification, allowlist,
YAML editing, command wiring) and do not touch Jenkins or the network.
