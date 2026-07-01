#!/usr/bin/env python3
"""Find unused JJB definitions in the data/ directory.

Reports reusable JJB definitions that nothing references, so they can be
removed (or a missing `- project:` binding spotted):

  * job-templates   not reachable from any `- project:` entry
  * job-groups      not reachable from any `- project:` entry
  * builder macros  not referenced by any `builders:` list

How references are resolved
---------------------------
JJB keeps `{placeholder}` tokens literal in both a definition's `name`/`id`
and in the strings that reference it, so a reference is matched to its
definition by plain string equality (e.g. a project entry
``'{name}-nodejs-lint'`` matches the job-template ``name: '{name}-nodejs-lint'``,
and the tox job-group's ``'{name}-{ubuntu_release}-{os_release}-tox-{environment}'``
matches the template of the same literal name).

Reachability starts at every `- project:` `jobs:` entry and follows job-groups
(which may nest) down to job-templates. A job-template is "used" if its `name`
OR its `id` is reached. Builder macros are "used" if referenced from any
job-template's `builders:` list (following macro-to-macro composition); this is
deliberately independent of template reachability, so a builder is only ever
reported unused when NO template mentions it at all.

This tool only reads the YAML in data/. It never renders, contacts Jenkins or
edits anything; the output is advisory. See tools/README.md.
"""

import argparse
import fnmatch
import glob
import io
import json
import os
import re
import sys

from ruamel.yaml import YAML

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_DATA_DIR = os.path.join(REPO_ROOT, "data")

# Matches JJB include tags (`!include:`, `!include-raw:`, `!include-raw-escape:`)
# and captures the single path that follows, e.g.
#   - shell: !include-raw-escape: scripts/rake-test.sh
INCLUDE_RE = re.compile(r"!include(?:-raw)?(?:-escape)?:\s*(\S+)")


def ref_key(entry):
    """Return the referenced name from a `jobs:`/`builders:` list entry.

    Entries are either a bare string (the referenced name) or a single-key
    mapping whose key is the name and whose value carries parameters. Returns
    None for anything else.
    """
    if isinstance(entry, str):
        return entry
    if isinstance(entry, dict) and len(entry) >= 1:
        return next(iter(entry))
    return None


def iter_builders_lists(node):
    """Yield every list that is the value of a `builders:` key, recursively.

    Recursing (rather than reading only the top-level `builders:`) catches
    macros nested inside wrappers such as `conditional-step`.
    """
    if isinstance(node, dict):
        for key, val in node.items():
            if key == "builders" and isinstance(val, list):
                yield val
            yield from iter_builders_lists(val)
    elif isinstance(node, list):
        for item in node:
            yield from iter_builders_lists(item)


class Definitions:
    """Loads every `- job-template:` / `- job-group:` / `- builder:` /
    `- project:` block from data/ and works out which definitions are used."""

    def __init__(self, data_dir):
        self.data_dir = data_dir
        self.templates = []    # {name, id, body, file, line}
        self.job_groups = []   # {name, jobs, file, line}
        self.builders = []     # {name, body, file, line}
        self.projects = []     # {name, jobs, file, line}
        self.blocks = []       # every top-level block: {kind, body}
        self._load()
        self._analyse()

    def _load(self):
        yaml = YAML()
        yaml.preserve_quotes = True
        files = sorted(glob.glob(os.path.join(self.data_dir, "**", "*.yaml"),
                                 recursive=True))
        for path in files:
            try:
                with open(path, encoding="utf-8") as fh:
                    docs = list(yaml.load_all(fh))
            except Exception as exc:  # noqa: BLE001 - skip unparseable YAML
                sys.stderr.write(f"warning: skipping {path}: {exc}\n")
                continue
            for doc in docs:
                if not isinstance(doc, list):
                    continue
                for item in doc:
                    if not isinstance(item, dict) or len(item) != 1:
                        continue
                    kind = next(iter(item))
                    body = item[kind]
                    if not isinstance(body, dict):
                        continue
                    self.blocks.append({"kind": kind, "body": body})
                    line = getattr(getattr(item, "lc", None), "line", None)
                    record = {
                        "name": body.get("name"),
                        "body": body,
                        "file": path,
                        "line": (line + 1) if line is not None else None,
                    }
                    if kind == "job-template":
                        record["id"] = body.get("id")
                        self.templates.append(record)
                    elif kind == "job-group":
                        record["jobs"] = list(body.get("jobs") or [])
                        self.job_groups.append(record)
                    elif kind == "builder":
                        self.builders.append(record)
                    elif kind == "project":
                        record["jobs"] = list(body.get("jobs") or [])
                        self.projects.append(record)

    def _analyse(self):
        self._reach_jobs()
        self._reach_builders()

    def _reach_jobs(self):
        """Compute the set of job-template/job-group names reachable from any
        project, following job-groups transitively."""
        groups_by_name = {g["name"]: g for g in self.job_groups if g["name"]}
        reachable = set()
        stack = [ref_key(entry)
                 for proj in self.projects for entry in proj["jobs"]]
        while stack:
            key = stack.pop()
            if key is None or key in reachable:
                continue
            reachable.add(key)
            group = groups_by_name.get(key)
            if group:
                stack.extend(ref_key(e) for e in group["jobs"])
        self.reachable_jobs = reachable

    def _reach_builders(self):
        """Compute the set of builder-macro names referenced by rendered jobs,
        following macro-to-macro composition.

        Seeds from every block that can carry a `builders:` list (`- job:`,
        `- job-template:`, `- defaults:`), then propagates through macros that
        reference other macros. Macro bodies are NOT part of the seed: a macro
        is only reached through the composition graph from a used macro, so an
        unused macro cannot keep another macro alive."""
        macro_names = {b["name"] for b in self.builders if b["name"]}
        # macro name -> other macros it references in its own body
        macro_refs = {}
        for macro in self.builders:
            refs = set()
            for blist in iter_builders_lists(macro["body"]):
                for entry in blist:
                    key = ref_key(entry)
                    if key in macro_names and key != macro["name"]:
                        refs.add(key)
            macro_refs[macro["name"]] = refs
        # seed with every macro referenced from a rendered job (any block that
        # is not itself a builder macro)
        stack = []
        for block in self.blocks:
            if block["kind"] == "builder":
                continue
            for blist in iter_builders_lists(block["body"]):
                for entry in blist:
                    key = ref_key(entry)
                    if key in macro_names:
                        stack.append(key)
        used = set()
        while stack:
            name = stack.pop()
            if name in used:
                continue
            used.add(name)
            stack.extend(macro_refs.get(name, ()))
        self.macro_names = macro_names
        self.used_builders = used

    # -- reporting -----------------------------------------------------------

    def unused_templates(self):
        return [t for t in self.templates
                if t["name"] not in self.reachable_jobs
                and t.get("id") not in self.reachable_jobs]

    def unused_job_groups(self):
        return [g for g in self.job_groups
                if g["name"] not in self.reachable_jobs]

    def unused_builders(self):
        return [b for b in self.builders
                if b["name"] and b["name"] not in self.used_builders]


def _relpath(path):
    return os.path.relpath(path, REPO_ROOT)


def _location(rec):
    loc = _relpath(rec["file"])
    return f"{loc}:{rec['line']}" if rec["line"] else loc


def _filter_ignored(records, patterns):
    def keep(rec):
        names = [n for n in (rec.get("name"), rec.get("id")) if n]
        return not any(fnmatch.fnmatch(n, p) for n in names for p in patterns)
    return [r for r in records if keep(r)]


def report_text(defs, sections):
    lines = ["", "=== Unused JJB definitions in "
             f"{_relpath(defs.data_dir)}/ ===", ""]
    total = 0
    for title, records, defined in sections:
        total += len(records)
        lines.append(f"{title} ({defined} defined, {len(records)} unused):")
        if not records:
            lines.append("  (none)")
        for rec in sorted(records, key=lambda r: (r["file"], r["line"] or 0)):
            label = rec["name"] or "<unnamed>"
            if rec.get("id") and rec["id"] != rec["name"]:
                label += f"  (id: {rec['id']})"
            lines.append(f"  - {label}")
            lines.append(f"      {_location(rec)}")
        lines.append("")
    if total == 0:
        lines.append("Nothing unused. Every definition is referenced.")
        lines.append("")
    return "\n".join(lines)


def report_json(sections):
    def dump(rec):
        out = {"name": rec.get("name"), "file": _relpath(rec["file"]),
               "line": rec["line"]}
        if "id" in rec:
            out["id"] = rec.get("id")
        return out
    key = {"Job-templates": "job_templates", "Job-groups": "job_groups",
           "Builder macros": "builders"}
    return json.dumps(
        {key[title]: [dump(r) for r in records]
         for title, records, _ in sections},
        indent=2)


def _block_line_range(lines, start_idx):
    """Return (start_idx, end_idx), inclusive, for the top-level block whose
    `- kind:` line is at start_idx.

    The block runs until the next line that begins a new top-level item or a
    column-0 comment (or EOF); everything indented in between belongs to it.
    Leading comments above the block are intentionally left in place."""
    end_idx = start_idx
    i = start_idx + 1
    while i < len(lines):
        if lines[i][:1] in ("-", "#"):
            break
        end_idx = i
        i += 1
    return start_idx, end_idx


def _resolve_script(ref, scripts_dir):
    """Resolve an include path to a file under scripts_dir, or None.

    JJB's include_path is `.:scripts:...`, so a reference is written either as
    `scripts/foo.sh` (relative to the include root, i.e. the data dir's parent)
    or as a bare `foo.sh` found via the scripts/ entry. Anything that does not
    land inside scripts_dir (e.g. data/publisher.yaml.inc) resolves to None."""
    include_root = os.path.dirname(os.path.abspath(scripts_dir))
    for base in (include_root, scripts_dir):
        cand = os.path.normpath(os.path.join(base, ref))
        try:
            if os.path.commonpath([scripts_dir, cand]) == scripts_dir:
                return cand
        except ValueError:  # different drives / not comparable
            continue
    return None


def _script_refs(text, scripts_dir):
    """Return the set of scripts_dir files referenced by include tags in text."""
    found = set()
    for ref in INCLUDE_RE.findall(text):
        path = _resolve_script(ref, scripts_dir)
        if path:
            found.add(path)
    return found


def remove_definitions(records, data_dir, keep_scripts=False):
    """Delete each definition's YAML block in place, leaving the rest of every
    file byte-for-byte unchanged.

    Groups records by file, drops whole line ranges, and re-parses the result
    to be sure the edit left valid YAML (raising, without writing, if not).

    Unless keep_scripts is set, also deletes any scripts/*.sh file that a
    removed block pulled in via `!include-raw-escape:` and that no surviving
    definition still references.

    Returns (removed, orphaned_scripts): a list of (record, "file:line") and a
    sorted list of orphaned script paths (deleted unless keep_scripts)."""
    by_file = {}
    for rec in records:
        if rec["line"] is None:
            raise RuntimeError(
                f"cannot locate {rec.get('name')!r} (no source line recorded); "
                "remove it by hand")
        by_file.setdefault(rec["file"], []).append(rec)

    scripts_dir = os.path.join(os.path.dirname(os.path.abspath(data_dir)),
                               "scripts")
    removed = []
    removed_script_refs = set()
    edits = []  # (path, text) to write once every file validates
    for path, recs in by_file.items():
        with open(path, encoding="utf-8") as fh:
            lines = fh.readlines()
        drop = set()
        for rec in recs:
            start, end = _block_line_range(lines, rec["line"] - 1)
            drop.update(range(start, end + 1))
        removed_script_refs |= _script_refs(
            "".join(lines[i] for i in drop), scripts_dir)
        text = "".join(ln for i, ln in enumerate(lines) if i not in drop)
        try:
            list(YAML().load_all(io.StringIO(text)))
        except Exception as exc:  # noqa: BLE001
            raise RuntimeError(
                f"removing blocks from {_relpath(path)} would leave invalid "
                f"YAML ({exc}); no files changed")
        edits.append((path, text))
        for rec in recs:
            removed.append((rec, f"{_relpath(path)}:{rec['line']}"))

    for path, text in edits:
        with open(path, "w", encoding="utf-8") as fh:
            fh.write(text)

    # A script is orphaned only if nothing references it after the edits.
    surviving = set()
    for path in glob.glob(os.path.join(data_dir, "**", "*.yaml"),
                          recursive=True):
        with open(path, encoding="utf-8") as fh:
            surviving |= _script_refs(fh.read(), scripts_dir)
    orphaned = sorted(p for p in removed_script_refs
                      if p not in surviving and os.path.isfile(p))
    if not keep_scripts:
        for path in orphaned:
            os.remove(path)
    return removed, orphaned


def build_parser():
    parser = argparse.ArgumentParser(
        description=(__doc__ or "").splitlines()[0])
    parser.add_argument("--data-dir", default=DEFAULT_DATA_DIR,
                        help="JJB data directory (default: repo data/)")
    parser.add_argument("--ignore", action="append", default=[], metavar="GLOB",
                        help="fnmatch glob of definition names to treat as used "
                             "(repeatable, e.g. --ignore dummy)")
    parser.add_argument("--json", action="store_true",
                        help="emit findings as JSON instead of a text report")
    parser.add_argument("--exit-code", action="store_true",
                        help="exit 1 if any unused definition is found")
    parser.add_argument("--fix", action="store_true",
                        help="remove the unused definitions from their YAML "
                             "files (leaves the changes in the working tree "
                             "for review; does not commit)")
    parser.add_argument("--keep-scripts", action="store_true",
                        help="with --fix, do not delete scripts/*.sh files that "
                             "a removed builder macro was the only referrer of")
    return parser


def main(argv=None):
    args = build_parser().parse_args(argv)
    defs = Definitions(args.data_dir)
    sections = [
        ("Job-templates", _filter_ignored(defs.unused_templates(), args.ignore),
         len(defs.templates)),
        ("Job-groups", _filter_ignored(defs.unused_job_groups(), args.ignore),
         len(defs.job_groups)),
        ("Builder macros", _filter_ignored(defs.unused_builders(), args.ignore),
         len(defs.builders)),
    ]
    if args.fix:
        to_remove = [r for _, records, _ in sections for r in records]
        if not to_remove:
            print("Nothing to fix; every definition is referenced.")
            return 0
        removed, orphaned = remove_definitions(
            to_remove, args.data_dir, keep_scripts=args.keep_scripts)
        print(f"Removed {len(removed)} unused definition(s):")
        for rec, loc in sorted(removed, key=lambda x: x[1]):
            print(f"  - {rec.get('name')}   ({loc})")
        if orphaned:
            verb = "Orphaned (kept)" if args.keep_scripts else "Deleted"
            print(f"\n{verb} {len(orphaned)} now-unreferenced script(s):")
            for path in orphaned:
                print(f"  - {_relpath(path)}")
        print("\nReview the changes, then run:\n"
              "  pre-commit run --all-files\n"
              "  jenkins-jobs test -r data\n"
              "and submit with: git review")
        print("\nNote: removing a definition can free others (a builder used "
              "only by a removed template, etc.); re-run to catch cascades.")
        return 0

    if args.json:
        print(report_json(sections))
    else:
        print(report_text(defs, sections))
    if args.exit_code and any(records for _, records, _ in sections):
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
