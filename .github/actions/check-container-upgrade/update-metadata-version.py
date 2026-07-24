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
Update serviceVersion in test-infra metadata.json files when a container
image version is bumped. Produces output matching Jackson's DefaultPrettyPrinter
format so the result is identical to what the Maven build generates.

Entries are matched the same way CamelTestInfraGenerateMetadataMojo derives
serviceVersion: the property key prefix (the part before ".container") is
matched against the entry aliasImplementation/alias values. Matching on the
Maven module name does not work, because a container.properties file may be
shared across modules — azure.container lives in camel-test-infra-azure-common
while the metadata entries belong to camel-test-infra-azure-storage-blob and
camel-test-infra-azure-storage-queue.

Some bumps legitimately have no metadata counterpart, and are reported as a
no-op rather than an error:
  * platform-specific keys (.ppc64le/.s390x/.aarch64/.amd64), which the Mojo skips
  * multi-container modules whose entries have no serviceVersion (observability)
  * modules with no @InfraService entry at all (triton, tensorflow-serving, ...)

Usage:
    python3 update-metadata-version.py <property-name> <old-version> <new-version> <metadata-file> [<metadata-file2> ...]
"""

import json
import os
import sys

# Suffixes the metadata Mojo skips: platform-specific image variants never
# reach metadata.json, so a bump of those keys has no serviceVersion to update.
PLATFORM_SUFFIXES = (".ppc64le", ".s390x", ".aarch64", ".amd64")

# Keys carrying version-check metadata rather than an image reference.
METADATA_MARKERS = (".version.exclude", ".version.include", ".version.freeze")


def jackson_format(data):
    """Serialize JSON to match Jackson's DefaultPrettyPrinter output."""
    lines = []
    for i, entry in enumerate(data):
        lines.append("[ {" if i == 0 else "}, {")
        keys = list(entry.keys())
        for j, key in enumerate(keys):
            value = entry[key]
            if isinstance(value, list):
                if not value:
                    formatted = "[ ]"
                else:
                    items = ", ".join(json.dumps(v, ensure_ascii=False) for v in value)
                    formatted = f"[ {items} ]"
            else:
                formatted = json.dumps(value, ensure_ascii=False)
            comma = "," if j < len(keys) - 1 else ""
            lines.append(f"  {json.dumps(key, ensure_ascii=False)} : {formatted}{comma}")
    lines.append("} ]")
    return "\n".join(lines)


def normalize(value):
    """Normalize an alias or property prefix for matching, as the Mojo does."""
    return value.replace("-", "").replace(".", "").lower()


def property_prefix(property_name):
    """
    Return the normalized prefix of a container property key, or None when the
    key holds no image version the metadata could ever carry.
    """
    key = property_name.lower()

    if key.endswith(PLATFORM_SUFFIXES) or key.endswith(".version"):
        return None
    if any(marker in key for marker in METADATA_MARKERS):
        return None
    if "container" not in key:
        return None

    index = key.find(".container")
    if index <= 0:
        return None

    return normalize(key[:index])


def matches(entry, prefix):
    """
    Check whether the metadata entry is the one the property key feeds: the
    prefix must equal, or end with, one of the entry aliases — the suffix match
    covers compound keys such as hivemq.sparkplug.container.
    """
    aliases = list(entry.get("aliasImplementation") or [])
    aliases.extend(entry.get("alias") or [])

    for alias in aliases:
        normalized = normalize(alias)
        if normalized and prefix.endswith(normalized):
            return True

    return False


def update_metadata(prefix, old_version, new_version, metadata_file):
    """Update serviceVersion for entries fed by the bumped property."""
    if not os.path.isfile(metadata_file):
        print(f"⚠️  {metadata_file} not found, skipping")
        return False

    with open(metadata_file, "r", encoding="utf-8") as f:
        data = json.load(f)

    updated = False
    for entry in data:
        if entry.get("serviceVersion") == old_version and matches(entry, prefix):
            entry["serviceVersion"] = new_version
            updated = True

    if updated:
        with open(metadata_file, "w", encoding="utf-8") as f:
            f.write(jackson_format(data))
        print(f"✅ Updated serviceVersion {old_version} → {new_version} in {metadata_file}")

    return updated


def main():
    if len(sys.argv) < 5:
        print(
            "Usage: update-metadata-version.py <property-name> <old-version> <new-version> <metadata-file> [<metadata-file2> ...]"
        )
        sys.exit(1)

    property_name = sys.argv[1]
    old_version = sys.argv[2]
    new_version = sys.argv[3]
    metadata_files = sys.argv[4:]

    prefix = property_prefix(property_name)
    if prefix is None:
        print(f"ℹ️  {property_name} has no serviceVersion in the metadata, nothing to update")
        return

    untouched = [
        metadata_file
        for metadata_file in metadata_files
        if not update_metadata(prefix, old_version, new_version, metadata_file)
    ]

    if len(untouched) == len(metadata_files):
        print(f"ℹ️  No metadata entry matches {property_name}/{old_version}, nothing to update")
    elif untouched:
        # The metadata files mirror each other, so an update landing in only
        # some of them means one of them is out of sync.
        print("⚠️  No matching entry in: " + ", ".join(untouched), file=sys.stderr)


if __name__ == "__main__":
    main()
