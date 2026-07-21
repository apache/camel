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

# ============================================================================
# Shared POSIX assertions for camel-validate.sh / assertion library tests
#
# Usage (caller must source this file):
#   assert_camel_version <actual_output> <expected_version>    -- returns 1 on mismatch
#   assert_init_content <directory> <filename>                 -- verifies content matches fixture
#   assert_uninstalled <path_or_prefix ...>                    -- each must not exist after uninstall
#   assert_camel_cli <camel-cmd> <workdir> [expected-version] -- full version+init assertion wrapper
#   assert_camel_absent <path>                                -- verify a camel symlink/entry is gone
# ============================================================================

if [ -n "${BASH_SOURCE+set}" ]; then
    _raw_source="${BASH_SOURCE[0]}"
elif [ -n "$0" ] && [ "$0" != "-" ]; then
    _raw_source="$0"
else
    _raw_source=""
fi

if [ -n "$_raw_source" ]; then
    _ASSERT_LIB_DIR=$(CDPATH='' cd -- "$(dirname -- "$_raw_source")" && pwd)
else
    _ASSERT_LIB_DIR=$(CDPATH='' cd -- "$(dirname -- "${0:-.}")" && pwd)
fi
unset _raw_source

assertion_pass() { echo "PASS: $1"; }
assertion_fail()  { echo "FAIL: $1"; return 1; }

assert_camel_version() {
    local actual="$1" expected="$2"
    local actual_ver
    actual_ver=$(echo "$actual" | head -n 1 | awk '{print $NF}')
    if [ "$actual_ver" = "$expected" ]; then
        assertion_pass "camel version matches expected '$expected'"
        return 0
    else
        assertion_fail "camel version mismatch: expected '$expected', got '$actual_ver'"
        return 1
    fi
}

assert_init_content() {
    local dir="$1" filename="$2"
    local filepath="$dir/$filename"
    if [ ! -f "$filepath" ]; then
        assertion_fail "init output file not found: $filepath"
        return 1
    fi
    local fixture="${ASSERT_INIT_FIXTURE:-$_ASSERT_LIB_DIR/../../../test/resources/validate/expected-init-route.txt}"
    if [ ! -f "$fixture" ]; then
        assertion_fail "init fixture not found: $fixture (are you running from camel-launcher module?)"
        return 1
    fi
    if cmp -s "$filepath" "$fixture"; then
        assertion_pass "camel init route content matches expected fixture"
        return 0
    else
        assertion_fail "camel init route content differs from fixture (diff below):"
        diff -u "$fixture" "$filepath" || true
        return 1
    fi
}

assert_uninstalled() {
    for entry in "$@"; do
        if [ -e "$entry" ] || [ -L "$entry" ]; then
            assertion_fail "uninstall left behind: $entry"
            return 1
        else
            assertion_pass "'$entry' does not exist (removed by uninstall)"
        fi
    done
    return 0
}

# Convenience wrapper: runs `camel version` and `camel init`, checks everything.
assert_camel_cli() {
    local CAMELCMD="$1" WORKDIR="$2" EXPECTED_VERSION="${3:-}"
    local _orig_dir
    _orig_dir=$(pwd)

    # Step 1: version check. When an expected version is given, a mismatch is a real failure
    # that must propagate out of this wrapper (empty output stays a soft skip - the caller may
    # be validating a CLI that cannot print a version on this host).
    local camv_output
    camv_output=$("$CAMELCMD" --version 2>/dev/null) || true
    if [ -z "$camv_output" ]; then
        local camv_err=""
        camv_err=$("$CAMELCMD" --version 2>&1 >/dev/null) || true
        echo "WARN: camel version returned empty output (skipped)${camv_err:+ (stderr: $camv_err)}"
    elif [ -n "$EXPECTED_VERSION" ]; then
        assert_camel_version "$camv_output" "$EXPECTED_VERSION" || return 1
    else
        echo "INFO: camel version reported: $camv_output (no expected version given, skipping comparison)"
    fi

    # Step 2: init content check. Uses "hello.java" (not an arbitrary name) because the
    # fixture's class name is derived from this filename, same as the real POSIX validators. A
    # failing init is a genuine defect, not something to warn past - matches the inline validators
    # in camel-validate.sh, which FAIL on the same condition.
    cd "$WORKDIR" || { echo "FAIL: cannot cd to $WORKDIR"; return 1; }
    if [ -f hello.java ]; then rm -f hello.java; fi
    local init_err=""
    if init_err=$("$CAMELCMD" init hello.java 2>&1 >/dev/null); then
        assert_init_content "$WORKDIR" "hello.java" || { echo "FAIL: generated route missing expected content"; cd "$_orig_dir" || exit; return 1; }
    else
        echo "FAIL: camel init failed${init_err:+: $init_err}"
        cd "$_orig_dir" || exit
        return 1
    fi
    cd "$_orig_dir" || exit

    # Step 3: assert the executable exists. A binary that isn't executable at this point is a
    # real defect (everything above only worked because a shell can still run a non-executable
    # script via an interpreter shebang search in some environments), not a soft skip.
    if [ -x "$CAMELCMD" ]; then
        assertion_pass "camel CLI executable found"
    else
        echo "FAIL: camel CLI not executable at '$CAMELCMD'"
        return 1
    fi

    return 0
}

assert_camel_absent() {
    local path="$1"
    if [ -e "$path" ] || [ -L "$path" ]; then
        assertion_fail "uninstall left behind: $path"
        return 1
    else
        assertion_pass "'$path' does not exist (removed by uninstall)"
        return 0
    fi
}
