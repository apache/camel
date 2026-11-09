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

# Helpers that give the unified diff of a pull request.

# The PR diff from the GitHub API, or nothing when the API does not return it.
# The API refuses the diff of a PR beyond the GitHub diff limits (HTTP 406, for
# example more than 300 files).
fetchApiDiff() {
  local prId="$1"
  local repository="$2"

  local diff_output
  diff_output=$(curl -s -w "\n%{http_code}" \
    -H "Authorization: Bearer ${GITHUB_TOKEN}" \
    -H "Accept: application/vnd.github.v3.diff" \
    "https://api.github.com/repos/${repository}/pulls/${prId}" || true)

  local http_code
  http_code=$(echo "$diff_output" | tail -n 1)
  local diff_body
  diff_body=$(echo "$diff_output" | sed '$d')

  if [[ ! "$http_code" =~ ^2[0-9][0-9]$ || -z "$diff_body" ]]; then
    local message
    message=$(echo "$diff_body" | jq -r '.message // empty' 2>/dev/null || true)
    echo "WARNING: The GitHub API did not return the PR diff (HTTP ${http_code:-none})${message:+: $message}" >&2
    return 0
  fi
  echo "$diff_body"
}

# The diff of HEAD against its merge base with origin/<base branch>, or nothing
# when the merge base is not in the local history.
localDiff() {
  local baseRef="$1"

  local merge_base
  if ! merge_base=$(git merge-base HEAD "origin/${baseRef}" 2>/dev/null); then
    echo "WARNING: No merge base between HEAD and origin/${baseRef} in the local history" >&2
    return 0
  fi
  git diff "$merge_base" HEAD
}

# The PR diff: from the GitHub API, else from the local checkout.
fetchDiff() {
  local prId="$1"
  local repository="$2"
  local baseRef="${3:-main}"

  local diff_body
  diff_body=$(fetchApiDiff "$prId" "$repository")
  if [ -z "$diff_body" ]; then
    echo "Computing the PR diff from the local checkout against origin/${baseRef}" >&2
    diff_body=$(localDiff "$baseRef")
  fi
  echo "$diff_body"
}
