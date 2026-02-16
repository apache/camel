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


"""
Container Version Checker for Apache Camel Test Infrastructure

This script scans all container.properties files in the test-infra modules and checks
for newer versions of container images in their respective registries.

Supported Registries:
    - Docker Hub (docker.io, registry-1.docker.io)
    - Quay.io (quay.io)
    - Google Container Registry (gcr.io, mirror.gcr.io)
    - GitHub Container Registry (ghcr.io)
    - IBM Container Registry (icr.io)
    - Elastic Docker Registry (docker.elastic.co)
    - NVIDIA Container Registry (nvcr.io)
    - Microsoft Container Registry (mcr.microsoft.com)
    - Weaviate Container Registry (cr.weaviate.io)

Version Filtering:
    You can control which version tags are considered valid by adding whitelist/blacklist
    properties in your container.properties files:

    Whitelist (include): Only versions containing these words will be considered
        <property>.version.include=word1,word2,word3

    Blacklist (exclude): Versions containing these words will be excluded
        <property>.version.exclude=word1,word2,word3

    Examples:
        # Only accept versions with "alpine"
        postgres.container=postgres:17.2-alpine
        postgres.container.version.include=alpine

        # Exclude release candidates and beta versions
        kafka.container=quay.io/strimzi/kafka:latest-kafka-3.9.1
        kafka.container.version.exclude=rc,beta,alpha

        # Only numeric versions (no text suffixes)
        mysql.container=mysql:8.0.35
        mysql.container.version.exclude=alpine,slim,debian

    Major version freeze:
        Prevents major version jumps (e.g., 3.x → 4.x):
        <property>.version.freeze.major=true

        Example:
            kafka3.container=mirror.gcr.io/apache/kafka:3.9.1
            kafka3.container.version.freeze.major=true

    Structural pattern matching:
        Version tags are automatically filtered to match the same format as the
        current version. For example, if the current version is '17.5-alpine',
        only tags matching the pattern X.Y-alpine will be considered. This prevents
        non-version tags (branch names, base image tags) from being proposed.

    Notes:
        - Structural pattern matching is applied first (automatic, no config needed)
        - Filters are case-insensitive
        - Include filter: version must contain at least ONE of the words
        - Exclude filter: version must NOT contain ANY of the words
        - Exclude filters are checked first, then include filters
        - If no filters specified, all versions matching the structural pattern are considered

Usage:
    python3 check-container-versions.py [options]

Options:
    --verbose, -v        Enable verbose output
    --json              Output results in JSON format
    --check-prereleases  Include pre-release versions in checks
    --registry-timeout   Registry API timeout in seconds (default: 30)
    --help, -h          Show this help message

Dependencies:
    pip install requests packaging colorama
"""

import os
import re
import sys
import json
import argparse
import configparser
from pathlib import Path
from typing import Dict, List, Tuple, Optional, Any
from dataclasses import dataclass, asdict
from urllib.parse import urlparse
import requests
from packaging import version
from colorama import init, Fore, Style, Back
import time

# Initialize colorama for cross-platform colored output
init(autoreset=True)


def extract_numeric_segments(version_str: str) -> List[int]:
    """Extract all numeric segments from a version string for comparison.

    Examples:
        '17.5-alpine' → [17, 5]
        'v2.5.11' → [2, 5, 11]
        'latest-kafka-3.9.1' → [3, 9, 1]
        'RELEASE.2025-09-07T16-13-09Z-cpuv1' → [2025, 9, 7, 16, 13, 9, 1]
    """
    return [int(n) for n in re.findall(r'\d+', version_str)]


def compare_version_tuples(v1: str, v2: str) -> int:
    """Compare two version strings by their numeric segments.

    Returns positive if v1 > v2, negative if v1 < v2, 0 if equal.
    This avoids the broken string comparison fallback (e.g., "v1.9.7" > "v1.15.1").
    """
    nums1 = extract_numeric_segments(v1)
    nums2 = extract_numeric_segments(v2)

    if not nums1 or not nums2:
        return 0  # Can't compare

    # Compare component by component
    for n1, n2 in zip(nums1, nums2):
        if n1 != n2:
            return n1 - n2

    return len(nums1) - len(nums2)


def is_same_major_version(v1: str, v2: str) -> bool:
    """Check if two versions share the same major version (first numeric segment).

    Used with version.freeze.major to prevent major version jumps.
    """
    nums1 = extract_numeric_segments(v1)
    nums2 = extract_numeric_segments(v2)

    if not nums1 or not nums2:
        return True  # Can't determine, allow

    return nums1[0] == nums2[0]


def infer_version_pattern(current_version: str) -> Optional[re.Pattern]:
    """Infer a structural regex pattern from the current version tag.

    Replaces numeric segments with \\d+ patterns while keeping literal text
    segments intact. This ensures candidate versions match the same format
    as the current version.

    Examples:
        '17.5-alpine'       → '^\\d+\\.\\d+-alpine$'   (matches '18.0-alpine', not 'alpine3.23')
        'v2.5.11'           → '^v\\d+\\.\\d+\\.\\d+$'  (matches 'v2.6.0', not '2.5.12')
        'latest-kafka-3.9.1'→ '^latest\\-kafka\\-\\d+\\.\\d+\\.\\d+$'
        '0.12.0-cpu'        → '^\\d+\\.\\d+\\.\\d+\\-cpu$' (matches '0.13.0-cpu', not 'latest-gpu')
        '7.0.12-jammy'      → '^\\d+\\.\\d+\\.\\d+\\-jammy$'
    """
    if not current_version:
        return None

    parts = re.split(r'(\d+)', current_version)
    pattern_parts = []
    for part in parts:
        if part == '':
            continue
        if re.match(r'^\d+$', part):
            pattern_parts.append(r'\d+')
        else:
            pattern_parts.append(re.escape(part))

    pattern = '^' + ''.join(pattern_parts) + '$'
    try:
        return re.compile(pattern)
    except re.error:
        return None


@dataclass
class ContainerImage:
    """Represents a container image with its registry, name, and version."""
    registry: str
    namespace: str
    name: str
    current_version: str
    property_name: str
    file_path: str
    version_include: List[str] = None  # Whitelist: version must contain one of these words
    version_exclude: List[str] = None  # Blacklist: version must not contain any of these words
    version_freeze_major: bool = False  # Lock to same major version when True

    def __post_init__(self):
        """Initialize default values for optional fields."""
        if self.version_include is None:
            self.version_include = []
        if self.version_exclude is None:
            self.version_exclude = []
        # Infer structural pattern from current version (not a dataclass field, excluded from asdict)
        self._version_pattern = infer_version_pattern(self.current_version)

    @property
    def full_name(self) -> str:
        """Returns the full image name without version."""
        if self.namespace:
            return f"{self.registry}/{self.namespace}/{self.name}"
        else:
            return f"{self.registry}/{self.name}"

    @property
    def full_image(self) -> str:
        """Returns the complete image reference with version."""
        return f"{self.full_name}:{self.current_version}"

    def is_version_allowed(self, version_tag: str) -> bool:
        """Check if a version matches structural pattern and whitelist/blacklist criteria.

        Checks are applied in order:
        1. Structural pattern: version must match the same format as current version
        2. Blacklist (exclude): version must NOT contain any excluded words
        3. Whitelist (include): version must contain at least one included word
        4. Major version freeze: version must share the same first numeric segment
        """
        # Check structural pattern first - this prevents non-version tags
        # like branch names, base image tags, etc.
        if self._version_pattern and not self._version_pattern.match(version_tag):
            return False

        version_lower = version_tag.lower()

        # Check blacklist (exclusions)
        if self.version_exclude:
            for exclude_word in self.version_exclude:
                if exclude_word.lower() in version_lower:
                    return False

        # Check whitelist (inclusions)
        if self.version_include:
            # If whitelist is specified, version must contain at least one of the words
            matched = any(include_word.lower() in version_lower
                         for include_word in self.version_include)
            if not matched:
                return False

        # Check major version freeze
        if self.version_freeze_major:
            if not is_same_major_version(self.current_version, version_tag):
                return False

        # Version passed all checks
        return True

@dataclass
class VersionCheckResult:
    """Result of checking for newer versions of a container image."""
    image: ContainerImage
    available_versions: List[str]
    latest_version: Optional[str]
    newer_versions: List[str]
    is_latest: bool
    error: Optional[str] = None

class ContainerRegistryAPI:
    """Base class for container registry API interactions."""

    def __init__(self, timeout: int = 30):
        self.timeout = timeout
        self.session = requests.Session()
        self.session.headers.update({
            'User-Agent': 'Apache-Camel-Test-Infra-Version-Checker/1.0'
        })

    def get_available_versions(self, image: ContainerImage) -> List[str]:
        """Get available versions for the given image."""
        raise NotImplementedError

    def normalize_registry_url(self, registry: str) -> str:
        """Normalize registry URL for API calls."""
        if registry == "mirror.gcr.io":
            return "gcr.io"
        elif registry == "docker.io" or registry == "":
            return "registry-1.docker.io"
        return registry

class DockerHubAPI(ContainerRegistryAPI):
    """Docker Hub registry API implementation."""

    def get_available_versions(self, image: ContainerImage) -> List[str]:
        """Get available versions from Docker Hub."""
        try:
            # Handle official images (no namespace)
            if not image.namespace or image.namespace == "library":
                repo_name = image.name
            else:
                repo_name = f"{image.namespace}/{image.name}"

            url = f"https://registry.hub.docker.com/v2/repositories/{repo_name}/tags/"

            versions = []
            page = 1
            max_pages = 20  # Prevent infinite loops

            while page <= max_pages:
                params = {'page': page, 'page_size': 100}
                response = self.session.get(url, params=params, timeout=self.timeout)

                if response.status_code != 200:
                    if response.status_code == 404:
                        return []  # Repository not found
                    response.raise_for_status()

                data = response.json()

                for tag_info in data.get('results', []):
                    tag_name = tag_info.get('name', '')
                    if tag_name and tag_name != 'latest':
                        versions.append(tag_name)

                # Check if there are more pages
                if not data.get('next'):
                    break

                page += 1

            return versions

        except Exception as e:
            raise Exception(f"Docker Hub API error: {str(e)}")

class QuayAPI(ContainerRegistryAPI):
    """Quay.io registry API implementation."""

    def get_available_versions(self, image: ContainerImage) -> List[str]:
        """Get available versions from Quay.io."""
        try:
            repo_name = f"{image.namespace}/{image.name}"
            url = f"https://quay.io/api/v1/repository/{repo_name}/tag/"

            params = {'limit': 100, 'page': 1}
            versions = []

            while True:
                response = self.session.get(url, params=params, timeout=self.timeout)

                if response.status_code != 200:
                    if response.status_code == 404:
                        return []
                    response.raise_for_status()

                data = response.json()

                for tag_info in data.get('tags', []):
                    tag_name = tag_info.get('name', '')
                    if tag_name and tag_name != 'latest':
                        versions.append(tag_name)

                # Check if there are more pages
                if not data.get('has_additional', False):
                    break

                params['page'] += 1
                if params['page'] > 20:  # Prevent infinite loops
                    break

            return versions

        except Exception as e:
            raise Exception(f"Quay.io API error: {str(e)}")

class GCRAPI(ContainerRegistryAPI):
    """Google Container Registry API implementation."""

    def get_available_versions(self, image: ContainerImage) -> List[str]:
        """Get available versions from GCR."""
        try:
            # GCR uses Docker Registry v2 API
            if image.namespace:
                repo_name = f"{image.namespace}/{image.name}"
            else:
                repo_name = image.name

            url = f"https://{image.registry}/v2/{repo_name}/tags/list"

            response = self.session.get(url, timeout=self.timeout)

            if response.status_code != 200:
                if response.status_code == 404:
                    return []
                response.raise_for_status()

            data = response.json()
            versions = data.get('tags', [])

            # Filter out 'latest' tag
            return [v for v in versions if v != 'latest']

        except Exception as e:
            raise Exception(f"GCR API error: {str(e)}")

class GHCRAPI(ContainerRegistryAPI):
    """GitHub Container Registry API implementation."""

    def get_available_versions(self, image: ContainerImage) -> List[str]:
        """Get available versions from GHCR (GitHub Container Registry)."""
        try:
            # GHCR uses OCI Distribution API (Docker Registry v2 compatible)
            if image.namespace:
                repo_name = f"{image.namespace}/{image.name}"
            else:
                repo_name = image.name

            url = f"https://{image.registry}/v2/{repo_name}/tags/list"

            # GHCR may require authentication for some repos, but we'll try anonymous first
            response = self.session.get(url, timeout=self.timeout)

            if response.status_code != 200:
                if response.status_code == 404:
                    return []
                response.raise_for_status()

            data = response.json()
            versions = data.get('tags', [])

            # Filter out 'latest' tag
            return [v for v in versions if v != 'latest']

        except Exception as e:
            raise Exception(f"GHCR API error: {str(e)}")

class DockerV2RegistryAPI(ContainerRegistryAPI):
    """Generic Docker Registry v2 API implementation for various registries."""

    def __init__(self, timeout: int = 30, registry_name: str = "Docker Registry"):
        super().__init__(timeout)
        self.registry_name = registry_name

    def get_available_versions(self, image: ContainerImage) -> List[str]:
        """Get available versions from a Docker Registry v2 compatible registry."""
        try:
            # Build repository name
            if image.namespace:
                repo_name = f"{image.namespace}/{image.name}"
            else:
                repo_name = image.name

            url = f"https://{image.registry}/v2/{repo_name}/tags/list"

            response = self.session.get(url, timeout=self.timeout)

            if response.status_code != 200:
                if response.status_code == 404:
                    return []
                response.raise_for_status()

            data = response.json()
            versions = data.get('tags', [])

            # Filter out 'latest' tag
            return [v for v in versions if v != 'latest']

        except Exception as e:
            raise Exception(f"{self.registry_name} API error: {str(e)}")

class ElasticRegistryAPI(ContainerRegistryAPI):
    """Elastic Docker Registry API implementation."""

    def get_available_versions(self, image: ContainerImage) -> List[str]:
        """Get available versions from Elastic's Docker Registry."""
        try:
            # Elastic uses a standard Docker Registry v2 API
            if image.namespace:
                repo_name = f"{image.namespace}/{image.name}"
            else:
                repo_name = image.name

            url = f"https://{image.registry}/v2/{repo_name}/tags/list"

            response = self.session.get(url, timeout=self.timeout)

            if response.status_code != 200:
                if response.status_code == 404:
                    return []
                response.raise_for_status()

            data = response.json()
            versions = data.get('tags', [])

            # Filter out 'latest' tag
            return [v for v in versions if v != 'latest']

        except Exception as e:
            raise Exception(f"Elastic Registry API error: {str(e)}")

class NVIDIARegistryAPI(ContainerRegistryAPI):
    """NVIDIA Container Registry API implementation."""

    def get_available_versions(self, image: ContainerImage) -> List[str]:
        """Get available versions from NVIDIA Container Registry."""
        try:
            # NVIDIA NGC uses a Docker Registry v2 compatible API
            if image.namespace:
                repo_name = f"{image.namespace}/{image.name}"
            else:
                repo_name = image.name

            url = f"https://{image.registry}/v2/{repo_name}/tags/list"

            response = self.session.get(url, timeout=self.timeout)

            if response.status_code != 200:
                if response.status_code == 404:
                    return []
                response.raise_for_status()

            data = response.json()
            versions = data.get('tags', [])

            # Filter out 'latest' tag
            return [v for v in versions if v != 'latest']

        except Exception as e:
            raise Exception(f"NVIDIA Registry API error: {str(e)}")

class MicrosoftRegistryAPI(ContainerRegistryAPI):
    """Microsoft Container Registry API implementation."""

    def get_available_versions(self, image: ContainerImage) -> List[str]:
        """Get available versions from Microsoft Container Registry."""
        try:
            # MCR uses Docker Registry v2 API
            if image.namespace:
                repo_name = f"{image.namespace}/{image.name}"
            else:
                repo_name = image.name

            url = f"https://{image.registry}/v2/{repo_name}/tags/list"

            response = self.session.get(url, timeout=self.timeout)

            if response.status_code != 200:
                if response.status_code == 404:
                    return []
                response.raise_for_status()

            data = response.json()
            versions = data.get('tags', [])

            # Filter out 'latest' tag
            return [v for v in versions if v != 'latest']

        except Exception as e:
            raise Exception(f"Microsoft Registry API error: {str(e)}")

class ContainerVersionChecker:
    """Main class for checking container versions."""

    # Pre-release indicators for version tags
    PRERELEASE_INDICATORS = ['alpha', 'beta', 'rc', 'dev', 'snapshot', 'preview', 'nightly', 'canary']

    def __init__(self,
                 include_prereleases: bool = False,
                 registry_timeout: int = 30,
                 verbose: bool = False):
        self.include_prereleases = include_prereleases
        self.verbose = verbose

        # Initialize registry APIs
        self.registry_apis = {
            'docker.io': DockerHubAPI(registry_timeout),
            'registry-1.docker.io': DockerHubAPI(registry_timeout),
            'quay.io': QuayAPI(registry_timeout),
            'gcr.io': GCRAPI(registry_timeout),
            'mirror.gcr.io': GCRAPI(registry_timeout),
            'ghcr.io': GHCRAPI(registry_timeout),
            'icr.io': DockerV2RegistryAPI(registry_timeout, "IBM Container Registry"),
            'docker.elastic.co': ElasticRegistryAPI(registry_timeout),
            'nvcr.io': NVIDIARegistryAPI(registry_timeout),
            'mcr.microsoft.com': MicrosoftRegistryAPI(registry_timeout),
            'cr.weaviate.io': DockerV2RegistryAPI(registry_timeout, "Weaviate Container Registry"),
        }

    def _is_prerelease(self, ver: str) -> bool:
        """Check if a version tag appears to be a pre-release."""
        lower = ver.lower()
        return any(indicator in lower for indicator in self.PRERELEASE_INDICATORS)

    def parse_container_reference(self, container_ref: str) -> Tuple[str, str, str, str]:
        """Parse container reference into registry, namespace, name, and version."""
        # Handle cases like:
        # - postgres:17.5-alpine
        # - mirror.gcr.io/postgres:17.5-alpine
        # - quay.io/strimzi/kafka:latest-kafka-3.9.1
        # - mirror.gcr.io/confluentinc/cp-kafka:7.9.2

        if '://' in container_ref:
            # Remove protocol if present
            container_ref = container_ref.split('://', 1)[1]

        # Split by ':'
        parts = container_ref.rsplit(':', 1)
        if len(parts) != 2:
            raise ValueError(f"Invalid container reference: {container_ref}")

        image_part, version_part = parts

        # Split image part by '/'
        image_components = image_part.split('/')

        if len(image_components) == 1:
            # No registry specified, assume Docker Hub
            registry = "docker.io"
            namespace = ""
            name = image_components[0]
        elif len(image_components) == 2:
            # Could be registry/image or namespace/image
            if '.' in image_components[0] or image_components[0] in ['localhost', 'mirror']:
                # Looks like a registry
                registry = image_components[0]
                namespace = ""
                name = image_components[1]
            else:
                # Looks like namespace/image on Docker Hub
                registry = "docker.io"
                namespace = image_components[0]
                name = image_components[1]
        elif len(image_components) == 3:
            # registry/namespace/image
            registry = image_components[0]
            namespace = image_components[1]
            name = image_components[2]
        else:
            # More complex case, treat everything except last as registry/namespace
            registry = image_components[0]
            namespace = '/'.join(image_components[1:-1])
            name = image_components[-1]

        return registry, namespace, name, version_part

    def find_container_properties_files(self, base_path: str) -> List[str]:
        """Find all container.properties files in the specified directory."""
        properties_files = []
        base_path = Path(base_path)

        if self.verbose:
            print(f"  Searching in: {base_path}")

        # Directories to exclude from search
        exclude_dirs = {'target', 'build', '.git', 'node_modules', '__pycache__'}

        # Look for container.properties files anywhere in the directory tree
        for properties_file in base_path.rglob("container.properties"):
            # Skip files in excluded directories
            if any(excluded in properties_file.parts for excluded in exclude_dirs):
                if self.verbose:
                    print(f"  Skipped (in excluded dir): {properties_file}")
                continue

            properties_files.append(str(properties_file))
            if self.verbose:
                print(f"  Found: {properties_file}")

        # If no files found, also check for any .properties files that might contain container references
        # If no container.properties files found, search for other patterns
        if not properties_files:
            if self.verbose:
                print("  No container.properties files found, searching for alternatives...")

            # Check for other common container property file patterns
            search_patterns = [
                "docker.properties",
                "containers.properties",
                "images.properties",
                "testcontainers.properties"
            ]

            for pattern in search_patterns:
                for properties_file in base_path.rglob(pattern):
                    # Skip files in excluded directories
                    if any(excluded in properties_file.parts for excluded in exclude_dirs):
                        continue
                    properties_files.append(str(properties_file))
                    if self.verbose:
                        print(f"  Found by pattern {pattern}: {properties_file}")

            # If still nothing found, scan all .properties files for container references
            if not properties_files:
                if self.verbose:
                    print("  No specific container property files found, scanning all .properties files...")

                for properties_file in base_path.rglob("*.properties"):
                    # Skip files in excluded directories
                    if any(excluded in properties_file.parts for excluded in exclude_dirs):
                        continue

                    # Skip very large files to avoid performance issues
                    try:
                        file_size = properties_file.stat().st_size
                        if file_size > 1024 * 1024:  # Skip files larger than 1MB
                            continue

                        with open(properties_file, 'r', encoding='utf-8') as f:
                            content = f.read()
                            # Look for patterns that suggest container definitions
                            if any(pattern in content.lower() for pattern in [
                                '.container=', 'container.image=', '.image=',
                                'docker.io', 'gcr.io', 'quay.io', 'ghcr.io', 'icr.io',
                                'mcr.microsoft.com', 'nvcr.io', 'docker.elastic.co',
                                'cr.weaviate.io', 'localhost:', 'registry'
                            ]):
                                properties_files.append(str(properties_file))
                                if self.verbose:
                                    print(f"  Found container references in: {properties_file}")
                    except (UnicodeDecodeError, IOError, OSError):
                        # Skip files that can't be read
                        continue

        return sorted(properties_files)

    def parse_properties_file(self, file_path: str) -> List[ContainerImage]:
        """Parse a container.properties file and extract container images."""
        images = []

        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                content = f.read()

            # First pass: collect all properties
            properties = {}
            for line_num, line in enumerate(content.splitlines(), 1):
                line = line.strip()

                # Skip comments and empty lines
                if not line or line.startswith('#') or line.startswith('##'):
                    continue

                # Look for property=value format
                if '=' in line:
                    key, value = line.split('=', 1)
                    key = key.strip()
                    value = value.strip()

                    # Skip if value looks like a property reference
                    if not value or value.startswith('${'):
                        continue

                    properties[key] = value

            # Second pass: build ContainerImage objects
            processed_keys = set()

            for key, value in properties.items():
                # Skip if already processed or if it's a version filter/config property
                if key in processed_keys or '.version.include' in key or '.version.exclude' in key or '.version.freeze' in key:
                    continue

                try:
                    registry, namespace, name, current_version = self.parse_container_reference(value)

                    # Check for version filters
                    version_include = []
                    version_exclude = []

                    # Look for .version.include property
                    include_key = f"{key}.version.include"
                    if include_key in properties:
                        version_include = [word.strip() for word in properties[include_key].split(',') if word.strip()]

                    # Look for .version.exclude property
                    exclude_key = f"{key}.version.exclude"
                    if exclude_key in properties:
                        version_exclude = [word.strip() for word in properties[exclude_key].split(',') if word.strip()]

                    # Look for .version.freeze.major property
                    freeze_major_key = f"{key}.version.freeze.major"
                    version_freeze_major = False
                    if freeze_major_key in properties:
                        version_freeze_major = properties[freeze_major_key].lower() in ['true', 'yes', '1']

                    image = ContainerImage(
                        registry=registry,
                        namespace=namespace,
                        name=name,
                        current_version=current_version,
                        property_name=key,
                        file_path=file_path,
                        version_include=version_include,
                        version_exclude=version_exclude,
                        version_freeze_major=version_freeze_major
                    )
                    images.append(image)
                    processed_keys.add(key)

                    if self.verbose:
                        print(f"  Found: {key} = {value}")
                        if version_include:
                            print(f"    Include filter: {', '.join(version_include)}")
                        if version_exclude:
                            print(f"    Exclude filter: {', '.join(version_exclude)}")
                        if version_freeze_major:
                            print(f"    Major version freeze: enabled")

                except ValueError as e:
                    if self.verbose:
                        print(f"  Warning: Could not parse {key}={value}: {e}")
                    continue

        except Exception as e:
            print(f"Error reading {file_path}: {e}")
            return []

        return images

    def check_image_versions(self, image: ContainerImage) -> VersionCheckResult:
        """Check for newer versions of a container image."""
        try:
            # Get the appropriate API for the registry
            registry_key = image.registry.lower()

            # Handle registry aliases and special cases
            if registry_key == "mirror.gcr.io":
                registry_key = "gcr.io"
            elif registry_key in ["", "docker.io"]:
                registry_key = "docker.io"
            elif registry_key == "registry-1.docker.io":
                registry_key = "docker.io"

            api = self.registry_apis.get(registry_key)
            if not api:
                return VersionCheckResult(
                    image=image,
                    available_versions=[],
                    latest_version=None,
                    newer_versions=[],
                    is_latest=False,
                    error=f"Unsupported registry: {image.registry}"
                )

            if self.verbose:
                print(f"    Checking {image.full_image}...")

            # Get available versions
            available_versions = api.get_available_versions(image)

            if not available_versions:
                return VersionCheckResult(
                    image=image,
                    available_versions=[],
                    latest_version=None,
                    newer_versions=[],
                    is_latest=True,
                    error="No versions found in registry"
                )

            # Apply whitelist/blacklist filters
            filtered_versions = [v for v in available_versions if image.is_version_allowed(v)]

            if not filtered_versions:
                return VersionCheckResult(
                    image=image,
                    available_versions=[],
                    latest_version=None,
                    newer_versions=[],
                    is_latest=True,
                    error="No versions match the include/exclude filters"
                )

            if self.verbose and len(filtered_versions) < len(available_versions):
                excluded_count = len(available_versions) - len(filtered_versions)
                print(f"      Filtered out {excluded_count} versions based on include/exclude rules")

            # Sort versions using numeric segment comparison (avoids packaging.version failures)
            def version_sort_key(v):
                nums = extract_numeric_segments(v)
                if nums:
                    # Pad to 20 segments for consistent tuple comparison
                    return tuple(nums + [0] * (20 - len(nums)))
                return (0,) * 20

            sorted_versions = sorted(filtered_versions, key=version_sort_key, reverse=True)

            # Find newer versions using numeric segment comparison
            newer_versions = []
            current_ver = image.current_version

            for ver in sorted_versions:
                if compare_version_tuples(ver, current_ver) > 0:
                    if self.include_prereleases or not self._is_prerelease(ver):
                        newer_versions.append(ver)

            latest_version = sorted_versions[0] if sorted_versions else None
            is_latest = current_ver == latest_version or len(newer_versions) == 0

            return VersionCheckResult(
                image=image,
                available_versions=sorted_versions[:10],  # Limit to top 10
                latest_version=latest_version,
                newer_versions=newer_versions[:5],  # Limit to top 5 newer
                is_latest=is_latest
            )

        except Exception as e:
            return VersionCheckResult(
                image=image,
                available_versions=[],
                latest_version=None,
                newer_versions=[],
                is_latest=False,
                error=str(e)
            )

    def run_check(self, scan_path: str, quiet: bool = False) -> List[VersionCheckResult]:
        """Run the version check on all container.properties files."""
        if not quiet:
            print(f"🔍 Scanning for container.properties files in {scan_path}...")

        properties_files = self.find_container_properties_files(scan_path)

        if not properties_files:
            if not quiet:
                print("❌ No container.properties files found!")
            return []

        if not quiet:
            print(f"📁 Found {len(properties_files)} container.properties files")

        all_images = []

        # Parse all properties files
        for file_path in properties_files:
            if self.verbose and not quiet:
                print(f"\n📄 Parsing {file_path}...")

            images = self.parse_properties_file(file_path)
            all_images.extend(images)

        if not all_images:
            if not quiet:
                print("❌ No container images found in properties files!")
            return []

        if not quiet:
            print(f"🐳 Found {len(all_images)} container images")
            print("🌐 Checking for newer versions...")

        # Check versions for all images
        results = []
        for i, image in enumerate(all_images, 1):
            if not quiet:
                print(f"  [{i}/{len(all_images)}] {image.full_image}")
            result = self.check_image_versions(image)
            results.append(result)
            time.sleep(0.1)  # Be nice to APIs

        return results

def print_registry_summary(results: List[VersionCheckResult]):
    """Print a summary of registries used."""
    registry_counts = {}
    for result in results:
        registry = result.image.registry
        if registry in registry_counts:
            registry_counts[registry] += 1
        else:
            registry_counts[registry] = 1

    if registry_counts:
        print(f"\n{Style.BRIGHT}🌐 REGISTRY USAGE SUMMARY{Style.RESET_ALL}")
        print("-" * 35)
        for registry, count in sorted(registry_counts.items(), key=lambda x: x[1], reverse=True):
            print(f"  {Fore.CYAN}{registry:<25}{Style.RESET_ALL} {count:>3} images")

def print_results(results: List[VersionCheckResult], verbose: bool = False):
    """Print the results in a human-readable format."""

    # Separate results into categories
    outdated = [r for r in results if not r.is_latest and not r.error and r.newer_versions]
    up_to_date = [r for r in results if r.is_latest and not r.error]
    errors = [r for r in results if r.error]

    print(f"\n{Style.BRIGHT}📊 VERSION CHECK SUMMARY{Style.RESET_ALL}")
    print("=" * 50)

    print(f"Total images checked: {len(results)}")
    print(f"{Fore.GREEN}✅ Up to date: {len(up_to_date)}{Style.RESET_ALL}")
    print(f"{Fore.YELLOW}⚠️  Outdated: {len(outdated)}{Style.RESET_ALL}")
    print(f"{Fore.RED}❌ Errors: {len(errors)}{Style.RESET_ALL}")

    if outdated:
        print(f"\n{Style.BRIGHT}{Fore.YELLOW}📦 OUTDATED IMAGES{Style.RESET_ALL}")
        print("-" * 30)

        for result in sorted(outdated, key=lambda x: len(x.newer_versions), reverse=True):
            image = result.image
            print(f"\n{Fore.CYAN}{image.property_name}{Style.RESET_ALL}")
            print(f"  File: {os.path.relpath(image.file_path)}")
            print(f"  Current: {Fore.YELLOW}{image.current_version}{Style.RESET_ALL}")
            print(f"  Latest:  {Fore.GREEN}{result.latest_version}{Style.RESET_ALL}")

            if len(result.newer_versions) > 1:
                newer_display = result.newer_versions[:3]
                if len(result.newer_versions) > 3:
                    newer_display.append(f"... (+{len(result.newer_versions) - 3} more)")
                print(f"  Newer:   {Fore.GREEN}{', '.join(newer_display)}{Style.RESET_ALL}")

    if errors:
        print(f"\n{Style.BRIGHT}{Fore.RED}❌ ERRORS{Style.RESET_ALL}")
        print("-" * 15)

        for result in errors:
            image = result.image
            print(f"\n{Fore.CYAN}{image.property_name}{Style.RESET_ALL}")
            print(f"  Image: {image.full_image}")
            print(f"  Error: {Fore.RED}{result.error}{Style.RESET_ALL}")

    if verbose and up_to_date:
        print(f"\n{Style.BRIGHT}{Fore.GREEN}✅ UP TO DATE IMAGES{Style.RESET_ALL}")
        print("-" * 25)

        for result in up_to_date:
            image = result.image
            print(f"  {image.property_name}: {image.current_version}")

def output_json(results: List[VersionCheckResult]) -> str:
    """Output results in JSON format."""
    json_results = []

    for result in results:
        json_result = {
            'image': asdict(result.image),
            'available_versions': result.available_versions,
            'latest_version': result.latest_version,
            'newer_versions': result.newer_versions,
            'is_latest': result.is_latest,
            'error': result.error
        }
        json_results.append(json_result)

    return json.dumps(json_results, indent=2)

def main():
    parser = argparse.ArgumentParser(
        description='Check for newer versions of container images in Apache Camel test-infra',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
    # Basic usage
    python3 check-container-versions.py

    # Verbose output
    python3 check-container-versions.py --verbose

    # JSON output for automation
    python3 check-container-versions.py --json

    # Include pre-release versions
    python3 check-container-versions.py --check-prereleases
        """
    )

    parser.add_argument(
        '--verbose', '-v',
        action='store_true',
        help='Enable verbose output'
    )

    parser.add_argument(
        '--json',
        action='store_true',
        help='Output results in JSON format'
    )

    parser.add_argument(
        '--check-prereleases',
        action='store_true',
        help='Include pre-release versions in checks'
    )

    parser.add_argument(
        '--registry-timeout',
        type=int,
        default=30,
        help='Registry API timeout in seconds (default: 30)'
    )

    parser.add_argument(
        '--test-infra-path',
        type=str,
        default=None,
        help='Path to test-infra directory or any directory containing container.properties files (default: current directory)'
    )

    parser.add_argument(
        '--scan-path',
        type=str,
        default=None,
        help='Alias for --test-infra-path (scan any directory for container.properties files)'
    )

    args = parser.parse_args()

    # Determine scan path (support both --test-infra-path and --scan-path)
    scan_path = args.test_infra_path or args.scan_path

    if scan_path:
        # Use specified path (convert to absolute path)
        scan_path = os.path.abspath(os.path.expanduser(scan_path))
        if not os.path.exists(scan_path):
            print(f"❌ Specified path does not exist: {scan_path}")
            sys.exit(1)
        if not os.path.isdir(scan_path):
            print(f"❌ Specified path is not a directory: {scan_path}")
            sys.exit(1)
    else:
        # Use current working directory
        scan_path = os.getcwd()

    # Convert to absolute path for consistent behavior
    scan_path = os.path.abspath(scan_path)

    if not args.json:
        print(f"{Style.BRIGHT}🐳 Apache Camel Test Infrastructure Container Version Checker{Style.RESET_ALL}")
        print("=" * 60)

    # Create checker and run
    checker = ContainerVersionChecker(
        include_prereleases=args.check_prereleases,
        registry_timeout=args.registry_timeout,
        verbose=args.verbose
    )

    try:
        # Use quiet mode when outputting JSON to avoid non-JSON output
        results = checker.run_check(scan_path, quiet=args.json)

        if args.json:
            print(output_json(results))
        else:
            print_results(results, args.verbose)

            # Show registry usage summary if verbose
            if args.verbose:
                print_registry_summary(results)

        # Exit with non-zero if there are outdated images (regardless of output mode)
        outdated_count = len([r for r in results if not r.is_latest and not r.error and r.newer_versions])
        if outdated_count > 0:
            if not args.json:
                print(f"\n💡 Found {outdated_count} outdated images. Consider updating!")
            sys.exit(1)
        else:
            if not args.json:
                print(f"\n🎉 All images are up to date!")

    except KeyboardInterrupt:
        print(f"\n{Fore.YELLOW}⚠️  Interrupted by user{Style.RESET_ALL}")
        sys.exit(130)
    except Exception as e:
        if args.json:
            print(json.dumps({"error": str(e)}, indent=2))
        else:
            print(f"{Fore.RED}❌ Error: {e}{Style.RESET_ALL}")
        sys.exit(1)

if __name__ == "__main__":
    main()