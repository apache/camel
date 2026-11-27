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

# Unit tests for the countTestableModules function in incremental-build.sh.
# The threshold check must count the dependency-detected modules (grep + Scalpel)
# that are added to -pl next to the -amd reactor of the changed modules.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Extract and load only countTestableModules from incremental-build.sh without
# running main() (same approach as incremental_build_filter_test.sh).
_fn_body=$(awk '
  /^countTestableModules\(\)/ { in_fn=1; depth=0 }
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

REACTOR=$'camel-catalog\ncamel-endpointdsl\ncamel-ai-prompt'

# 1. No dependency-detected modules: the count is the -amd reactor
assert_eq "3" "$(countTestableModules "$REACTOR" "")" \
  "reactor only: counts the -amd reactor"

# 2. Dependency-detected modules are added to the count
assert_eq "5" "$(countTestableModules "$REACTOR" ":camel-kafka,:camel-zipfile")" \
  "reactor + deps: dependency-detected modules are counted"

# 3. A module in both lists is counted once
assert_eq "4" "$(countTestableModules "$REACTOR" ":camel-catalog,:camel-zipfile")" \
  "overlap: a module in both lists is counted once"

# 4. Nothing at all
assert_eq "0" "$(countTestableModules "" "")" \
  "empty: counts zero"

# 5. Many Scalpel downstream modules push the count over the threshold of 50
DEPS=$(for i in $(seq 1 60); do printf ':camel-m%s,' "$i"; done)
assert_eq "63" "$(countTestableModules "$REACTOR" "${DEPS%,}")" \
  "many downstream modules: all are counted"

echo ""
echo "incremental-build-count-test.sh: ${pass} passed, ${fail} failed"
if [[ "$fail" -ne 0 ]]; then
  exit 1
fi
