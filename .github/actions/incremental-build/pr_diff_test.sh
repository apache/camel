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

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=pr_diff.sh
source "${SCRIPT_DIR}/pr_diff.sh"

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

changed_files() {
  sed -n -e '/^diff --git a/p' | awk '{print $3}' | cut -b 3- | sort
}

# A repository whose origin/main moved on after the PR branch left it
repo=$(mktemp -d)
trap 'rm -rf "$repo"' EXIT
cd "$repo"
git init -q -b main
git config user.email test@example.com
git config user.name test
mkdir -p components/a components/b
echo a > components/a/A.java
git add -A && git commit -q -m base
git checkout -q -b pr
echo b > components/b/B.java
echo a2 > components/a/A.java
git add -A && git commit -q -m pr
git checkout -q main
echo c > main-only.txt
git add -A && git commit -q -m "main moves on"
git update-ref refs/remotes/origin/main main
git checkout -q pr

assert_eq "$(printf 'components/a/A.java\ncomponents/b/B.java')" "$(localDiff main | changed_files)" \
  "local diff has the PR changes only, not the later changes of the base branch"
assert_eq "" "$(localDiff other 2>/dev/null)" "no local diff without the base branch"

# A pull_request build checks out the merge of the PR into the base branch
git checkout -q --detach main
git merge -q --no-edit pr
assert_eq "$(printf 'components/a/A.java\ncomponents/b/B.java')" "$(localDiff main | changed_files)" \
  "local diff of the merge commit has the PR changes only"
git checkout -q pr

export GITHUB_TOKEN=test-token

# The API refuses the diff of a PR beyond the diff limits
curl() {
  printf '%s\n%s' '{"message": "Sorry, the diff exceeded the maximum number of files (300).", "status": "406"}' 406
}
assert_eq "" "$(fetchApiDiff 1 apache/camel 2>/dev/null)" "no API diff on HTTP 406"
assert_eq "WARNING: The GitHub API did not return the PR diff (HTTP 406): Sorry, the diff exceeded the maximum number of files (300)." \
  "$(fetchApiDiff 1 apache/camel 2>&1 >/dev/null)" "the warning names the HTTP status and the API message"
assert_eq "$(printf 'components/a/A.java\ncomponents/b/B.java')" "$(fetchDiff 1 apache/camel main 2>/dev/null | changed_files)" \
  "falls back to the local diff on HTTP 406"
assert_eq "" "$(fetchDiff 1 apache/camel other 2>/dev/null)" "no diff when neither the API nor the local history has it"

# The API returns the diff
curl() {
  printf '%s\n%s' 'diff --git a/components/x/X.java b/components/x/X.java' 200
}
assert_eq "components/x/X.java" "$(fetchDiff 1 apache/camel main 2>/dev/null | changed_files)" "uses the API diff when there is one"

# The API cannot be reached
curl() {
  return 6
}
assert_eq "$(printf 'components/a/A.java\ncomponents/b/B.java')" "$(fetchDiff 1 apache/camel main 2>/dev/null | changed_files)" \
  "falls back to the local diff when curl fails"

echo ""
echo "pr_diff_test.sh: ${pass} passed, ${fail} failed"
if [[ "$fail" -ne 0 ]]; then
  exit 1
fi
