#!/usr/bin/env python3
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# /// script
# requires-python = ">=3.9"
# dependencies = ["defusedxml>=0.7.1"]
# ///

"""Collect recovered flaky tests from surefire/failsafe XML reports.

A recovered flake is a test that failed at least once and then passed within
the attempts allowed by ``rerunFailingTestsCount``. Surefire records those
attempts as ``<flakyFailure>``/``<flakyError>`` and reports the build as
successful, so without this script they leave no trace in CI output at all.

Tests that failed every attempt are recorded as ``<rerunFailure>``/
``<rerunError>``. They already fail the build and are deliberately not
collected here.

Run with ``uv run collect-flakes.py`` so the PEP-723 dependency block above is
honoured. Plain ``python3 collect-flakes.py`` ignores it and will fail on the
defusedxml import.
"""

import argparse
import json
import os
import sys
from dataclasses import dataclass, replace
from html import escape
from pathlib import Path
from xml.etree.ElementTree import ParseError

import defusedxml.ElementTree as ET
from defusedxml.common import DefusedXmlException

# Surefire records a failed-then-passed attempt under these tags.
FLAKY_TAGS = ("flakyFailure", "flakyError")

# Maven writes unit-test reports under target/surefire-reports and
# integration-test reports under target/failsafe-reports.
REPORT_DIRS = ("surefire-reports", "failsafe-reports")


@dataclass(frozen=True)
class Flake:
    classname: str
    test: str
    failed_attempts: int
    message: str
    module: str = ""


def parse_report(path):
    """Return the recovered flakes recorded in a single surefire/failsafe report.

    Raises ValueError if the document carries a DOCTYPE or tries an
    entity-expansion or external-entity attack. Surefire never emits a DOCTYPE,
    so any report that declares one did not come from the build. forbid_dtd has
    to be passed explicitly: defusedxml only forbids entity *declarations* by
    default, which would let a bare `<!DOCTYPE .. SYSTEM ..>` through.
    """
    raw = Path(path).read_bytes()
    try:
        root = ET.fromstring(raw, forbid_dtd=True)
    except DefusedXmlException as exc:
        raise ValueError(
            f"refusing hostile XML report {path}: {type(exc).__name__}"
        ) from exc

    flakes = []
    for testcase in root.iter("testcase"):
        # Document order, so attempts[0] really is the first attempt: grouping by
        # tag would report the first flakyFailure even when a flakyError came first.
        attempts = [el for el in testcase if el.tag in FLAKY_TAGS]
        if not attempts:
            continue
        flakes.append(
            Flake(
                classname=testcase.get("classname", ""),
                test=testcase.get("name", ""),
                failed_attempts=len(attempts),
                message=attempts[0].get("message", ""),
            )
        )
    return flakes


def _report_files(root):
    """Yield ``(module, report path)`` for every report under a reactor root.

    os.walk rather than a ``**`` glob so the recursion can be pruned: after a
    full build every module's target/ holds thousands of class and
    generated-source directories, none of which can hold a report.
    """
    for dirpath, dirnames, filenames in os.walk(root):
        name = os.path.basename(dirpath)
        parent = os.path.basename(os.path.dirname(dirpath))
        if name in REPORT_DIRS and parent == "target":
            dirnames[:] = []
            # .../<module>/target/<reports_dir>
            module = Path(dirpath).parent.parent.relative_to(root).as_posix()
            for filename in sorted(filenames):
                if filename.startswith("TEST-") and filename.endswith(".xml"):
                    yield module, Path(dirpath) / filename
        elif name == "target":
            dirnames[:] = [d for d in dirnames if d in REPORT_DIRS]
        else:
            dirnames[:] = [d for d in dirnames if not d.startswith(".")]


def collect(root):
    """Walk a reactor and return every recovered flake, labelled by module.

    An unreadable or hostile report is skipped with a warning rather than
    aborting: this runs on the always-path of a build whose result is already
    determined, and must never be the reason a job fails. ParseError covers a
    report truncated by a JVM killed mid-write, ValueError the hostile documents
    parse_report rejects, and OSError an unreadable file.
    """
    root = Path(root)
    flakes = []
    for module, report in _report_files(root):
        try:
            parsed = parse_report(report)
        except (ParseError, ValueError, OSError) as exc:
            print(f"skipping unreadable report {report}: {exc}", file=sys.stderr)
            continue
        flakes.extend(replace(flake, module=module) for flake in parsed)
    return flakes


def _ordered(flakes):
    return sorted(flakes, key=lambda f: (f.module, f.classname, f.test))


def to_payload(flakes, label=""):
    """Build the machine-readable summary uploaded as a workflow artifact.

    Aggregating this across PRs is what turns anecdotes about flaky tests into
    the evidence needed to decide which ones to quarantine. ``label`` records
    which matrix entry produced the data, so the aggregate can tell a test that
    only flakes on one JDK from one that flakes everywhere.
    """
    ordered = _ordered(flakes)
    return {
        "label": label,
        "total_flakes": len(ordered),
        "total_retried_attempts": sum(f.failed_attempts for f in ordered),
        "flakes": [
            {
                "module": f.module,
                "classname": f.classname,
                "test": f.test,
                "failed_attempts": f.failed_attempts,
                "message": f.message,
            }
            for f in ordered
        ],
    }


def _cell(text):
    """Make a value safe to drop into a markdown table cell.

    Angle brackets are escaped, not just passed through: assertion messages are
    full of them (``expected: <true> but was: <false>``) and GitHub's renderer
    treats ``<true>`` as raw HTML and strips it, silently eating the message.
    """
    collapsed = escape(" ".join(text.split()), quote=False)
    return collapsed.replace("|", "\\|") or "(no message)"


def render_markdown(flakes, label=""):
    """Render the PR-comment section, or an empty string when nothing was retried.

    ``label`` names the matrix entry the data came from (for example ``JDK 17``).
    The PR-comment artifact is uploaded with overwrite: true across the JDK
    matrix, and unlike the rest of the comment, flake data is genuinely not
    identical between entries. Naming the entry means a reader can at least tell
    which JDK a reported flake came from; the per-JDK flakes-java-* artifacts
    remain the complete record.
    """
    ordered = _ordered(flakes)
    if not ordered:
        return ""

    attempts = sum(f.failed_attempts for f in ordered)
    noun = "test" if len(ordered) == 1 else "tests"
    attempt_noun = "attempt" if attempts == 1 else "attempts"
    on_label = f" on {label}" if label else ""
    lines = [
        "",
        f":repeat: **{len(ordered)} {noun} passed only after a retry{on_label}** "
        f"({attempts} retried {attempt_noun})",
        "",
        f"<details><summary>Recovered flaky tests{on_label} ({len(ordered)})</summary>",
        "",
        "| Module | Test | Failed attempts | First failure |",
        "| --- | --- | --- | --- |",
    ]
    for f in ordered:
        simple_class = _cell(f.classname.rsplit(".", 1)[-1])
        lines.append(
            f"| `{_cell(f.module)}` | `{simple_class}.{_cell(f.test)}` | {f.failed_attempts} "
            f"| {_cell(f.message)} |"
        )
    lines += [
        "",
        "> :information_source: These tests did **not** fail the build. Surefire "
        "retried them and they passed.",
        "> Retries are enabled project-wide by `surefire.rerunFailingTestsCount` "
        "in `parent/pom.xml`.",
        "",
        "</details>",
    ]
    return "\n".join(lines)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("root", help="reactor root to scan for test reports")
    parser.add_argument(
        "--comment-file",
        help="markdown file to append the flake section to (left untouched when "
        "nothing was retried)",
    )
    parser.add_argument("--json-out", help="path to write the machine-readable summary")
    parser.add_argument(
        "--step-summary",
        help="path to append the section to as well, typically $GITHUB_STEP_SUMMARY",
    )
    parser.add_argument(
        "--label",
        default="",
        help="matrix entry this run covers (e.g. 'JDK 17'), named in the section "
        "and recorded in the JSON so aggregation can tell the entries apart",
    )
    args = parser.parse_args(argv)

    flakes = collect(args.root)
    section = render_markdown(flakes, args.label)

    for target in (args.comment_file, args.step_summary):
        if target and section:
            with open(target, "a", encoding="utf-8") as handle:
                handle.write(section + "\n")

    if args.json_out:
        Path(args.json_out).write_text(
            json.dumps(to_payload(flakes, args.label), indent=2) + "\n",
            encoding="utf-8",
        )

    print(f"recovered flaky tests: {len(flakes)}")
    # Never fail the job: the build verdict is already decided by this point.
    return 0


if __name__ == "__main__":
    sys.exit(main())
