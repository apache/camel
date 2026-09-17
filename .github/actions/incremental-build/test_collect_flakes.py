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

"""Tests for collect-flakes.py.

Run with: uv run --with defusedxml python3 -m unittest discover
(plain python3 fails on the defusedxml import unless it is already installed).
"""

import contextlib
import importlib.util
import io
import json
import shutil
import tempfile
import unittest
from dataclasses import replace
from pathlib import Path

HERE = Path(__file__).parent
TESTDATA = HERE / "testdata"

_spec = importlib.util.spec_from_file_location("collect_flakes", HERE / "collect-flakes.py")
collector = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(collector)


class ParseReportTest(unittest.TestCase):
    """A recovered flake is a test that failed at least once and then passed.

    Surefire records it as <flakyFailure>. A test that failed every attempt is
    recorded as <rerunFailure> and is NOT a flake: it already fails the build.
    Conflating the two is the mistake this test exists to catch.
    """

    def test_reports_only_the_test_that_passed_on_retry(self):
        flakes = collector.parse_report(TESTDATA / "TEST-recovered-flake.xml")

        self.assertEqual(
            ["flakyPassesOnRetry"],
            [f.test for f in flakes],
            "expected only the recovered flake; alwaysFails failed every attempt "
            "and alwaysPasses never failed",
        )


class FlakeAttributionTest(unittest.TestCase):
    """A flake report is only actionable if it names the test and says why it
    failed. Counting attempts separates a once-in-a-blue-moon flake from one
    that needed every retry surefire allowed.
    """

    def test_captures_class_and_failure_message(self):
        (flake,) = collector.parse_report(TESTDATA / "TEST-recovered-flake.xml")

        self.assertEqual("org.apache.camel.component.probe.ProbeTest", flake.classname)
        self.assertEqual("deliberate first-attempt failure", flake.message)

    def test_reports_the_earliest_attempt_when_the_kinds_differ(self):
        (flake,) = collector.parse_report(TESTDATA / "TEST-error-then-failure-flake.xml")

        self.assertEqual(
            "first attempt timed out",
            flake.message,
            "the column is labelled 'First failure'; reading flakyFailure before "
            "flakyError would report the second attempt and hide the real cause",
        )

    def test_counts_every_failed_attempt_not_just_the_first(self):
        (flake,) = collector.parse_report(TESTDATA / "TEST-two-attempt-flake.xml")

        self.assertEqual(
            2,
            flake.failed_attempts,
            "the test failed twice before passing; reporting 1 would hide how "
            "close it came to failing the build",
        )


class CollectTest(unittest.TestCase):
    """Walking the reactor has to cover unit tests (surefire) and integration
    tests (failsafe), and attribute each flake to the module that owns it. A
    bare class name is not enough to file a ticket against.
    """

    @staticmethod
    def _place(reports_dir, fixture):
        reports_dir.mkdir(parents=True)
        shutil.copy(TESTDATA / fixture, reports_dir / fixture)

    def test_finds_surefire_and_failsafe_reports_and_labels_the_owning_module(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._place(
                root / "components/camel-foo/target/surefire-reports",
                "TEST-recovered-flake.xml",
            )
            self._place(
                root / "components/camel-bar/target/failsafe-reports",
                "TEST-two-attempt-flake.xml",
            )

            found = {(f.module, f.test) for f in collector.collect(root)}

        self.assertEqual(
            {
                ("components/camel-bar", "connectsEventually"),
                ("components/camel-foo", "flakyPassesOnRetry"),
            },
            found,
        )

    def test_a_corrupt_report_is_skipped_rather_than_losing_the_whole_run(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            good = root / "components/camel-foo/target/surefire-reports"
            self._place(good, "TEST-recovered-flake.xml")
            (root / "components/camel-bad/target/surefire-reports").mkdir(parents=True)
            (
                root / "components/camel-bad/target/surefire-reports/TEST-truncated.xml"
            ).write_text("<testsuite><testcase name=")

            warnings = io.StringIO()
            with contextlib.redirect_stderr(warnings):
                found = [f.test for f in collector.collect(root)]

        self.assertEqual(["flakyPassesOnRetry"], found)
        self.assertIn("TEST-truncated.xml", warnings.getvalue())


class RenderTest(unittest.TestCase):
    """The PR comment is the only place a contributor sees that a green build
    quietly retried a test, so the section has to name the test precisely enough
    to tag or file a ticket against it.
    """

    FLAKES = [
        collector.Flake(
            classname="org.apache.camel.component.probe.SlowTest",
            test="connectsEventually",
            failed_attempts=2,
            message="Connection refused",
            module="components/camel-probe",
        )
    ]

    def test_renders_nothing_when_no_test_was_retried(self):
        self.assertEqual(
            "",
            collector.render_markdown([]),
            "a clean run must not add an empty section to every PR comment",
        )

    def test_names_the_module_test_and_attempt_count(self):
        rendered = collector.render_markdown(self.FLAKES)

        self.assertIn("components/camel-probe", rendered)
        self.assertIn("SlowTest.connectsEventually", rendered)
        self.assertIn("2", rendered)
        self.assertIn("Connection refused", rendered)

    def test_escapes_a_message_github_would_otherwise_render_as_html(self):
        rendered = collector.render_markdown(
            [replace(self.FLAKES[0], message="expected: <true> but was: <false>")]
        )

        self.assertIn(
            "expected: &lt;true&gt; but was: &lt;false&gt;",
            rendered,
            "GitHub strips <true> as raw HTML, which would empty the column for "
            "the assertion messages that produce most flake reports",
        )

    def test_escapes_a_pipe_in_the_test_name_so_it_cannot_split_the_row(self):
        rendered = collector.render_markdown(
            [replace(self.FLAKES[0], test="[1] input=<script>alert(1)</script>|extra")]
        )

        self.assertIn(
            "&lt;script&gt;alert(1)&lt;/script&gt;\\|extra",
            rendered,
            "a JUnit 5 @ParameterizedTest display name or a Camel URI/DSL "
            "parameterized test routinely contains '|', which would otherwise "
            "split the row into the wrong columns",
        )

    def test_names_the_matrix_entry_so_the_overwritten_comment_stays_readable(self):
        rendered = collector.render_markdown(self.FLAKES, "JDK 25")

        self.assertIn(
            "on JDK 25",
            rendered,
            "the PR comment is overwritten last-writer-wins across the JDK "
            "matrix, so an unlabelled section leaves the reader unable to tell "
            "which JDK the flake came from",
        )

    def test_omits_the_label_entirely_when_no_matrix_entry_was_given(self):
        rendered = collector.render_markdown(self.FLAKES)

        self.assertNotIn(
            " on ",
            rendered.splitlines()[1],
            "a single-entry caller must not get a dangling 'on ' in the heading",
        )

    def test_json_payload_records_the_total_and_the_detail(self):
        payload = collector.to_payload(self.FLAKES)

        self.assertEqual("", payload["label"])
        self.assertEqual(1, payload["total_flakes"])
        self.assertEqual(2, payload["total_retried_attempts"])
        self.assertEqual(
            [
                {
                    "module": "components/camel-probe",
                    "classname": "org.apache.camel.component.probe.SlowTest",
                    "test": "connectsEventually",
                    "failed_attempts": 2,
                    "message": "Connection refused",
                }
            ],
            payload["flakes"],
        )

    def test_json_payload_records_the_matrix_entry_for_cross_run_aggregation(self):
        payload = collector.to_payload(self.FLAKES, "JDK 17")

        self.assertEqual(
            "JDK 17",
            payload["label"],
            "aggregating these artifacts is the point, and it cannot "
            "distinguish a test that only flakes on one JDK from one that "
            "flakes everywhere unless each payload says which JDK it is",
        )


class MainTest(unittest.TestCase):
    """This runs on the always-path of a build whose verdict is already decided.
    It appends to the existing comment rather than replacing it, and never
    reports failure.
    """

    def _reactor_with_one_flake(self, root):
        reports = root / "components/camel-probe/target/surefire-reports"
        reports.mkdir(parents=True)
        shutil.copy(
            TESTDATA / "TEST-two-attempt-flake.xml",
            reports / "TEST-two-attempt-flake.xml",
        )

    def test_appends_to_the_existing_comment_and_writes_the_json_artifact(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._reactor_with_one_flake(root)
            comment = root / "incremental-test-comment.md"
            comment.write_text(":test_tube: **CI tested the following modules:**\n")
            payload_file = root / "flakes.json"

            with contextlib.redirect_stdout(io.StringIO()):
                exit_code = collector.main(
                    [str(root), "--comment-file", str(comment), "--json-out", str(payload_file)]
                )

            text = comment.read_text()
            payload = json.loads(payload_file.read_text())

        self.assertEqual(0, exit_code)
        self.assertTrue(
            text.startswith(":test_tube:"), "must not clobber the existing comment"
        )
        self.assertIn("connectsEventually", text)
        self.assertEqual(1, payload["total_flakes"])
        self.assertEqual(2, payload["total_retried_attempts"])

    def test_writes_a_zero_payload_and_no_comment_section_on_a_clean_run(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            comment = root / "incremental-test-comment.md"
            comment.write_text("original\n")
            payload_file = root / "flakes.json"

            with contextlib.redirect_stdout(io.StringIO()):
                exit_code = collector.main(
                    [str(root), "--comment-file", str(comment), "--json-out", str(payload_file)]
                )

            text = comment.read_text()
            payload = json.loads(payload_file.read_text())

        self.assertEqual(0, exit_code)
        self.assertEqual("original\n", text)
        self.assertEqual(
            0,
            payload["total_flakes"],
            "a clean run must still record zero, so aggregating artifacts across "
            "PRs does not mistake a missing file for a missing flake",
        )


class DoctypeRejectionTest(unittest.TestCase):
    """Surefire never emits a DOCTYPE, so any report carrying one is not a
    surefire report. Refusing it up front closes entity-expansion DoS without
    pulling in a third-party XML parser.
    """

    def test_rejects_a_report_containing_a_doctype(self):
        with self.assertRaises(ValueError):
            collector.parse_report(TESTDATA / "TEST-doctype-rejected.xml")

    def test_rejects_a_doctype_that_declares_no_entities_of_its_own(self):
        """defusedxml forbids entity *declarations* by default but allows a bare
        DOCTYPE, so forbid_dtd has to be requested explicitly. Without it an
        external subset pointing at an attacker-controlled DTD is accepted.
        """
        with self.assertRaises(ValueError):
            collector.parse_report(TESTDATA / "TEST-doctype-no-entities-rejected.xml")

    def test_rejects_a_doctype_hidden_by_a_non_utf8_encoding(self):
        """A byte-level scan for b'<!DOCTYPE' misses a UTF-16 document, where
        the marker is interleaved with NUL bytes. Entity defence has to happen
        in the parser, after decoding, not in a pre-parse string search.
        """
        with self.assertRaises(ValueError):
            collector.parse_report(TESTDATA / "TEST-utf16-doctype-rejected.xml")


if __name__ == "__main__":
    unittest.main()
