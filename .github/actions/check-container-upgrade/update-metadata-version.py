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

Usage:
    python3 update-metadata-version.py <artifact-id> <old-version> <new-version> <metadata-file> [<metadata-file2> ...]
"""

import json
import os
import sys


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


def update_metadata(artifact_id, old_version, new_version, metadata_file):
    """Update serviceVersion for entries matching the artifact and old version."""
    if not os.path.isfile(metadata_file):
        print(f"⚠️  {metadata_file} not found, skipping")
        return False

    with open(metadata_file, "r", encoding="utf-8") as f:
        data = json.load(f)

    updated = False
    for entry in data:
        if (
            entry.get("artifactId") == artifact_id
            and entry.get("serviceVersion") == old_version
        ):
            entry["serviceVersion"] = new_version
            updated = True

    if updated:
        with open(metadata_file, "w", encoding="utf-8") as f:
            f.write(jackson_format(data))
        print(f"✅ Updated serviceVersion {old_version} → {new_version} in {metadata_file}")
    else:
        print(f"ℹ️  No matching entries for {artifact_id}/{old_version} in {metadata_file}")

    return updated


def main():
    if len(sys.argv) < 5:
        print(
            "Usage: update-metadata-version.py <artifact-id> <old-version> <new-version> <metadata-file> [<metadata-file2> ...]"
        )
        sys.exit(1)

    artifact_id = sys.argv[1]
    old_version = sys.argv[2]
    new_version = sys.argv[3]
    metadata_files = sys.argv[4:]

    failed_metadata_files = []
    for metadata_file in metadata_files:
        if not update_metadata(artifact_id, old_version, new_version, metadata_file):
            failed_metadata_files.append(metadata_file)

    if failed_metadata_files:
        print(
            "❌ No metadata target was updated in: "
            + ", ".join(failed_metadata_files),
            file=sys.stderr,
        )
        sys.exit(1)


if __name__ == "__main__":
    main()
