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
import sys
from dataclasses import dataclass, replace
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

    Raises ValueError if the document tries an entity-expansion or external-entity
    attack. Surefire never emits a DOCTYPE, so any report that declares entities
    did not come from the build.
    """
    raw = Path(path).read_bytes()
    try:
        root = ET.fromstring(raw)
    except DefusedXmlException as exc:
        raise ValueError(
            f"refusing hostile XML report {path}: {type(exc).__name__}"
        ) from exc

    flakes = []
    for testcase in root.iter("testcase"):
        attempts = [el for tag in FLAKY_TAGS for el in testcase.findall(tag)]
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


def collect(root):
    """Walk a reactor and return every recovered flake, labelled by module.

    An unreadable or hostile report is skipped with a warning rather than
    aborting: this runs on the always-path of a build whose result is already
    determined, and must never be the reason a job fails.
    """
    root = Path(root)
    flakes = []
    for reports_dir in REPORT_DIRS:
        for report in sorted(root.glob(f"**/target/{reports_dir}/TEST-*.xml")):
            # .../<module>/target/<reports_dir>/TEST-*.xml
            module = report.parent.parent.parent.relative_to(root).as_posix()
            try:
                parsed = parse_report(report)
            except (ParseError, ValueError, OSError) as exc:
                print(f"skipping unreadable report {report}: {exc}", file=sys.stderr)
                continue
            flakes.extend(replace(flake, module=module) for flake in parsed)
    return flakes


def _ordered(flakes):
    return sorted(flakes, key=lambda f: (f.module, f.classname, f.test))


def to_payload(flakes):
    """Build the machine-readable summary uploaded as a workflow artifact.

    Aggregating this across PRs is what turns anecdotes about flaky tests into
    the evidence needed to decide which ones to quarantine.
    """
    ordered = _ordered(flakes)
    return {
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
    """Make a value safe to drop into a markdown table cell."""
    return " ".join(text.split()).replace("|", "\\|") or "(no message)"


def render_markdown(flakes):
    """Render the PR-comment section, or an empty string when nothing was retried."""
    ordered = _ordered(flakes)
    if not ordered:
        return ""

    attempts = sum(f.failed_attempts for f in ordered)
    noun = "test" if len(ordered) == 1 else "tests"
    lines = [
        "",
        f":repeat: **{len(ordered)} {noun} passed only after a retry** "
        f"({attempts} retried attempts)",
        "",
        f"<details><summary>Recovered flaky tests ({len(ordered)})</summary>",
        "",
        "| Module | Test | Failed attempts | First failure |",
        "| --- | --- | --- | --- |",
    ]
    for f in ordered:
        simple_class = f.classname.rsplit(".", 1)[-1]
        lines.append(
            f"| `{f.module}` | `{simple_class}.{f.test}` | {f.failed_attempts} "
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
    args = parser.parse_args(argv)

    flakes = collect(args.root)
    section = render_markdown(flakes)

    for target in (args.comment_file, args.step_summary):
        if target and section:
            with open(target, "a", encoding="utf-8") as handle:
                handle.write(section + "\n")

    if args.json_out:
        Path(args.json_out).write_text(
            json.dumps(to_payload(flakes), indent=2) + "\n", encoding="utf-8"
        )

    print(f"recovered flaky tests: {len(flakes)}")
    # Never fail the job: the build verdict is already decided by this point.
    return 0


if __name__ == "__main__":
    sys.exit(main())
