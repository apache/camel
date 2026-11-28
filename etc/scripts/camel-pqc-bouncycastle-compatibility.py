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

"""Run camel-pqc against the minimum, pinned and latest stable BC releases.

The pinned dependency in parent/pom.xml is never changed. Latest is resolved
from Maven Central metadata at execution time. The minimum tested baseline and the latest stable release are also
used as a compile/runtime interoperability matrix in both directions.
"""
from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
import tempfile
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MODULE = "components/camel-pqc"
MINIMUM_TESTED = "1.86"
METADATA_URL = (
    "https://repo.maven.apache.org/maven2/org/bouncycastle/"
    "bcprov-jdk18on/maven-metadata.xml"
)


def version_tuple(value: str) -> tuple[int, ...]:
    if not re.fullmatch(r"\d+(?:\.\d+){1,2}", value):
        raise ValueError(f"Not a stable numeric Bouncy Castle release: {value!r}")
    return tuple(int(part) for part in value.split("."))


def read_pinned_version() -> str:
    pom = ROOT / "parent/pom.xml"
    if not pom.is_file():
        raise RuntimeError(f"Cannot find {pom}; run this script from the Camel repository")
    document = ET.parse(pom).getroot()
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    node = document.find("m:properties/m:bouncycastle-version", namespace)
    if node is None or not (node.text or "").strip():
        raise RuntimeError("Could not read bouncycastle-version from parent/pom.xml")
    value = node.text.strip()
    version_tuple(value)
    return value


def read_latest_stable_version() -> str:
    request = urllib.request.Request(
        METADATA_URL,
        headers={"User-Agent": "Apache-Camel-camel-pqc-compatibility-check"},
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        metadata = ET.fromstring(response.read())
    release = metadata.findtext("versioning/release")
    if release:
        release = release.strip()
        try:
            version_tuple(release)
        except ValueError:
            # Maven metadata may point at a preview build; select the newest
            # numeric release below rather than treating a preview as stable.
            release = None
        if release:
            return release

    # Defensive fallback for repositories which omit <release> or publish a preview there.
    versions = [
        (version_tuple(node.text.strip()), node.text.strip())
        for node in metadata.findall("versioning/versions/version")
        if node.text and re.fullmatch(r"\d+(?:\.\d+){1,2}", node.text.strip())
    ]
    if not versions:
        raise RuntimeError("Maven Central metadata contains no stable bcprov-jdk18on release")
    return max(versions)[1]


def command_prefix() -> list[str]:
    wrapper = ROOT / "mvnw"
    if wrapper.is_file():
        if not os.access(wrapper, os.X_OK):
            raise RuntimeError(f"Maven wrapper is not executable: {wrapper}")
        return [str(wrapper)]
    return ["mvn"]


def run(command: list[str]) -> None:
    print("\n>>> " + " ".join(command), flush=True)
    subprocess.run(command, cwd=ROOT, check=True)


def mvn(version: str, *arguments: str) -> list[str]:
    return command_prefix() + [
        "-B", "-ntp", "-pl", MODULE, f"-Dbouncycastle-version={version}", *arguments
    ]


def bootstrap_dependencies() -> None:
    # Mirrors Camel's own quick dependency build used by its component-test workflow.
    command = command_prefix() + [
        "-B", "-ntp", "-DskipTests", "-Dquickly", "-pl", "buildingtools," + MODULE, "-am", "install"
    ]
    run(command)


def run_clean_suite(version: str) -> None:
    run(mvn(version, "clean", "test"))


def run_suite_on_compiled_tests(runtime_version: str) -> None:
    run(mvn(runtime_version, f"-Dcamel.pqc.expectedBcVersion={runtime_version}", "surefire:test"))


def export_vectors(compile_version: str, target: Path) -> None:
    run(mvn(
        compile_version,
        f"-Dcamel.pqc.expectedBcVersion={compile_version}",
        f"-Dcamel.pqc.interop.export={target}",
        "-Dtest=PQCBCVersionBehaviorTest",
        "surefire:test",
    ))


def import_vectors(runtime_version: str, producer_version: str, source: Path) -> None:
    arguments = [
        f"-Dcamel.pqc.expectedBcVersion={runtime_version}",
        f"-Dcamel.pqc.interop.import={source}",
    ]
    # HQC incompatibility is established specifically for BC 1.86 <-> 1.87.
    # Do not assume every future release has the same migration behavior.
    if {runtime_version, producer_version} == {"1.86", "1.87"}:
        arguments.append("-Dcamel.pqc.interop.expectHqcCompatible=false")
    run(mvn(runtime_version, *arguments, "-Dtest=PQCBCVersionBehaviorTest", "surefire:test"))


def run_micrometer_tests() -> None:
    command = command_prefix() + ["-B", "-ntp", "-pl", "components/camel-pqc-micrometer", "test"]
    run(command)


def run_interoperability_matrix(versions: list[str]) -> None:
    with tempfile.TemporaryDirectory(prefix="camel-pqc-bc-interop-") as temporary:
        directory = Path(temporary)
        for compile_version in versions:
            vectors = directory / f"bc-{compile_version}-vectors.properties"
            # Compile once for this BC API level, then execute that same bytecode
            # against every runtime version. This models platform BOM overrides,
            # including the Camel-pinned version when it is not the latest.
            run(mvn(compile_version, "clean", "test-compile"))
            export_vectors(compile_version, vectors)
            for runtime_version in versions:
                if runtime_version != compile_version:
                    import_vectors(runtime_version, compile_version, vectors)
                run_suite_on_compiled_tests(runtime_version)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--latest", help="override Maven Central latest stable version (for offline/reproducible runs)"
    )
    parser.add_argument(
        "--skip-latest", action="store_true",
        help="skip the moving Maven Central latest version (for pull-request CI); minimum and pinned versions still run",
    )
    parser.add_argument(
        "--skip-dependency-bootstrap", action="store_true",
        help="skip Camel's quick -am dependency install when those snapshots are already installed",
    )
    parser.add_argument(
        "--skip-interoperability", action="store_true",
        help="run same-runtime clean test for each version, but skip cross-version vector checks",
    )
    args = parser.parse_args()
    if args.skip_latest and args.latest:
        parser.error("--latest cannot be combined with --skip-latest")

    if not (ROOT / "pom.xml").is_file() or not (ROOT / f"{MODULE}/pom.xml").is_file():
        parser.error("run from the top-level Camel Git repository")

    minimum = MINIMUM_TESTED
    pinned = read_pinned_version()
    if version_tuple(pinned) < version_tuple(minimum):
        raise RuntimeError(f"Camel pinned version {pinned} is below tested baseline {minimum}")

    versions_to_check = {minimum, pinned}
    if args.skip_latest:
        latest_label = "not queried (pull-request run)"
    else:
        latest = args.latest or read_latest_stable_version()
        version_tuple(latest)
        if version_tuple(latest) < version_tuple(minimum):
            raise RuntimeError(f"Maven Central latest {latest} is below tested minimum {minimum}")
        versions_to_check.add(latest)
        latest_label = latest

    versions = sorted(versions_to_check, key=version_tuple)
    print(f"camel-pqc Bouncy Castle compatibility versions: {', '.join(versions)}", flush=True)
    print(f"Minimum tested: {minimum}; Camel pinned: {pinned}; Maven Central latest: {latest_label}", flush=True)

    if not args.skip_dependency_bootstrap:
        bootstrap_dependencies()
    run_micrometer_tests()
    run_micrometer_tests()

    if args.skip_interoperability:
        for version in versions:
            run_clean_suite(version)
    else:
        # Include every distinct version so the matrix explicitly covers the
        # Camel compile-time pin against platform-selected runtime versions.
        run_interoperability_matrix(versions)

    print("\nCAMEL-PQC BOUNCY CASTLE COMPATIBILITY CHECK PASSED", flush=True)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except subprocess.CalledProcessError as exc:
        print(f"Maven compatibility command failed with exit code {exc.returncode}", file=sys.stderr)
        raise SystemExit(exc.returncode or 1)
    except Exception as exc:
        print(f"Compatibility check failed: {exc}", file=sys.stderr)
        raise SystemExit(1)
