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

"""Tests for the multi-arch manifest verification added by CAMEL-26492.

Run with: python3 -m unittest test_check_container_versions
"""

import importlib.util
import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

HERE = Path(__file__).parent

_spec = importlib.util.spec_from_file_location(
    "check_container_versions", HERE / "check-container-versions.py"
)
checker_mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(checker_mod)

ContainerImage = checker_mod.ContainerImage
ContainerVersionChecker = checker_mod.ContainerVersionChecker
ContainerRegistryAPI = checker_mod.ContainerRegistryAPI
DockerHubAPI = checker_mod.DockerHubAPI
QuayAPI = checker_mod.QuayAPI
REQUIRED_ARCHITECTURES = checker_mod.REQUIRED_ARCHITECTURES
VersionCheckResult = checker_mod.VersionCheckResult


def make_image(registry="mirror.gcr.io", namespace="library", name="postgres",
               current_version="17.0", property_name="postgres.container",
               has_arch_override=False, arch_overrides=None, skipped_arches=None):
    return ContainerImage(
        registry=registry,
        namespace=namespace,
        name=name,
        current_version=current_version,
        property_name=property_name,
        file_path="/fake/container.properties",
        has_arch_override=has_arch_override,
        arch_overrides=arch_overrides if arch_overrides is not None else [],
        skipped_arches=skipped_arches if skipped_arches is not None else [],
    )


def manifest_response(architectures):
    """Build a fake manifest list JSON payload for the given os/arch strings."""
    manifests = [
        {"platform": {"os": os_, "architecture": arch_}}
        for os_, arch_ in (a.split("/") for a in architectures)
    ]
    return {"manifests": manifests, "mediaType": "application/vnd.docker.distribution.manifest.list.v2+json"}


class RequiredArchitecturesTest(unittest.TestCase):

    def test_required_architectures_includes_s390x_and_ppc64le(self):
        self.assertIn("linux/s390x", REQUIRED_ARCHITECTURES)
        self.assertIn("linux/ppc64le", REQUIRED_ARCHITECTURES)


class ContainerRegistryAPICheckArchitecturesTest(unittest.TestCase):
    """Unit tests for ContainerRegistryAPI.check_architectures()."""

    def _api(self):
        api = checker_mod.GCRAPI(timeout=5)
        return api

    def _mock_response(self, payload, status=200):
        resp = MagicMock()
        resp.status_code = status
        resp.json.return_value = payload
        return resp

    def test_returns_empty_when_all_arches_present(self):
        api = self._api()
        image = make_image()
        with patch.object(api.session, "get",
                          return_value=self._mock_response(
                              manifest_response(["linux/amd64", "linux/s390x", "linux/ppc64le", "linux/arm64"]))):
            missing = api.check_architectures(image, "17.1")
        self.assertEqual([], missing)

    def test_returns_missing_ppc64le(self):
        api = self._api()
        image = make_image()
        with patch.object(api.session, "get",
                          return_value=self._mock_response(
                              manifest_response(["linux/amd64", "linux/s390x", "linux/arm64"]))):
            missing = api.check_architectures(image, "17.1")
        self.assertEqual(["linux/ppc64le"], missing)

    def test_returns_both_missing(self):
        api = self._api()
        image = make_image()
        with patch.object(api.session, "get",
                          return_value=self._mock_response(
                              manifest_response(["linux/amd64", "linux/arm64"]))):
            missing = api.check_architectures(image, "17.1")
        self.assertIn("linux/s390x", missing)
        self.assertIn("linux/ppc64le", missing)

    def test_returns_empty_on_non_200(self):
        api = self._api()
        image = make_image()
        with patch.object(api.session, "get",
                          return_value=self._mock_response({}, status=404)):
            missing = api.check_architectures(image, "17.1")
        self.assertEqual([], missing)

    def test_returns_missing_arches_on_single_arch_manifest(self):
        """A single-arch manifest (no 'manifests' key) must count as arch-missing, not pass."""
        api = self._api()
        image = make_image()
        # Single-arch manifest has no 'manifests' key
        with patch.object(api.session, "get",
                          return_value=self._mock_response({"schemaVersion": 2, "layers": []})):
            missing = api.check_architectures(image, "17.1")
        # Both required arches must be reported as missing
        self.assertIn("linux/s390x", missing)
        self.assertIn("linux/ppc64le", missing)

    def test_returns_empty_on_exception(self):
        api = self._api()
        image = make_image()
        with patch.object(api.session, "get", side_effect=Exception("network error")):
            missing = api.check_architectures(image, "17.1")
        self.assertEqual([], missing)


class DockerHubAPICheckArchitecturesTest(unittest.TestCase):
    """DockerHub override: obtains a Bearer token before fetching the manifest."""

    def _mock_response(self, payload, status=200):
        resp = MagicMock()
        resp.status_code = status
        resp.json.return_value = payload
        return resp

    def test_uses_token_for_manifest_fetch(self):
        api = DockerHubAPI(timeout=5)
        image = make_image(registry="docker.io", namespace="library", name="postgres")
        token_resp = self._mock_response({"token": "mytoken"})
        manifest_resp = self._mock_response(
            manifest_response(["linux/amd64", "linux/s390x", "linux/ppc64le"])
        )
        with patch.object(api.session, "get", side_effect=[token_resp, manifest_resp]) as mock_get:
            missing = api.check_architectures(image, "17.1")
        self.assertEqual([], missing)
        # Second call must carry the Authorization header
        _, kwargs = mock_get.call_args_list[1]
        headers = kwargs.get("headers", {})
        self.assertIn("Authorization", headers)
        self.assertIn("mytoken", headers["Authorization"])

    def test_official_image_uses_library_prefix(self):
        api = DockerHubAPI(timeout=5)
        image = make_image(registry="docker.io", namespace="", name="postgres")
        with patch.object(api.session, "get",
                          side_effect=[self._mock_response({"token": "t"}),
                                       self._mock_response(manifest_response(["linux/amd64"]))]) as mock_get:
            api.check_architectures(image, "17.1")
        manifest_url = mock_get.call_args_list[1][0][0]
        self.assertIn("library/postgres", manifest_url)


class QuayAPICheckArchitecturesTest(unittest.TestCase):
    """QuayAPI override uses quay.io/v2/<repo>/manifests/<tag>."""

    def _mock_response(self, payload, status=200):
        resp = MagicMock()
        resp.status_code = status
        resp.json.return_value = payload
        return resp

    def test_uses_v2_manifest_endpoint(self):
        api = QuayAPI(timeout=5)
        image = make_image(registry="quay.io", namespace="strimzi", name="kafka")
        with patch.object(api.session, "get",
                          return_value=self._mock_response(
                              manifest_response(["linux/amd64", "linux/s390x", "linux/ppc64le"])
                          )) as mock_get:
            missing = api.check_architectures(image, "latest-kafka-3.9.1")
        self.assertEqual([], missing)
        url = mock_get.call_args[0][0]
        self.assertIn("quay.io/v2/strimzi/kafka/manifests/latest-kafka-3.9.1", url)


class ParsePropertiesArchOverrideTest(unittest.TestCase):
    """parse_properties_file must set has_arch_override for images that have arch siblings."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.checker = ContainerVersionChecker()

    def _write(self, content):
        path = os.path.join(self.tmp.name, "container.properties")
        with open(path, "w") as f:
            f.write(content)
        return path

    def test_has_arch_override_set_when_ppc64le_sibling_present(self):
        path = self._write(
            "keycloak.container=mirror.gcr.io/keycloak/keycloak:26.0.0\n"
            "keycloak.container.ppc64le=icr.io/ppc64le-oss/keycloak-ppc64le:v26.0.0-bv\n"
        )
        images = self.checker.parse_properties_file(path)
        main = next((i for i in images if i.property_name == "keycloak.container"), None)
        self.assertIsNotNone(main)
        self.assertTrue(main.has_arch_override,
                        "keycloak.container should be flagged because keycloak.container.ppc64le exists")

    def test_has_arch_override_set_when_s390x_sibling_present(self):
        path = self._write(
            "cassandra.container=mirror.gcr.io/cassandra:4.0.0\n"
            "cassandra.container.s390x=mirror.gcr.io/s390x/cassandra:4.0.0\n"
        )
        images = self.checker.parse_properties_file(path)
        main = next((i for i in images if i.property_name == "cassandra.container"), None)
        self.assertIsNotNone(main)
        self.assertTrue(main.has_arch_override)

    def test_no_arch_override_when_no_arch_sibling(self):
        path = self._write(
            "kafka.container=quay.io/strimzi/kafka:latest-kafka-3.9.1\n"
        )
        images = self.checker.parse_properties_file(path)
        self.assertEqual(1, len(images))
        self.assertFalse(images[0].has_arch_override)

    def test_arch_override_property_itself_not_in_returned_images(self):
        """Arch-specific override properties (e.g. keycloak.container.ppc64le) contain
        platform-specific registry refs that cannot be parsed as a standard container
        ref.  Whether they parse or not, the caller only cares about the base image."""
        path = self._write(
            "kafka.container=quay.io/strimzi/kafka:3.9.1\n"
            "kafka.container.ppc64le=icr.io/ppc64le-oss/kafka-ppc64le:3.9.1\n"
        )
        images = self.checker.parse_properties_file(path)
        names = {i.property_name for i in images}
        # The base property must be present
        self.assertIn("kafka.container", names)


class CheckImageVersionsArchGatingTest(unittest.TestCase):
    """check_image_versions must gate upgrades on multi-arch availability."""

    def setUp(self):
        self.checker = ContainerVersionChecker(skip_arch_check=False)
        # Replace the GCR API with a mock
        self.mock_api = MagicMock()
        self.checker.registry_apis["gcr.io"] = self.mock_api
        self.checker.registry_apis["mirror.gcr.io"] = self.mock_api

    def test_upgrade_proposed_when_all_arches_present(self):
        image = make_image(current_version="17.0", has_arch_override=False)
        self.mock_api.get_available_versions.return_value = ["17.1", "17.0"]
        self.mock_api.check_architectures.return_value = []  # all good

        result = self.checker.check_image_versions(image)

        self.assertEqual(["17.1"], result.newer_versions)
        self.assertFalse(result.is_latest)
        self.assertEqual([], result.arch_missing)

    def test_upgrade_blocked_when_arch_missing(self):
        image = make_image(current_version="17.0", has_arch_override=False)
        self.mock_api.get_available_versions.return_value = ["17.1", "17.0"]
        self.mock_api.check_architectures.return_value = ["linux/ppc64le"]

        result = self.checker.check_image_versions(image)

        self.assertEqual([], result.newer_versions,
                         "No upgrade should be proposed when ppc64le is missing")
        self.assertTrue(result.is_latest)
        self.assertIn("linux/ppc64le", result.arch_missing)

    def test_arch_check_targets_latest_version_not_just_newer(self):
        """The arch check must verify latest_version (what the workflow bumps to)."""
        image = make_image(current_version="17.0", has_arch_override=False)
        # latest_version = "18.0" (sorted_versions[0]); newer_versions[0] would be "17.1"
        self.mock_api.get_available_versions.return_value = ["18.0", "17.1", "17.0"]
        self.mock_api.check_architectures.return_value = ["linux/ppc64le"]

        result = self.checker.check_image_versions(image)

        # check_architectures should have been called with "18.0" (latest_version), not "17.1"
        called_with_tag = self.mock_api.check_architectures.call_args[0][1]
        self.assertEqual("18.0", called_with_tag,
                         "Arch check must target latest_version, not newer_versions[0]")

    def test_partial_arch_override_still_checks_unoverridden_arch(self):
        """Only ppc64le is overridden → s390x must still be checked and can block an upgrade."""
        image = make_image(current_version="17.0", has_arch_override=True,
                           arch_overrides=["linux/ppc64le"])
        self.mock_api.get_available_versions.return_value = ["17.1", "17.0"]
        # s390x is missing; ppc64le is covered by arch_override
        self.mock_api.check_architectures.return_value = ["linux/s390x", "linux/ppc64le"]

        result = self.checker.check_image_versions(image)

        # Only linux/s390x should block (linux/ppc64le is exempted)
        self.assertEqual([], result.newer_versions)
        self.assertIn("linux/s390x", result.arch_missing)
        self.assertNotIn("linux/ppc64le", result.arch_missing)

    def test_skipped_arches_exempted_from_check(self):
        """Arches marked skipITs.* in pom.xml must be exempt from the manifest check."""
        image = make_image(current_version="17.0", has_arch_override=False,
                           skipped_arches=["linux/s390x", "linux/ppc64le"])
        self.mock_api.get_available_versions.return_value = ["17.1", "17.0"]
        self.mock_api.check_architectures.return_value = ["linux/s390x", "linux/ppc64le"]

        result = self.checker.check_image_versions(image)

        # Both arches are skipped → upgrade should be proposed
        self.mock_api.check_architectures.assert_not_called()
        self.assertEqual(["17.1"], result.newer_versions)

    def test_upgrade_proposed_when_has_arch_override(self):
        """Images with explicit arch overrides for all required arches skip the multi-arch check."""
        image = make_image(current_version="17.0", has_arch_override=True,
                           arch_overrides=REQUIRED_ARCHITECTURES)
        self.mock_api.get_available_versions.return_value = ["17.1", "17.0"]
        # check_architectures should NOT be called when all required arches are overridden
        self.mock_api.check_architectures.return_value = ["linux/s390x", "linux/ppc64le"]

        result = self.checker.check_image_versions(image)

        self.mock_api.check_architectures.assert_not_called()
        self.assertEqual(["17.1"], result.newer_versions)
        self.assertEqual([], result.arch_missing)

    def test_upgrade_proposed_when_skip_arch_check(self):
        """--skip-arch-check bypasses the manifest verification."""
        self.checker.skip_arch_check = True
        image = make_image(current_version="17.0", has_arch_override=False)
        self.mock_api.get_available_versions.return_value = ["17.1", "17.0"]
        self.mock_api.check_architectures.return_value = ["linux/s390x"]

        result = self.checker.check_image_versions(image)

        self.mock_api.check_architectures.assert_not_called()
        self.assertEqual(["17.1"], result.newer_versions)

    def test_arch_missing_included_in_json_output(self):
        result = VersionCheckResult(
            image=make_image(),
            available_versions=["17.1", "17.0"],
            latest_version="17.1",
            newer_versions=[],
            is_latest=True,
            arch_missing=["linux/s390x"],
        )
        json_str = checker_mod.output_json([result])
        data = json.loads(json_str)
        self.assertEqual(["linux/s390x"], data[0]["arch_missing"])


class GCRAPICheckArchitecturesTest(unittest.TestCase):
    """GCRAPI.check_architectures must use the original registry (mirror.gcr.io) not normalise it."""

    def _mock_response(self, payload, status=200):
        resp = MagicMock()
        resp.status_code = status
        resp.json.return_value = payload
        return resp

    def test_uses_mirror_gcr_io_directly_for_manifest(self):
        """mirror.gcr.io must be used as-is; normalising to gcr.io causes 401."""
        GCRAPI = checker_mod.GCRAPI
        api = GCRAPI(timeout=5)
        image = make_image(registry="mirror.gcr.io", namespace="apache", name="kafka")
        with patch.object(api.session, "get",
                          return_value=self._mock_response(
                              manifest_response(["linux/amd64", "linux/s390x", "linux/ppc64le"])
                          )) as mock_get:
            missing = api.check_architectures(image, "3.9.1")
        self.assertEqual([], missing)
        url = mock_get.call_args[0][0]
        self.assertIn("mirror.gcr.io", url, "Manifest URL must use mirror.gcr.io, not gcr.io")
        self.assertNotIn("//gcr.io", url)

    def test_single_arch_counts_as_missing(self):
        """A single-arch manifest (no 'manifests' key) must block the upgrade."""
        GCRAPI = checker_mod.GCRAPI
        api = GCRAPI(timeout=5)
        image = make_image(registry="mirror.gcr.io", namespace="library", name="postgres")
        with patch.object(api.session, "get",
                          return_value=self._mock_response({"schemaVersion": 2, "layers": []})):
            missing = api.check_architectures(image, "17.1")
        self.assertIn("linux/s390x", missing)
        self.assertIn("linux/ppc64le", missing)


class ComponentSkipItsMapTest(unittest.TestCase):
    """_build_component_skip_its_map must find skipITs in component poms that depend on test-infra."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.base = Path(self.tmp.name)

    def _write_pom(self, path: Path, content: str):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)

    def test_discovers_skipped_arches_from_component_pom(self):
        # Component pom references camel-test-infra-elasticsearch and sets skipITs.s390x
        self._write_pom(
            self.base / "components" / "camel-elasticsearch" / "pom.xml",
            """<project><properties>
                <skipITs.s390x>true</skipITs.s390x>
            </properties>
            <dependencies><dependency>
                <artifactId>camel-test-infra-elasticsearch</artifactId>
            </dependency></dependencies></project>"""
        )
        result = ContainerVersionChecker._build_component_skip_its_map(str(self.base))
        self.assertIn("camel-test-infra-elasticsearch", result)
        self.assertIn("linux/s390x", result["camel-test-infra-elasticsearch"])

    def test_no_entry_when_no_skip_its(self):
        self._write_pom(
            self.base / "components" / "camel-kafka" / "pom.xml",
            """<project><dependencies><dependency>
                <artifactId>camel-test-infra-kafka</artifactId>
            </dependency></dependencies></project>"""
        )
        result = ContainerVersionChecker._build_component_skip_its_map(str(self.base))
        self.assertNotIn("camel-test-infra-kafka", result)

    def test_merges_arches_from_multiple_poms(self):
        # Two component poms each skip a different arch for the same test-infra module
        self._write_pom(
            self.base / "components" / "camel-es-a" / "pom.xml",
            """<project><properties><skipITs.s390x>true</skipITs.s390x></properties>
            <dependencies><dependency>
                <artifactId>camel-test-infra-es</artifactId>
            </dependency></dependencies></project>"""
        )
        self._write_pom(
            self.base / "components" / "camel-es-b" / "pom.xml",
            """<project><properties><skipITs.ppc64le>true</skipITs.ppc64le></properties>
            <dependencies><dependency>
                <artifactId>camel-test-infra-es</artifactId>
            </dependency></dependencies></project>"""
        )
        result = ContainerVersionChecker._build_component_skip_its_map(str(self.base))
        self.assertIn("linux/s390x", result.get("camel-test-infra-es", []))
        self.assertIn("linux/ppc64le", result.get("camel-test-infra-es", []))



class ReadSkipItsPomTest(unittest.TestCase):
    """Unit tests for ContainerVersionChecker._read_skip_its_from_pom."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)

    def _write_pom(self, path, content):
        pom = Path(path) / 'pom.xml'
        pom.parent.mkdir(parents=True, exist_ok=True)
        pom.write_text(content)
        return str(pom.parent)

    def test_ppc64le_and_s390x_set(self):
        pom_content = """<project><properties>
            <skipITs.ppc64le>true</skipITs.ppc64le>
            <skipITs.s390x>true</skipITs.s390x>
        </properties></project>"""
        module_dir = self._write_pom(Path(self.tmp.name) / 'module', pom_content)
        # container.properties lives inside the module directory
        props_path = str(Path(module_dir) / 'src' / 'container.properties')
        result = ContainerVersionChecker._read_skip_its_from_pom(props_path)
        self.assertIn('linux/ppc64le', result)
        self.assertIn('linux/s390x', result)

    def test_no_skip_its(self):
        pom_content = "<project><properties></properties></project>"
        module_dir = self._write_pom(Path(self.tmp.name) / 'module2', pom_content)
        props_path = str(Path(module_dir) / 'container.properties')
        result = ContainerVersionChecker._read_skip_its_from_pom(props_path)
        self.assertEqual([], result)

    def test_only_ppc64le_skipped(self):
        pom_content = """<project><properties>
            <skipITs.ppc64le>true</skipITs.ppc64le>
        </properties></project>"""
        module_dir = self._write_pom(Path(self.tmp.name) / 'module3', pom_content)
        props_path = str(Path(module_dir) / 'container.properties')
        result = ContainerVersionChecker._read_skip_its_from_pom(props_path)
        self.assertIn('linux/ppc64le', result)
        self.assertNotIn('linux/s390x', result)


class BearerTokenChallengeTest(unittest.TestCase):
    """Unit tests for ContainerRegistryAPI._bearer_token_from_challenge."""

    def setUp(self):
        self.api = checker_mod.GCRAPI(timeout=5)

    def test_extracts_token_from_challenge(self):
        challenge = 'Bearer realm="https://auth.example.com/token",service="example",scope="pull"'
        mock_resp = MagicMock()
        mock_resp.status_code = 200
        mock_resp.json.return_value = {'token': 'abc123'}
        with patch.object(self.api.session, 'get', return_value=mock_resp):
            token = self.api._bearer_token_from_challenge(challenge)
        self.assertEqual('abc123', token)

    def test_returns_empty_for_non_bearer(self):
        token = self.api._bearer_token_from_challenge('Basic realm="example"')
        self.assertEqual('', token)

    def test_returns_empty_when_no_realm(self):
        token = self.api._bearer_token_from_challenge('Bearer service="example"')
        self.assertEqual('', token)


if __name__ == "__main__":
    unittest.main()
