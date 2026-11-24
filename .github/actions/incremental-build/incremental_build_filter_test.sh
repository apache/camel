#!/usr/bin/env bash
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

# Unit tests for the filterExclusions function in incremental-build.sh.
# Tests that exclusions are dropped when the excluded module is an explicitly
# included primary change, and kept otherwise.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Extract and load only filterExclusions from incremental-build.sh without
# running main().  awk pulls the function body between its opening brace
# and the matching closing brace (depth-tracked), then eval loads it.
_fn_body=$(awk '
  /^filterExclusions\(\)/ { in_fn=1; depth=0 }
  in_fn {
    for (i=1; i<=length($0); i++) {
      c = substr($0, i, 1)
      if (c == "{") depth++
      if (c == "}") depth--
    }
    print
    if (in_fn && depth == 0) { in_fn=0 }
  }
' "${SCRIPT_DIR}/incremental-build.sh")
eval "$_fn_body"

pass=0
fail=0

assert_eq() {
  local expected="$1"
  local actual="$2"
  local message="$3"
  if [[ "$expected" == "$actual" ]]; then
    pass=$((pass + 1))
  else
    echo "FAIL: $message"
    echo "  expected: [$expected]"
    echo "  actual:   [$actual]"
    fail=$((fail + 1))
  fi
}

EXCLUSION_LIST="!:camel-allcomponents,!:camel-catalog,!:camel-yaml-dsl,!:camel-jbang-core"

# 1. Normal case: no overlap between build_pl and exclusions → all exclusions kept
result=$(filterExclusions "components/camel-kafka" "$EXCLUSION_LIST" 2>/dev/null)
assert_eq "$EXCLUSION_LIST" "$result" \
  "no overlap: all exclusions are retained"

# 2. build_pl contains a path whose basename matches an excluded artifact →
#    that exclusion is lifted (the module is a primary change, not a downstream)
result=$(filterExclusions "dsl/camel-yaml-dsl,components/camel-kafka" "$EXCLUSION_LIST" 2>/dev/null)
assert_eq "!:camel-allcomponents,!:camel-catalog,!:camel-jbang-core" "$result" \
  "path-based match: exclusion for camel-yaml-dsl is lifted"

# 3. build_pl contains an :artifactId-style reference → that exclusion is lifted
result=$(filterExclusions ":camel-jbang-core,components/camel-kafka" "$EXCLUSION_LIST" 2>/dev/null)
assert_eq "!:camel-allcomponents,!:camel-catalog,!:camel-yaml-dsl" "$result" \
  "colon-style match: exclusion for camel-jbang-core is lifted"

# 4. Multiple matches: both a path-based and colon-based exclusion are lifted
result=$(filterExclusions "dsl/camel-yaml-dsl,:camel-jbang-core" "$EXCLUSION_LIST" 2>/dev/null)
assert_eq "!:camel-allcomponents,!:camel-catalog" "$result" \
  "multiple matches: both camel-yaml-dsl and camel-jbang-core exclusions are lifted"

# 5. All exclusions lifted (every excluded module is a primary change)
SMALL_EXCL="!:camel-catalog,!:camel-yaml-dsl"
result=$(filterExclusions "dsl/camel-yaml-dsl,:camel-catalog" "$SMALL_EXCL" 2>/dev/null)
assert_eq "" "$result" \
  "all exclusions lifted when every excluded module is a primary change"

# 6. Empty build_pl → all exclusions retained (edge case: nothing changed)
result=$(filterExclusions "" "$EXCLUSION_LIST" 2>/dev/null)
assert_eq "$EXCLUSION_LIST" "$result" \
  "empty build_pl: all exclusions are retained"

# 7. Exclusion whose basename is a prefix of an included module must NOT be lifted
#    (camel-yaml vs camel-yaml-dsl: basename matching is exact, not prefix)
EXCL_PREFIX="!:camel-yaml"
result=$(filterExclusions "dsl/camel-yaml-dsl" "$EXCL_PREFIX" 2>/dev/null)
assert_eq "$EXCL_PREFIX" "$result" \
  "prefix non-match: camel-yaml exclusion is NOT lifted by camel-yaml-dsl module"

echo ""
echo "incremental-build-filter-test.sh: ${pass} passed, ${fail} failed"
if [[ "$fail" -ne 0 ]]; then
  exit 1
fi
