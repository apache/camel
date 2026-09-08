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
# shellcheck source=reactor_timing.sh
source "${SCRIPT_DIR}/reactor_timing.sh"

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

assert_contains() {
  local haystack="$1"
  local needle="$2"
  local message="$3"
  if [[ "$haystack" == *"$needle"* ]]; then
    pass=$((pass + 1))
  else
    echo "FAIL: $message"
    echo "  expected to contain: [$needle]"
    echo "  actual:              [$haystack]"
    fail=$((fail + 1))
  fi
}

SAMPLE_LINE='[INFO] Camel :: Kafka :: camel-kafka ........................ SUCCESS [ 252.500 s]'
assert_eq "252.500" "$(parse_reactor_duration_seconds "$SAMPLE_LINE")" "parse duration from success line"
assert_eq "SUCCESS" "$(parse_reactor_status "$SAMPLE_LINE")" "parse status from success line"
assert_eq "Camel :: Kafka :: camel-kafka" "$(parse_reactor_module_name "$SAMPLE_LINE")" "parse module name"

NO_DOTS_LINE='[INFO] Camel :: CSimple Maven Plugin (deprecated) SUCCESS [  2.123 s]'
assert_eq "2.123" "$(parse_reactor_duration_seconds "$NO_DOTS_LINE")" "parse duration from no-dots line"
assert_eq "SUCCESS" "$(parse_reactor_status "$NO_DOTS_LINE")" "parse status from no-dots line"
assert_eq "Camel :: CSimple Maven Plugin (deprecated)" "$(parse_reactor_module_name "$NO_DOTS_LINE")" "parse module name without dot padding"

FAILURE_LINE='[INFO] Camel :: Exec .................................... FAILURE [  1.234 s]'
assert_eq "1.234" "$(parse_reactor_duration_seconds "$FAILURE_LINE")" "parse duration from failure line"
assert_eq "FAILURE" "$(parse_reactor_status "$FAILURE_LINE")" "parse status from failure line"

SKIPPED_LINE='[INFO] Camel :: Catalog ................................. SKIPPED'
assert_eq "" "$(parse_reactor_duration_seconds "$SKIPPED_LINE")" "skipped line has no duration"
assert_eq "SKIPPED" "$(parse_reactor_status "$SKIPPED_LINE")" "parse skipped status"

assert_eq "12.3s" "$(format_elapsed_seconds "12.345")" "format sub-minute duration"
assert_eq "1m 5s" "$(format_elapsed_seconds "65")" "format minute duration"
assert_eq "1h 2m" "$(format_elapsed_seconds "3720")" "format hour duration"
assert_eq "n/a" "$(format_elapsed_seconds "")" "format empty duration"

fixture="$(mktemp)"
cat > "$fixture" <<'EOF'
[INFO] Camel :: Core :: camel-core ........................ SUCCESS [  10.000 s]
[INFO] Camel :: Kafka :: camel-kafka .................... SUCCESS [ 120.000 s]
[INFO] Camel :: HTTP :: camel-http ...................... SUCCESS [  30.000 s]
[INFO] Camel :: Exec .................................... FAILURE [   5.500 s]
[INFO] Camel :: Catalog ................................. SKIPPED
EOF

tsv="$(parse_reactor_log_to_tsv "$fixture")"
assert_eq "5" "$(echo "$tsv" | grep -c . || true)" "module count includes skipped modules"
assert_eq "165.500" "$(sum_elapsed_seconds_from_tsv "$tsv")" "sum elapsed seconds"

report_file="$(mktemp)"
append_reactor_timing_report "$fixture" "$report_file" "All tested modules" ""
report_content="$(cat "$report_file")"
if [[ "$report_content" == *"165.500"* ]]; then
  echo "FAIL: report should not expose raw seconds"
  fail=$((fail + 1))
else
  pass=$((pass + 1))
fi
assert_contains "$report_content" "2m 46s total" "report includes formatted total time"
assert_contains "$report_content" "| Camel :: Kafka :: camel-kafka | 2m 0s | SUCCESS |" "slowest module listed first"
assert_contains "$report_content" "**Top ${TOP_SLOWEST_LIMIT} slowest modules:**" "report includes slowest section"
assert_contains "$report_content" "\`Camel :: Kafka :: camel-kafka\` (2m 0s)" "slowest bullet uses formatted duration"

rm -f "$fixture" "$report_file"

empty_fixture="$(mktemp)"
touch "$empty_fixture"
empty_tsv="$(parse_reactor_log_to_tsv "$empty_fixture")"
assert_eq "" "$empty_tsv" "empty log yields empty tsv without error"

empty_report="$(mktemp)"
append_reactor_timing_report "$empty_fixture" "$empty_report" "All tested modules" ""
assert_eq "" "$(cat "$empty_report")" "empty log yields empty report"
rm -f "$empty_fixture" "$empty_report"

echo ""
echo "reactor_timing_test.sh: ${pass} passed, ${fail} failed"
if [[ "$fail" -ne 0 ]]; then
  exit 1
fi
