"""Unit tests for tools/find_unused.py.

These run against small inline YAML fixtures written to a temp directory; they
do not render JJB or touch the network.
"""

import os
import sys
import textwrap

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import find_unused as fu  # noqa: E402


def write_data(tmp_path, yaml_text):
    """Write one JJB YAML file into a data dir and return the dir path."""
    data_dir = tmp_path / "data"
    data_dir.mkdir()
    (data_dir / "jobs.yaml").write_text(textwrap.dedent(yaml_text))
    return str(data_dir)


def names(records):
    return sorted(r["name"] for r in records)


# --- ref_key ----------------------------------------------------------------

def test_ref_key_string():
    assert fu.ref_key("{name}-tox") == "{name}-tox"


def test_ref_key_single_key_dict():
    assert fu.ref_key({"{name}-tox": {"organisation": "x"}}) == "{name}-tox"


def test_ref_key_other():
    assert fu.ref_key(None) is None
    assert fu.ref_key(5) is None


# --- iter_builders_lists ----------------------------------------------------

def test_iter_builders_lists_finds_nested():
    body = {"builders": [{"conditional-step": {"builders": ["inner"]}},
                         "outer"]}
    found = [entry for blist in fu.iter_builders_lists(body) for entry in blist]
    assert "outer" in found
    assert "inner" in found


# --- unused templates / groups ----------------------------------------------

def test_template_used_via_project_by_name(tmp_path):
    data_dir = write_data(tmp_path, """
        - job-template:
            name: '{name}-tox'
            builders:
              - shell: echo hi
        - project:
            name: keystone
            jobs:
              - '{name}-tox'
    """)
    defs = fu.Definitions(data_dir)
    assert names(defs.unused_templates()) == []


def test_template_used_via_id_not_name(tmp_path):
    # Referenced by its id, while its rendered name differs. Must not be flagged.
    data_dir = write_data(tmp_path, """
        - job-template:
            id: '{name}-unit-job'
            name: '{name}-unit{name-suffix}'
        - project:
            name: keystone
            jobs:
              - '{name}-unit-job'
    """)
    defs = fu.Definitions(data_dir)
    assert names(defs.unused_templates()) == []


def test_unused_template_reported(tmp_path):
    data_dir = write_data(tmp_path, """
        - job-template:
            name: '{name}-tox'
        - job-template:
            name: '{name}-orphan'
        - project:
            name: keystone
            jobs:
              - '{name}-tox'
    """)
    defs = fu.Definitions(data_dir)
    assert names(defs.unused_templates()) == ["{name}-orphan"]


def test_template_reachable_through_job_group(tmp_path):
    data_dir = write_data(tmp_path, """
        - job-template:
            name: '{name}-tox'
        - job-group:
            name: '{name}-tox-group'
            jobs:
              - '{name}-tox'
        - project:
            name: keystone
            jobs:
              - '{name}-tox-group'
    """)
    defs = fu.Definitions(data_dir)
    assert names(defs.unused_templates()) == []
    assert names(defs.unused_job_groups()) == []


def test_unused_job_group_reported(tmp_path):
    data_dir = write_data(tmp_path, """
        - job-group:
            name: '{name}-orphan-group'
            jobs: []
        - project:
            name: keystone
            jobs: []
    """)
    defs = fu.Definitions(data_dir)
    assert names(defs.unused_job_groups()) == ["{name}-orphan-group"]


# --- unused builders --------------------------------------------------------

def test_builder_used_by_template(tmp_path):
    data_dir = write_data(tmp_path, """
        - builder:
            name: my-macro
            builders:
              - shell: echo hi
        - job-template:
            name: '{name}-tox'
            builders:
              - my-macro
        - project:
            name: keystone
            jobs:
              - '{name}-tox'
    """)
    defs = fu.Definitions(data_dir)
    assert names(defs.unused_builders()) == []


def test_builder_used_only_by_concrete_job(tmp_path):
    # Regression: builders referenced from a `- job:` (not a template) must
    # count as used.
    data_dir = write_data(tmp_path, """
        - builder:
            name: my-macro
            builders:
              - shell: echo hi
        - job:
            name: standalone
            builders:
              - my-macro
    """)
    defs = fu.Definitions(data_dir)
    assert names(defs.unused_builders()) == []


def test_builder_used_via_macro_composition(tmp_path):
    data_dir = write_data(tmp_path, """
        - builder:
            name: leaf-macro
            builders:
              - shell: echo hi
        - builder:
            name: parent-macro
            builders:
              - leaf-macro
        - job:
            name: standalone
            builders:
              - parent-macro
    """)
    defs = fu.Definitions(data_dir)
    assert names(defs.unused_builders()) == []


def test_unused_builder_reported(tmp_path):
    data_dir = write_data(tmp_path, """
        - builder:
            name: used-macro
            builders:
              - shell: echo hi
        - builder:
            name: orphan-macro
            builders:
              - shell: echo bye
        - job:
            name: standalone
            builders:
              - used-macro
    """)
    defs = fu.Definitions(data_dir)
    assert names(defs.unused_builders()) == ["orphan-macro"]


def test_orphan_macro_referencing_another_does_not_keep_it_alive(tmp_path):
    # An unused macro that references a second macro must not, by itself, mark
    # that second macro as used.
    data_dir = write_data(tmp_path, """
        - builder:
            name: orphan-a
            builders:
              - orphan-b
        - builder:
            name: orphan-b
            builders:
              - shell: echo hi
        - job:
            name: standalone
            builders:
              - shell: echo unrelated
    """)
    defs = fu.Definitions(data_dir)
    assert names(defs.unused_builders()) == ["orphan-a", "orphan-b"]


# --- CLI / output -----------------------------------------------------------

def test_main_exit_code(tmp_path):
    data_dir = write_data(tmp_path, """
        - job-template:
            name: '{name}-orphan'
        - project:
            name: keystone
            jobs: []
    """)
    assert fu.main(["--data-dir", data_dir]) == 0
    assert fu.main(["--data-dir", data_dir, "--exit-code"]) == 1
    assert fu.main(["--data-dir", data_dir, "--exit-code",
                    "--ignore", "{name}-orphan"]) == 0


# --- --fix removal ----------------------------------------------------------

THREE_BUILDERS = (
    "- builder:\n"
    "    name: keep-one\n"
    "    builders:\n"
    "      - shell: echo one\n"
    "- builder:\n"
    "    name: drop-me\n"
    "    builders:\n"
    "      - shell: echo drop\n"
    "- builder:\n"
    "    name: keep-two\n"
    "    builders:\n"
    "      - shell: echo two\n"
)


def _write_raw(tmp_path, text):
    data_dir = tmp_path / "data"
    data_dir.mkdir()
    path = data_dir / "b.yaml"
    path.write_text(text)
    return str(data_dir), path


def test_remove_middle_block_is_surgical(tmp_path):
    data_dir, path = _write_raw(tmp_path, THREE_BUILDERS)
    defs = fu.Definitions(data_dir)
    fu.remove_definitions([b for b in defs.builders if b["name"] == "drop-me"],
                          data_dir)
    assert path.read_text() == (
        "- builder:\n"
        "    name: keep-one\n"
        "    builders:\n"
        "      - shell: echo one\n"
        "- builder:\n"
        "    name: keep-two\n"
        "    builders:\n"
        "      - shell: echo two\n"
    )


def test_remove_last_block(tmp_path):
    data_dir, path = _write_raw(tmp_path, THREE_BUILDERS)
    defs = fu.Definitions(data_dir)
    fu.remove_definitions([b for b in defs.builders if b["name"] == "keep-two"],
                          data_dir)
    assert path.read_text() == (
        "- builder:\n"
        "    name: keep-one\n"
        "    builders:\n"
        "      - shell: echo one\n"
        "- builder:\n"
        "    name: drop-me\n"
        "    builders:\n"
        "      - shell: echo drop\n"
    )


def test_remove_multiple_blocks_one_file(tmp_path):
    data_dir, path = _write_raw(tmp_path, THREE_BUILDERS)
    defs = fu.Definitions(data_dir)
    fu.remove_definitions([b for b in defs.builders
                           if b["name"] in ("keep-one", "keep-two")], data_dir)
    assert path.read_text() == (
        "- builder:\n"
        "    name: drop-me\n"
        "    builders:\n"
        "      - shell: echo drop\n"
    )


def test_remove_leaves_leading_comment(tmp_path):
    # A column-0 comment above a block is intentionally left in place.
    data_dir, path = _write_raw(tmp_path,
                                "# header for drop-me\n"
                                "- builder:\n"
                                "    name: drop-me\n"
                                "    builders:\n"
                                "      - shell: echo drop\n"
                                "- builder:\n"
                                "    name: keep-two\n"
                                "    builders:\n"
                                "      - shell: echo two\n")
    defs = fu.Definitions(data_dir)
    fu.remove_definitions([b for b in defs.builders if b["name"] == "drop-me"],
                          data_dir)
    assert path.read_text() == (
        "# header for drop-me\n"
        "- builder:\n"
        "    name: keep-two\n"
        "    builders:\n"
        "      - shell: echo two\n"
    )


def test_resolve_script_under_scripts_dir(tmp_path):
    scripts_dir = tmp_path / "scripts"
    scripts_dir.mkdir()
    assert fu._resolve_script("scripts/foo.sh", str(scripts_dir)) == \
        str(scripts_dir / "foo.sh")
    # a bare name is found via the scripts/ include entry too
    assert fu._resolve_script("foo.sh", str(scripts_dir)) == \
        str(scripts_dir / "foo.sh")


def test_resolve_script_rejects_paths_outside_scripts(tmp_path):
    scripts_dir = tmp_path / "scripts"
    scripts_dir.mkdir()
    assert fu._resolve_script("../data/publisher.yaml.inc",
                              str(scripts_dir)) is None
    assert fu._resolve_script("../secret.txt", str(scripts_dir)) is None


def _scripts_case(tmp_path, jobs_yaml):
    data_dir = tmp_path / "data"
    data_dir.mkdir()
    scripts_dir = tmp_path / "scripts"
    scripts_dir.mkdir()
    (scripts_dir / "orphan.sh").write_text("#!/bin/bash\necho orphan\n")
    (scripts_dir / "shared.sh").write_text("#!/bin/bash\necho shared\n")
    (data_dir / "jobs.yaml").write_text(textwrap.dedent(jobs_yaml))
    return str(data_dir), scripts_dir


SCRIPTS_YAML = """
    - builder:
        name: uses-orphan
        builders:
          - shell: !include-raw-escape: scripts/orphan.sh
    - builder:
        name: uses-shared-a
        builders:
          - shell: !include-raw-escape: scripts/shared.sh
    - builder:
        name: uses-shared-b
        builders:
          - shell: !include-raw-escape: scripts/shared.sh
"""


def test_fix_deletes_orphaned_script(tmp_path):
    data_dir, scripts_dir = _scripts_case(tmp_path, SCRIPTS_YAML)
    defs = fu.Definitions(data_dir)
    _, orphaned = fu.remove_definitions(
        [b for b in defs.builders if b["name"] == "uses-orphan"], data_dir)
    assert orphaned == [str(scripts_dir / "orphan.sh")]
    assert not (scripts_dir / "orphan.sh").exists()
    assert (scripts_dir / "shared.sh").exists()


def test_fix_keeps_script_still_referenced(tmp_path):
    # shared.sh is referenced by two macros; removing one must not delete it.
    data_dir, scripts_dir = _scripts_case(tmp_path, SCRIPTS_YAML)
    defs = fu.Definitions(data_dir)
    _, orphaned = fu.remove_definitions(
        [b for b in defs.builders if b["name"] == "uses-shared-a"], data_dir)
    assert orphaned == []
    assert (scripts_dir / "shared.sh").exists()


def test_fix_keep_scripts_flag_preserves_file(tmp_path):
    data_dir, scripts_dir = _scripts_case(tmp_path, SCRIPTS_YAML)
    defs = fu.Definitions(data_dir)
    _, orphaned = fu.remove_definitions(
        [b for b in defs.builders if b["name"] == "uses-orphan"], data_dir,
        keep_scripts=True)
    assert orphaned == [str(scripts_dir / "orphan.sh")]
    assert (scripts_dir / "orphan.sh").exists()  # reported but not deleted


def test_fix_never_deletes_data_dir_include(tmp_path):
    # `!include: publisher.yaml.inc` lives in data/, not scripts/: never delete.
    data_dir = tmp_path / "data"
    data_dir.mkdir()
    (tmp_path / "scripts").mkdir()
    (data_dir / "publisher.yaml.inc").write_text("- archive: {}\n")
    (data_dir / "jobs.yaml").write_text(textwrap.dedent("""
        - job:
            name: standalone
            publishers: !include: publisher.yaml.inc
    """))
    defs = fu.Definitions(data_dir)
    job = next(b for b in defs.blocks if b["kind"] == "job")
    rec = {"name": "standalone", "file": os.path.join(str(data_dir),
                                                       "jobs.yaml"), "line": 2}
    _, orphaned = fu.remove_definitions([rec], str(data_dir))
    assert orphaned == []
    assert (data_dir / "publisher.yaml.inc").exists()
    assert job  # sanity: the block was parsed


def test_main_fix_end_to_end(tmp_path):
    data_dir = write_data(tmp_path, """
        - job-template:
            name: '{name}-orphan'
            builders:
              - shell: echo hi
        - job-template:
            name: '{name}-tox'
        - project:
            name: keystone
            jobs:
              - '{name}-tox'
    """)
    assert fu.main(["--data-dir", data_dir, "--fix"]) == 0
    defs = fu.Definitions(data_dir)
    assert [t["name"] for t in defs.templates] == ["{name}-tox"]
    assert names(defs.unused_templates()) == []
