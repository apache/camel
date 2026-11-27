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
"""Enforce the camel-pqc production-source Bouncy Castle import boundary."""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ADAPTER = Path("components/camel-pqc/src/main/java/org/apache/camel/component/pqc/BouncyCastleKemSupport.java")
# Production sources must use JCA/JCE interfaces and the Java 21 javax.crypto.KEM API.
# Provider-specific BC parameter specs/carriers are resolved reflectively inside the adapter.
ALLOWED: set[str] = set()
IMPORT = re.compile(r"^\s*import\s+(?:static\s+)?(org\.bouncycastle\.[A-Za-z0-9_.$*]+)\s*;", re.MULTILINE)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path.cwd(), help="Camel repository root (default: current directory)")
    args = parser.parse_args()
    root = args.root.resolve()
    source_root = root / "components/camel-pqc/src/main/java"
    adapter = root / ADAPTER
    if not source_root.is_dir() or not adapter.is_file():
        parser.error("expected a Camel repository containing camel-pqc production sources and BouncyCastleKemSupport.java")

    violations: list[str] = []
    adapter_imports: set[str] = set()
    for source in sorted(source_root.rglob("*.java")):
        imports = set(IMPORT.findall(source.read_text(encoding="utf-8")))
        bc_imports = {name for name in imports if name.startswith("org.bouncycastle.")}
        if source == adapter:
            adapter_imports = bc_imports
            unexpected = bc_imports - ALLOWED
            missing = ALLOWED - bc_imports
            if unexpected:
                violations.append(f"{source.relative_to(root)}: unexpected BC imports: {', '.join(sorted(unexpected))}")
            if missing:
                violations.append(f"{source.relative_to(root)}: required BC KEM imports missing: {', '.join(sorted(missing))}")
        elif bc_imports:
            violations.append(f"{source.relative_to(root)}: production BC imports are restricted to {ADAPTER}: "
                              + ", ".join(sorted(bc_imports)))

    if violations:
        print("camel-pqc Bouncy Castle production import fence FAILED:", file=sys.stderr)
        for violation in violations:
            print(f"  - {violation}", file=sys.stderr)
        return 1
    if adapter_imports != ALLOWED:
        print("camel-pqc Bouncy Castle production import fence FAILED: adapter import set is not exact", file=sys.stderr)
        return 1
    print("camel-pqc Bouncy Castle production import fence PASSED (zero BC production imports)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
