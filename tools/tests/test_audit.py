"""Unit tests for the pure logic in tools/audit_projects.py.

These tests do not touch Jenkins or the network; the JJB attribution tests run
against small inline YAML fixtures written to a temp directory.
"""

import os
import sys
from datetime import datetime, timedelta, timezone

import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import audit_projects as ap  # noqa: E402


# --- _subst -----------------------------------------------------------------

def test_subst_fills_known_placeholders():
    assert ap._subst("{name}-noble-tox-{environment}",
                     {"name": "keystone", "environment": "py312"}) \
        == "keystone-noble-tox-py312"


def test_subst_leaves_unknown_placeholders():
    assert ap._subst("{name}-{ubuntu_release}-tox-{environment}",
                     {"name": "keystone", "environment": "py312"}) \
        == "keystone-{ubuntu_release}-tox-py312"


# --- _entry_key_params ------------------------------------------------------

def test_entry_key_params_string():
    assert ap._entry_key_params("{name}-ruby-lint") == ("{name}-ruby-lint", {})


def test_entry_key_params_dict_with_params():
    key, params = ap._entry_key_params(
        {"{name}-os-tox-{environment}": {"organisation": "NeCTAR-RC",
                                         "environment": "py312"}})
    assert key == "{name}-os-tox-{environment}"
    assert params == {"organisation": "NeCTAR-RC", "environment": "py312"}


def test_entry_key_params_dict_null_value():
    assert ap._entry_key_params({"{name}-ruby-lint": None}) \
        == ("{name}-ruby-lint", {})


# --- allowlist --------------------------------------------------------------

def test_is_allowlisted_glob():
    globs = ["experimental-*", "exact-job"]
    assert ap.is_allowlisted("experimental-foo", globs)
    assert ap.is_allowlisted("exact-job", globs)
    assert not ap.is_allowlisted("normal-job", globs)


def test_load_allowlist_ignores_comments(tmp_path):
    f = tmp_path / "allow.txt"
    f.write_text("# comment\n\nfoo-*  # trailing\nbar\n")
    assert ap.load_allowlist(str(f)) == ["foo-*", "bar"]


# --- fmt_age ----------------------------------------------------------------

def test_fmt_age_never():
    now = datetime(2026, 6, 15, tzinfo=timezone.utc)
    assert ap.fmt_age(None, now) == "never built"


def test_fmt_age_days():
    now = datetime(2026, 6, 15, tzinfo=timezone.utc)
    dt = now - timedelta(days=400)
    assert "400d ago" in ap.fmt_age(dt, now)


# --- JjbModel attribution (Case A vs Case B) --------------------------------

FIXTURE = """\
- job-group:
    name: '{name}-os-tox-{environment}'
    jobs:
      - '{name}-{ubuntu_release}-{os_release}-tox-{environment}':
          ubuntu_release: 'jammy'
          os_release: 'zed'
      - '{name}-{ubuntu_release}-{os_release}-tox-{environment}':
          ubuntu_release: 'noble'
          os_release: '2025.1'
- project:
    name: aardvark
    jobs:
      - '{name}-os-tox-{environment}':
          organisation: 'NeCTAR-RC'
          environment: 'py312'
      - '{name}-container':
          organisation: 'NeCTAR-RC'
- project:
    name: solo
    jobs:
      - '{name}-noble-tox-{environment}':
          organisation: 'NeCTAR-RC'
          environment: 'py312'
"""


@pytest.fixture()
def model(tmp_path):
    (tmp_path / "fix.yaml").write_text(FIXTURE)
    return ap.JjbModel(str(tmp_path))


def _rendered():
    # The job names JJB would actually produce from the fixture above.
    return {
        "aardvark-jammy-zed-tox-py312",
        "aardvark-noble-2025.1-tox-py312",
        "aardvark-container",
        "solo-noble-tox-py312",
    }


def test_group_entry_is_case_b(model):
    attr = model.build_attribution(_rendered())
    kind, _ = model.classify("aardvark-jammy-zed-tox-py312", attr)
    assert kind == "B"


def test_direct_entry_is_case_a(model):
    attr = model.build_attribution(_rendered())
    kind, info = model.classify("aardvark-container", attr)
    assert kind == "A"
    assert info["project"] == "aardvark"
    assert info["expands_to"] == 1


def test_solo_template_with_param_is_case_a(model):
    attr = model.build_attribution(_rendered())
    kind, info = model.classify("solo-noble-tox-py312", attr)
    assert kind == "A"
    assert info["project"] == "solo"


def test_unknown_job_is_case_b(model):
    attr = model.build_attribution(_rendered())
    kind, _ = model.classify("not-a-real-job", attr)
    assert kind == "B"


# --- YAML editing (Case A removal) ------------------------------------------

def test_remove_entry_drops_single_job(model):
    attr = model.build_attribution(_rendered())
    _, info = model.classify("aardvark-container", attr)
    model.remove_entry(info)
    reloaded = ap.JjbModel(model.data_dir)
    aardvark = [p for p in reloaded.projects if p["name"] == "aardvark"][0]
    keys = [ap._entry_key_params(e)[0] for e in aardvark["jobs"]]
    assert "{name}-container" not in keys
    assert "{name}-os-tox-{environment}" in keys


def test_remove_entry_drops_empty_project(model):
    attr = model.build_attribution(_rendered())
    _, info = model.classify("solo-noble-tox-py312", attr)
    model.remove_entry(info)
    reloaded = ap.JjbModel(model.data_dir)
    assert not any(p["name"] == "solo" for p in reloaded.projects)


MULTI_FIXTURE = """\
- project:
    name: multi
    jobs:
      - '{name}-noble-tox-{environment}':
          environment: 'py312'
      - '{name}-noble-tox-{environment}':
          environment: 'pep8'
      - '{name}-container':
"""


def test_remove_two_entries_same_project_uses_descending_indices(tmp_path):
    (tmp_path / "m.yaml").write_text(MULTI_FIXTURE)
    rendered = {"multi-noble-tox-py312", "multi-noble-tox-pep8",
                "multi-container"}
    model = ap.JjbModel(str(tmp_path))
    attr = model.build_attribution(rendered)
    # Remove index 0 and index 2 (descending) — the self-check must pass and
    # the surviving entry must be the pep8 one at original index 1.
    targets = [model.classify("multi-noble-tox-py312", attr)[1],
               model.classify("multi-container", attr)[1]]
    for info in sorted(targets, key=lambda a: a["entry_index"], reverse=True):
        model.remove_entry(info)
    reloaded = ap.JjbModel(str(tmp_path))
    proj = [p for p in reloaded.projects if p["name"] == "multi"][0]
    keys = [ap._entry_key_params(e)[0] for e in proj["jobs"]]
    assert keys == ["{name}-noble-tox-{environment}"]
    assert ap._entry_key_params(proj["jobs"][0])[1] == {"environment": "pep8"}


def test_remove_entry_self_check_rejects_stale_index(tmp_path):
    (tmp_path / "m.yaml").write_text(MULTI_FIXTURE)
    rendered = {"multi-noble-tox-py312", "multi-noble-tox-pep8",
                "multi-container"}
    model = ap.JjbModel(str(tmp_path))
    attr = model.build_attribution(rendered)
    info = model.classify("multi-container", attr)[1]  # entry_index 2
    info = dict(info)
    info["entry_index"] = 0  # wrong index on purpose
    with pytest.raises(RuntimeError):
        model.remove_entry(info)
