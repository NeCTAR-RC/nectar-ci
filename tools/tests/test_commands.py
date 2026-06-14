"""Wiring tests for the report/orphans/stale subcommands.

Jenkins is mocked (no network). gather() is replaced with canned data so the
filtering, classification and dry-run/confirm gating can be exercised. The JJB
attribution still runs for real against the small fixture in test_audit.
"""

import os
import sys
from datetime import datetime, timedelta, timezone

import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import audit_projects as ap  # noqa: E402
from test_audit import FIXTURE  # noqa: E402


class FakeClient:
    def __init__(self):
        self.deleted = []

    def delete_job(self, url):
        self.deleted.append(url)
        return True


class FakeInput:
    """Feeds canned replies to prompt_each, one per prompt."""

    def __init__(self, *replies):
        self.replies = list(replies)

    def __call__(self, prompt=""):
        return self.replies.pop(0) if self.replies else ""


def make_data(client, now):
    old = now - timedelta(days=500)
    recent = now - timedelta(days=10)
    jenkins = {
        # managed + stale, Case A in the fixture (single expansion)
        "aardvark-container": {"name": "aardvark-container",
                               "url": "http://j/job/aardvark-container/",
                               "last_build": old},
        # managed + stale, Case B in the fixture (group expands to 2)
        "aardvark-jammy-zed-tox-py312": {
            "name": "aardvark-jammy-zed-tox-py312",
            "url": "http://j/job/aardvark-jammy-zed-tox-py312/",
            "last_build": old},
        # managed + recent (not stale)
        "solo-noble-tox-py312": {"name": "solo-noble-tox-py312",
                                 "url": "http://j/job/solo-noble-tox-py312/",
                                 "last_build": recent},
        # orphan (not in JJB)
        "legacy-orphan": {"name": "legacy-orphan",
                          "url": "http://j/job/legacy-orphan/",
                          "last_build": old},
        # orphan but allowlisted
        "experimental-thing": {"name": "experimental-thing",
                               "url": "http://j/job/experimental-thing/",
                               "last_build": old},
    }
    jjb_names = {"aardvark-container", "aardvark-jammy-zed-tox-py312",
                 "aardvark-noble-2025.1-tox-py312", "solo-noble-tox-py312"}
    jenkins_names = set(jenkins)
    return {
        "client": client,
        "jjb": {n: None for n in jjb_names},
        "jjb_names": jjb_names,
        "jenkins_by_name": jenkins,
        "jenkins_names": jenkins_names,
        "orphans": sorted(jenkins_names - jjb_names),
        "missing": sorted(jjb_names - jenkins_names),
        "managed": sorted(jjb_names & jenkins_names),
    }


@pytest.fixture()
def env(tmp_path, monkeypatch):
    data_dir = tmp_path / "data"
    data_dir.mkdir()
    (data_dir / "fix.yaml").write_text(FIXTURE)
    allow = tmp_path / "allow.txt"
    allow.write_text("experimental-*\n")

    now = datetime(2026, 6, 15, tzinfo=timezone.utc)
    client = FakeClient()
    monkeypatch.setattr(ap, "gather", lambda args: make_data(client, now))

    fixed_now = now

    class FixedDatetime(datetime):
        @classmethod
        def now(cls, tz=None):
            return fixed_now
    monkeypatch.setattr(ap, "datetime", FixedDatetime)
    return data_dir, allow, client


def run(argv):
    return ap.main(argv)


def test_report_runs(env, capsys):
    data_dir, allow, _ = env
    rc = run(["--data-dir", str(data_dir), "--allowlist", str(allow), "report"])
    out = capsys.readouterr().out
    assert rc == 0
    assert "Orphans (in Jenkins, not in JJB): 1" in out  # experimental allowlisted
    assert "legacy-orphan" in out
    assert "experimental-thing" not in out
    # Both stale managed jobs reported
    assert "aardvark-container" in out
    assert "aardvark-jammy-zed-tox-py312" in out


def test_orphans_dry_run_no_delete(env, capsys):
    data_dir, allow, client = env
    rc = run(["--data-dir", str(data_dir), "--allowlist", str(allow),
              "--dry-run", "orphans", "--delete"])
    assert rc == 0
    assert client.deleted == []  # dry-run never deletes


def test_orphans_per_job_delete_yes(env, monkeypatch):
    data_dir, allow, client = env
    monkeypatch.setattr(ap, "input", FakeInput("y"), raising=False)
    rc = run(["--data-dir", str(data_dir), "--allowlist", str(allow),
              "orphans", "--delete"])
    assert rc == 0
    assert client.deleted == ["http://j/job/legacy-orphan/"]


def test_orphans_per_job_decline(env, monkeypatch, capsys):
    data_dir, allow, client = env
    monkeypatch.setattr(ap, "input", FakeInput("n"), raising=False)
    rc = run(["--data-dir", str(data_dir), "--allowlist", str(allow),
              "orphans", "--delete"])
    out = capsys.readouterr().out
    assert rc == 0
    assert client.deleted == []  # declined per job
    assert "No jobs selected" in out


def test_orphans_excludes_allowlist(env, capsys):
    data_dir, allow, _ = env
    run(["--data-dir", str(data_dir), "--allowlist", str(allow), "orphans"])
    out = capsys.readouterr().out
    assert "legacy-orphan" in out
    assert "experimental-thing" not in out
    assert "1 allowlisted job(s) skipped" in out


def test_stale_classifies_case_a_and_b(env, capsys):
    data_dir, allow, _ = env
    rc = run(["--data-dir", str(data_dir), "--allowlist", str(allow), "stale"])
    out = capsys.readouterr().out
    assert rc == 0
    # aardvark-container -> Case A; aardvark-jammy-zed-tox-py312 -> Case B
    a_idx = out.index("Case A")
    b_idx = out.index("Case B")
    assert "aardvark-container" in out[a_idx:b_idx]
    assert "aardvark-jammy-zed-tox-py312" in out[b_idx:]
    # recent job must not be flagged
    assert "solo-noble-tox-py312" not in out


def test_stale_dry_run_no_mutation(env, capsys):
    data_dir, allow, client = env
    before = (data_dir / "fix.yaml").read_text()
    rc = run(["--data-dir", str(data_dir), "--allowlist", str(allow),
              "--dry-run", "stale", "--delete"])
    assert rc == 0
    assert client.deleted == []
    assert (data_dir / "fix.yaml").read_text() == before  # YAML untouched


def test_stale_per_job_case_a_yes_edits_yaml_and_deletes(env, monkeypatch):
    data_dir, allow, client = env
    # Prompt order is Case A (aardvark-container) then Case B
    # (aardvark-jammy-zed-tox-py312): retire only Case A.
    monkeypatch.setattr(ap, "input", FakeInput("y", "n"), raising=False)
    rc = run(["--data-dir", str(data_dir), "--allowlist", str(allow),
              "stale", "--delete"])
    assert rc == 0
    assert client.deleted == ["http://j/job/aardvark-container/"]
    # Case A removal also drops its JJB entry from the YAML.
    assert "{name}-container" not in (data_dir / "fix.yaml").read_text()


def test_stale_per_job_case_b_only_keeps_yaml(env, monkeypatch, capsys):
    data_dir, allow, client = env
    before = (data_dir / "fix.yaml").read_text()
    # Skip Case A, retire only the Case B job.
    monkeypatch.setattr(ap, "input", FakeInput("n", "y"), raising=False)
    rc = run(["--data-dir", str(data_dir), "--allowlist", str(allow),
              "stale", "--delete"])
    out = capsys.readouterr().out
    assert rc == 0
    assert client.deleted == ["http://j/job/aardvark-jammy-zed-tox-py312/"]
    # Case B is deleted from Jenkins but the YAML is untouched.
    assert (data_dir / "fix.yaml").read_text() == before
    assert "NOT removed from JJB" in out


def test_stale_per_job_quit_does_nothing(env, monkeypatch, capsys):
    data_dir, allow, client = env
    before = (data_dir / "fix.yaml").read_text()
    monkeypatch.setattr(ap, "input", FakeInput("q"), raising=False)
    rc = run(["--data-dir", str(data_dir), "--allowlist", str(allow),
              "stale", "--delete"])
    out = capsys.readouterr().out
    assert rc == 0
    assert client.deleted == []
    assert (data_dir / "fix.yaml").read_text() == before
    assert "Nothing selected" in out
