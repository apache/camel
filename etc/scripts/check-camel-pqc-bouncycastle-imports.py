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

"""Enforce zero reflection and the version-scoped public BC import boundary for PQC production code."""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

SOURCE_ROOTS = {
    "camel-pqc": Path("components/camel-pqc/src/main/java"),
    "api": Path("components/camel-pqc-bc-compat-api/src/main/java"),
    "bc186": Path("components/camel-pqc-bc-compat-186/src/main/java"),
    "bc187": Path("components/camel-pqc-bc-compat-187/src/main/java"),
    "micrometer": Path("components/camel-pqc-micrometer/src/main/java"),
}
BC_IMPORT = re.compile(
    r"(?m)^\s*import\s+(?:static\s+)?(org\.bouncycastle\.[A-Za-z0-9_.$*]+)\s*;"
)
REFLECTION = re.compile(
    r"java\.lang\.reflect|java\.lang\.invoke|"
    r"\bClass\s*\.\s*forName\s*\(|"
    r"\bgetDeclared(?:Constructor|Method|Field)s?\s*\(|"
    r"\bget(?:Constructor|Method|Field)s?\s*\(|"
    r"\b(?:setAccessible|trySetAccessible)\s*\(|\.\s*invoke\s*\(|"
    r"\b(?:Constructor|Method|Field)\s*\.\s*newInstance\s*\(|"
    r"\bClass\s*\.\s*newInstance\s*\("
)

CORE_IMPORTS = {
    "BouncyCastleSupport.java": {
        "org.bouncycastle.jce.provider.BouncyCastleProvider",
        "org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider",
    },
    "BouncyCastleKemSupport.java": {
        "org.bouncycastle.jcajce.SecretKeyWithEncapsulation",
        "org.bouncycastle.jcajce.spec.KEMExtractSpec",
        "org.bouncycastle.jcajce.spec.KEMGenerateSpec",
        "org.bouncycastle.jcajce.spec.KTSParameterSpec",
    },
}
BC186_PREFIXES = (
    "org.bouncycastle.jcajce.",
    "org.bouncycastle.jce.",
    "org.bouncycastle.pqc.jcajce.spec.",
    "org.bouncycastle.pqc.jcajce.interfaces.",
    "org.bouncycastle.pqc.jcajce.provider.",
    "org.bouncycastle.crypto.params.",
)
# BC 1.87 moved the LMS/XMSS JCA specs and key interfaces to org.bouncycastle.jcajce,
# but the non-LMS/XMSS PQC parameter specs (BIKE, Falcon, HQC, MAYO, NTRU, SABER,
# SNTRU Prime, SNOVA, etc.) still legitimately live in org.bouncycastle.pqc.jcajce.spec.
# Permit that public package and reject only the exact deprecated compatibility aliases.
BC187_PREFIXES = (
    "org.bouncycastle.jcajce.",
    "org.bouncycastle.jce.",
    "org.bouncycastle.pqc.jcajce.provider.",
    "org.bouncycastle.pqc.jcajce.spec.",
    "org.bouncycastle.crypto.params.",
)
DEPRECATED_BC187_ALIASES = {
    "org.bouncycastle.pqc.jcajce.spec.LMSParameterSpec",
    "org.bouncycastle.pqc.jcajce.spec.LMSHSSParameterSpec",
    "org.bouncycastle.pqc.jcajce.spec.LMSKeyGenParameterSpec",
    "org.bouncycastle.pqc.jcajce.spec.LMSHSSKeyGenParameterSpec",
    "org.bouncycastle.pqc.jcajce.spec.XMSSParameterSpec",
    "org.bouncycastle.pqc.jcajce.spec.XMSSMTParameterSpec",
    "org.bouncycastle.pqc.jcajce.interfaces.LMSKey",
    "org.bouncycastle.pqc.jcajce.interfaces.LMSPrivateKey",
    "org.bouncycastle.pqc.jcajce.interfaces.XMSSKey",
    "org.bouncycastle.pqc.jcajce.interfaces.XMSSPrivateKey",
    "org.bouncycastle.pqc.jcajce.interfaces.XMSSMTKey",
    "org.bouncycastle.pqc.jcajce.interfaces.XMSSMTPrivateKey",
}



def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path.cwd(), help="Camel repository root")
    args = parser.parse_args()
    root = args.root.resolve()

    missing = [str(root / path) for path in SOURCE_ROOTS.values() if not (root / path).is_dir()]
    if missing:
        parser.error("Missing PQC production source roots:\n  " + "\n  ".join(missing))

    violations: list[str] = []
    for module, relative_root in SOURCE_ROOTS.items():
        source_root = root / relative_root
        for source in sorted(source_root.rglob("*.java")):
            text = source.read_text(encoding="utf-8")
            relative = source.relative_to(root)
            if REFLECTION.search(text):
                violations.append(f"{relative}: reflective API usage (no exceptions are permitted)")
            if "org.bouncycastle.pqc.crypto." in text:
                violations.append(f"{relative}: removed/deprecated BC PQC lightweight package reference")
            if module == "camel-pqc" and "io.micrometer." in text:
                violations.append(f"{relative}: core camel-pqc must not link directly to Micrometer")

            imports = set(BC_IMPORT.findall(text))
            if not imports:
                continue
            if module in {"api", "micrometer"}:
                violations.append(f"{relative}: BC imports are not permitted in the Camel-owned API/metrics integration")
            elif module == "camel-pqc":
                expected = CORE_IMPORTS.get(source.name)
                if expected is None:
                    violations.append(f"{relative}: BC import outside the two typed JCA/provider support classes: {sorted(imports)}")
                else:
                    unexpected = imports - expected
                    missing_imports = expected - imports
                    if unexpected:
                        violations.append(f"{relative}: unexpected BC imports: {sorted(unexpected)}")
                    if missing_imports:
                        violations.append(f"{relative}: required typed BC imports missing: {sorted(missing_imports)}")
            elif module == "bc186":
                wildcard = {name for name in imports if name.endswith(".*")}
                if wildcard:
                    violations.append(f"{relative}: wildcard BC imports are forbidden: {sorted(wildcard)}")
                unexpected = {name for name in imports if not name.startswith(BC186_PREFIXES)}
                if unexpected:
                    violations.append(f"{relative}: non-public/unsupported BC 1.86 imports: {sorted(unexpected)}")
                internal = {name for name in imports if name.startswith("org.bouncycastle.pqc.crypto.")}
                if internal:
                    violations.append(f"{relative}: removed BC PQC lightweight internals are forbidden: {sorted(internal)}")
            elif module == "bc187":
                wildcard = {name for name in imports if name.endswith(".*")}
                if wildcard:
                    violations.append(f"{relative}: wildcard BC imports are forbidden: {sorted(wildcard)}")
                unexpected = {name for name in imports if not name.startswith(BC187_PREFIXES)}
                if unexpected:
                    violations.append(f"{relative}: non-public/unsupported BC 1.87 imports: {sorted(unexpected)}")
                deprecated = imports & DEPRECATED_BC187_ALIASES
                if deprecated:
                    violations.append(f"{relative}: deprecated BC 1.87 LMS/XMSS compatibility aliases are forbidden: {sorted(deprecated)}")
                internal = {name for name in imports if name.startswith("org.bouncycastle.pqc.crypto.")}
                if internal:
                    violations.append(f"{relative}: removed BC PQC lightweight internals are forbidden: {sorted(internal)}")

    if violations:
        print("camel-pqc zero-reflection/public-BC boundary FAILED:", file=sys.stderr)
        for violation in violations:
            print(f"  - {violation}", file=sys.stderr)
        return 1

    print("camel-pqc boundary PASSED: zero production reflection; typed, version-scoped BC API imports only")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
