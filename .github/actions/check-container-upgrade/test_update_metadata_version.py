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

"""Tests for update-metadata-version.py. Run with: python3 -m unittest discover"""

import contextlib
import importlib.util
import io
import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest import mock

HERE = Path(__file__).parent
REPO_ROOT = HERE.parents[2]
METADATA_INFRA = REPO_ROOT / "test-infra/camel-test-infra-all/src/generated/resources/META-INF/metadata.json"

_spec = importlib.util.spec_from_file_location("update_metadata_version", HERE / "update-metadata-version.py")
updater = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(updater)


def entry(artifact_id, service_version, alias, alias_implementation=None):
    return {
        "alias": alias,
        "aliasImplementation": alias_implementation or [],
        "artifactId": artifact_id,
        "serviceVersion": service_version,
    }


AZURE_BLOB = entry("camel-test-infra-azure-storage-blob", "3.35.0", ["azure"], ["storage-blob"])
AZURE_QUEUE = entry("camel-test-infra-azure-storage-queue", "3.35.0", ["azure"], ["storage-queue"])
OLLAMA = entry("camel-test-infra-ollama", "0.31.2", ["ollama"])
HIVEMQ = entry("camel-test-infra-hivemq", "2025.5", ["hive-mq"])
HIVEMQ_SPARKPLUG = entry("camel-test-infra-hivemq", "camel", ["hive-mq"], ["sparkplug"])
OBSERVABILITY = entry("camel-test-infra-observability", None, ["observability"])


class PropertyPrefixTest(unittest.TestCase):

    def test_container_property_yields_normalized_prefix(self):
        self.assertEqual("azure", updater.property_prefix("azure.container"))
        self.assertEqual("hashicorpvault", updater.property_prefix("hashicorp.vault.container"))
        self.assertEqual("kafka", updater.property_prefix("kafka.container.image"))

    def test_platform_specific_property_has_no_target(self):
        # The Mojo skips these keys, so their version never reaches metadata.json
        self.assertIsNone(updater.property_prefix("ollama.container.ppc64le"))
        self.assertIsNone(updater.property_prefix("aws.container.s390x"))
        self.assertIsNone(updater.property_prefix("tensorflow.serving.container.aarch64"))
        self.assertIsNone(updater.property_prefix("artemis.container.amd64"))

    def test_version_metadata_property_has_no_target(self):
        self.assertIsNone(updater.property_prefix("ollama.container.version.exclude"))
        self.assertIsNone(updater.property_prefix("mongodb.container.version.include"))
        self.assertIsNone(updater.property_prefix("milvus.container.version.freeze.major"))
        self.assertIsNone(updater.property_prefix("rocketmq.container.image.version"))

    def test_non_container_property_has_no_target(self):
        self.assertIsNone(updater.property_prefix("ollama.model"))
        self.assertIsNone(updater.property_prefix("ollama.api.key"))


class MatchesTest(unittest.TestCase):

    def test_matches_service_alias(self):
        self.assertTrue(updater.matches(OLLAMA, "ollama"))
        self.assertFalse(updater.matches(OLLAMA, "milvus"))

    def test_matches_dashed_alias(self):
        self.assertTrue(updater.matches(HIVEMQ, "hivemq"))

    def test_compound_prefix_matches_implementation_alias(self):
        self.assertTrue(updater.matches(HIVEMQ_SPARKPLUG, "hivemqsparkplug"))
        self.assertFalse(updater.matches(HIVEMQ, "hivemqsparkplug"))

    def test_matches_entry_of_another_module(self):
        # azure.container lives in camel-test-infra-azure-common
        self.assertTrue(updater.matches(AZURE_BLOB, "azure"))
        self.assertTrue(updater.matches(AZURE_QUEUE, "azure"))


class UpdateMetadataTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)

    def write(self, entries):
        path = os.path.join(self.tmp.name, "metadata.json")
        with open(path, "w", encoding="utf-8") as f:
            f.write(updater.jackson_format(entries))
        return path

    def read(self, path):
        with open(path, "r", encoding="utf-8") as f:
            return json.load(f)

    def update(self, prefix, old_version, new_version, path):
        with contextlib.redirect_stdout(io.StringIO()):
            return updater.update_metadata(prefix, old_version, new_version, path)

    def run_main(self):
        out = io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(out):
            updater.main()
        return out.getvalue()

    def test_updates_every_entry_fed_by_a_shared_property(self):
        path = self.write([AZURE_BLOB, AZURE_QUEUE, OLLAMA])

        self.assertTrue(self.update("azure", "3.35.0", "3.36.0", path))

        versions = {e["artifactId"]: e["serviceVersion"] for e in self.read(path)}
        self.assertEqual("3.36.0", versions["camel-test-infra-azure-storage-blob"])
        self.assertEqual("3.36.0", versions["camel-test-infra-azure-storage-queue"])
        self.assertEqual("0.31.2", versions["camel-test-infra-ollama"])

    def test_leaves_entries_on_another_version_alone(self):
        path = self.write([AZURE_BLOB, AZURE_QUEUE])

        self.assertFalse(self.update("azure", "3.30.0", "3.36.0", path))

        self.assertEqual(["3.35.0", "3.35.0"], [e["serviceVersion"] for e in self.read(path)])

    def test_version_separates_entries_sharing_a_service_alias(self):
        path = self.write([HIVEMQ, HIVEMQ_SPARKPLUG])

        self.assertTrue(self.update("hivemq", "2025.5", "2025.6", path))

        self.assertEqual(["2025.6", "camel"], [e["serviceVersion"] for e in self.read(path)])

    def test_missing_file_is_skipped(self):
        self.assertFalse(
            self.update("azure", "3.35.0", "3.36.0", os.path.join(self.tmp.name, "absent.json")))

    def test_main_updates_all_metadata_files(self):
        first = self.write([AZURE_BLOB, AZURE_QUEUE])
        second = os.path.join(self.tmp.name, "catalog.json")
        with open(second, "w", encoding="utf-8") as f:
            f.write(updater.jackson_format([AZURE_BLOB, AZURE_QUEUE]))

        with mock.patch("sys.argv", ["prog", "azure.container", "3.35.0", "3.36.0", first, second]):
            self.run_main()

        for path in (first, second):
            self.assertEqual(["3.36.0", "3.36.0"], [e["serviceVersion"] for e in self.read(path)])

    def test_main_succeeds_when_the_property_has_no_metadata_target(self):
        # CAMEL-24252: a bump with nothing to update must not abort the caller
        path = self.write([OLLAMA])

        with mock.patch("sys.argv", ["prog", "ollama.container.ppc64le", "v0.17.6", "v0.24.0", path]):
            output = self.run_main()

        self.assertIn("nothing to update", output)
        self.assertEqual("0.31.2", self.read(path)[0]["serviceVersion"])

    def test_main_succeeds_when_no_entry_matches(self):
        # CAMEL-24252: multi-container modules carry no serviceVersion
        path = self.write([OBSERVABILITY])

        with mock.patch("sys.argv", ["prog", "observability.perses.container", "v0.53.1", "v0.54.0", path]):
            output = self.run_main()

        self.assertIn("nothing to update", output)
        self.assertIsNone(self.read(path)[0]["serviceVersion"])

    def test_main_rejects_missing_arguments(self):
        with mock.patch("sys.argv", ["prog", "azure.container", "3.35.0"]):
            with self.assertRaises(SystemExit) as raised:
                self.run_main()

        self.assertEqual(1, raised.exception.code)


class JacksonFormatTest(unittest.TestCase):

    @unittest.skipUnless(METADATA_INFRA.is_file(), f"{METADATA_INFRA} not available")
    def test_output_matches_the_generated_metadata(self):
        # The formatter must reproduce the Maven build output byte for byte,
        # otherwise every bump would rewrite the whole file
        content = METADATA_INFRA.read_text(encoding="utf-8")

        self.assertEqual(content, updater.jackson_format(json.loads(content)))


if __name__ == "__main__":
    unittest.main()
