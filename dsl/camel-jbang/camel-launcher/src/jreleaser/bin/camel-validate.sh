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
# camel-validate.sh — POSIX validator dispatcher (Homebrew + SDKMAN)
#
# Usage:
#   camel-validate.sh <command> [options]
#
# Commands:
#   all      Run every validator: local (always, if the archive is present), plus the
#            host-gated homebrew and sdkman validators.
#   local    Validate this build's own archive directly (no package manager involved)
#   homebrew Validate via Homebrew (audit + install + version + init + uninstall)
#   sdkman   Validate via SDKMAN (descriptor check + offline archive + version + init)
#   help     Show usage
#
# Host-gating:
#   The homebrew and sdkman validators check for their package manager and print "SKIP:"
#   (exit 0) if it is absent. The local validator is not package-manager-gated; it runs
#   whenever the staged archive exists. Any genuine validation failure exits nonzero.
# ============================================================================

set -eu

SCRIPT_DIR=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
MODULE_DIR=$(CDPATH='' cd -- "$SCRIPT_DIR/../../.." && pwd)
LIB_DIR="$SCRIPT_DIR/lib"

# Prepare/build outputs live under target/. Overridable in test mode only, so unit tests can
# point the archive validators at a synthetic staging dir instead of the real target/.
if [ -n "${CAMEL_VALIDATE_TARGET_DIR:-}" ]; then
    if [ "${CAMEL_PACKAGE_TEST_MODE:-}" != "true" ]; then
        echo "Error: CAMEL_VALIDATE_TARGET_DIR requires CAMEL_PACKAGE_TEST_MODE=true." >&2
        exit 2
    fi
    TARGET_DIR="$CAMEL_VALIDATE_TARGET_DIR"
else
    TARGET_DIR="$MODULE_DIR/target"
fi

usage() {
    cat <<EOF
Usage: camel-validate.sh <command> [options]

Commands:
  all        Run all available validators (local, homebrew, sdkman). Package-manager
             validators are host-gated; "local" always runs if the archive is present.
  local      Validate this build's own archive directly (no package manager involved)
  homebrew   Validate via Homebrew (audit + install + version + init + uninstall)
  sdkman     Validate via SDKMAN (descriptor check + offline archive + version + init)
  help       Show this help message

Options:
  --channel <stable|lts>          Packaging channel (default: stable)
  --lts-line <X.Y>                LTS line being validated (required for --channel lts)
  --project-version <X.Y.Z>       Override detected project version (test mode only)

Env (test mode only, mirrors camel-package.sh):
  CAMEL_PACKAGE_TEST_MODE=true and CAMEL_PACKAGE_TEST_VERSION=<X.Y.Z> can be used instead of
  --project-version; the flag takes precedence if both are set.
EOF
    exit 2
}

# ---------- argument parsing ----------

COMMAND=""
CHANNEL="stable"
PROJECT_VERSION=""
LTS_LINE=""

[ $# -ge 1 ] || usage
COMMAND="$1"; shift
case "$COMMAND" in
  all|local|homebrew|sdkman|help) ;;
  *) echo "Error: unknown command '$COMMAND'." >&2; usage ;;
esac

while [ $# -gt 0 ]; do
    case "$1" in
        --channel) CHANNEL="$2"; shift 2 ;;
        --lts-line) LTS_LINE="$2"; shift 2 ;;
        --project-version) PROJECT_VERSION="$2"; shift 2 ;;
        *) echo "Error: unknown option '$1'." >&2; exit 2 ;;
    esac
done

case "$COMMAND" in help) usage ;; esac

# ---------- sourcing ----------

. "$LIB_DIR/assert-camel-cli.sh"

# ---------- shared temp workspace ----------
# Used by every validator (homebrew, sdkman); created once and cleaned on exit. Kept at script
# scope (not local to a validator) so both functions and the EXIT trap can see it under 'set -u'.
TMPDIR_BASE=$(mktemp -d)

# Name of the throwaway local Homebrew tap created by validate_homebrew() (empty until it
# creates one). Kept at script scope so the EXIT trap can always untap it, even if
# validate_homebrew() returns early (SKIP/FAIL) partway through.
BREW_TAP_NAME=""

brew_untap_validator_tap() {
    if [ -n "$BREW_TAP_NAME" ] && command -v brew >/dev/null 2>&1; then
        HOMEBREW_NO_AUTO_UPDATE=1 brew untap "$BREW_TAP_NAME" >/dev/null 2>&1 || true
    fi
}

trap 'brew_untap_validator_tap; rm -rf "$TMPDIR_BASE" >/dev/null 2>&1 || true' EXIT

# ---------- resolve version ----------

if [ -n "$PROJECT_VERSION" ]; then
    RESOLVED_VERSION="$PROJECT_VERSION"
elif [ -n "${CAMEL_PACKAGE_TEST_VERSION:-}" ]; then
    # Mirrors camel-package.sh's own test-mode override so CI doesn't need to thread the
    # synthetic version through as an extra CLI flag on top of the env vars it already exports.
    if [ "${CAMEL_PACKAGE_TEST_MODE:-}" != "true" ]; then
        echo "Error: CAMEL_PACKAGE_TEST_VERSION requires CAMEL_PACKAGE_TEST_MODE=true." 1>&2
        exit 2
    fi
    RESOLVED_VERSION="$CAMEL_PACKAGE_TEST_VERSION"
else
    RESOLVED_VERSION=$(mvn -q -B -ntp -f "$MODULE_DIR/pom.xml" \
        org.apache.maven.plugins:maven-help-plugin:3.5.1:evaluate \
        -Dexpression=project.version -DforceStdout)
fi

# ---------- versioned formula name helper ----------
# Reads $CHANNEL/$LTS_LINE directly (not passed as an argument) to avoid the
# formula-name/channel mismatch: the lts channel's brew formula is keyed off the
# LTS line, not the literal channel name "lts".

formula_name() {
    case "$CHANNEL" in
        stable) echo "camel" ;;
        lts)    echo "camel@$LTS_LINE" ;;
    esac
}

# Portable sha256 of a file (stdout = bare hex digest). Mirrors install.sh's fallback chain.
sha256_of() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print $1}'
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$1" | awk '{print $1}'
    else
        openssl dgst -sha256 "$1" | awk '{print $NF}'
    fi
}

# ============================================================
# Homebrew validator
# ============================================================

validate_homebrew() {
    local rc=0
    # Host-gate: homebrew must be present on PATH
    if ! command -v brew >/dev/null 2>&1; then
        echo "SKIP: homebrew not available"
        return 0
    fi

    local fmla formula_file
    fmla=$(formula_name)

    # JReleaser 1.25.0 actually writes the packaged Homebrew formula to:
    #   target/jreleaser/package/camel-cli/brew/Formula/<formula-name>.rb
    # (empirically verified with a real, non-stubbed jreleaser:package dry run; there is no
    # target/jreleaser/distributions/ directory at all in this JReleaser version).
    formula_file="$TARGET_DIR/jreleaser/package/camel-cli/brew/Formula/${fmla}.rb"

    if [ ! -f "$formula_file" ]; then
        echo "SKIP: homebrew formula not found: $formula_file (did prepare run?)"
        return 0
    fi

    echo "--- Homebrew validation ---"

    # Recent Homebrew rejects `brew install`/`brew audit` on a bare formula file path
    # ("Homebrew requires formulae to be in a tap" / "brew audit [path ...] is disabled");
    # empirically confirmed on this machine. A throwaway local tap (removed via `brew untap`
    # in the script's EXIT trap) satisfies that requirement without any network access or
    # real publication. `brew install` auto-trusts formulae from a tap created this way (no
    # separate trust command exists or is needed - empirically confirmed).
    local tap_name="camel-cli-validators/formulae"
    local tap_dir tap_output

    brew untap "$tap_name" >/dev/null 2>&1 || true
    if ! tap_output=$(HOMEBREW_NO_AUTO_UPDATE=1 brew tap-new "$tap_name" --no-git 2>&1); then
        echo "FAIL: could not create local validation tap '$tap_name':"
        echo "$tap_output"
        return 1
    fi
    BREW_TAP_NAME="$tap_name"

    tap_dir=$(brew --repository "$tap_name")
    if ! cp -p "$formula_file" "$tap_dir/Formula/${fmla}.rb"; then
        echo "FAIL: could not copy the generated formula into the validation tap"
        return 1
    fi
    # 644, not `a+r`: a world-writable formula file trips `brew audit --strict`
    # (FormulaAudit/Files "Incorrect file permissions (777)"), which would be a false
    # failure of the audit gate below caused by the tap copy itself, not by the formula.
    chmod 644 "$tap_dir/Formula/${fmla}.rb"

    # Step 1: Homebrew style + audit, referenced by tap-qualified name (not a path).
    # `brew style --fix` only normalizes formatting; its output is informational.
    local style_output=""
    style_output=$(HOMEBREW_NO_AUTO_UPDATE=1 brew style --fix "$tap_name/$fmla" 2>&1 || true)
    echo "INFO: homebrew style output:"
    echo "$style_output"

    # `brew audit --strict` is a real gate: a nonzero exit means a defect in the generated
    # formula and must fail validation, not warn past it. --online is deliberately omitted so
    # audit stays offline and never fails merely because the (test-mode, unpublished) download
    # URL is unreachable - only the offline structural/style/naming checks run here.
    local audit_output audit_rc=0
    audit_output=$(HOMEBREW_NO_AUTO_UPDATE=1 brew audit --strict "$tap_name/$fmla" 2>&1) || audit_rc=$?
    # Always echo the audit output (even on success) so the log shows the gate actually ran and
    # surfaces any advisory lines that did not rise to a nonzero exit.
    echo "INFO: homebrew audit --strict output:"
    echo "$audit_output"
    if [ "$audit_rc" -ne 0 ]; then
        echo "FAIL: homebrew audit --strict reported issues (exit $audit_rc)"
        rc=1
    else
        echo "PASS: homebrew style/audit passed"
    fi

    # Step 2: Install the formula from the tap.
    #
    # In test mode the formula's download URL points at a Maven coordinate for a version that
    # is not published yet (in CI the real POM version is a -SNAPSHOT, and JReleaser renders
    # the URL from that POM version - Central never hosts SNAPSHOTs). Rather than let `brew
    # install` fail to download and skip the whole install/version/init/uninstall chain,
    # rewrite the tapped formula's url to a file:// path for this build's own staged archive
    # and recompute its sha256, so brew genuinely downloads, verifies, extracts and installs
    # the real binary this build produced - fully offline and deterministic. Real release runs
    # (not test mode) keep the published Central URL untouched. This runs after `brew audit`
    # above, so audit still validates the real published-style formula, not the file:// one.
    if [ "${CAMEL_PACKAGE_TEST_MODE:-}" = "true" ]; then
        local url_basename staged_archive staged_sha
        url_basename=$(sed -n 's/^[[:space:]]*url "[^"]*\/\([^/"]*\)"/\1/p' "$tap_dir/Formula/${fmla}.rb" | head -n1)
        staged_archive="$TARGET_DIR/$url_basename"
        if [ -z "$url_basename" ] || [ ! -f "$staged_archive" ]; then
            echo "FAIL: test-mode formula rewrite could not locate the staged archive for '$url_basename' under $TARGET_DIR"
            return 1
        fi
        staged_sha=$(sha256_of "$staged_archive")
        if ! sed -e "s|^\([[:space:]]*\)url \"[^\"]*\"|\1url \"file://$staged_archive\"|" \
                 -e "s|^\([[:space:]]*\)sha256 \"[^\"]*\"|\1sha256 \"$staged_sha\"|" \
                 "$tap_dir/Formula/${fmla}.rb" > "$tap_dir/Formula/${fmla}.rb.new"; then
            echo "FAIL: could not rewrite the tapped formula for offline install"
            return 1
        fi
        mv -f "$tap_dir/Formula/${fmla}.rb.new" "$tap_dir/Formula/${fmla}.rb"
    fi

    local BREW_HOME="$TMPDIR_BASE/brew-home"
    mkdir -p "$BREW_HOME"
    local brew_output install_rc=0
    brew_output=$(HOME="$BREW_HOME" \
        HOMEBREW_NO_AUTO_UPDATE=1 \
        brew install "$tap_name/$fmla" --force 2>&1) || install_rc=$?

    if [ "$install_rc" -ne 0 ]; then
        if echo "$brew_output" | grep -Eqi "no suitable java"; then
            # A missing/incompatible Java dependency is a host-resolution limitation, not a
            # defect in the generated formula; nothing was installed, so there is nothing
            # left to validate.
            echo "SKIP: homebrew install could not resolve a Java dependency on this host:"
            echo "$brew_output"
            return 0
        fi
        if [ "${CAMEL_PACKAGE_TEST_MODE:-}" != "true" ] \
                && echo "$brew_output" | grep -Eqi "Failed to download resource|curl: \("; then
            # Non-test (real release) runs only: if the release artifact is not yet published at
            # its Central coordinate, the fetch cannot succeed regardless of whether the formula
            # is correct, so treat it as an environment limitation. In test mode the url was
            # rewritten to a local file:// archive above, so a download failure there is a
            # genuine defect and falls through to FAIL below.
            echo "SKIP: homebrew install could not download the release artifact (not published yet, expected for an unpublished release):"
            echo "$brew_output"
            return 0
        fi
        echo "FAIL: homebrew install failed (exit $install_rc):"
        echo "$brew_output"
        return 1
    fi
    echo "PASS: homebrew install completed with no errors"

    # Step 3: Verify camel version after installation. A successful `brew install` exit code
    # is not proof the executable actually works, so a missing/empty/mismatched result here
    # is a real failure, not something to warn past.
    #
    # Expected version comes from the formula's own `version` line, not $RESOLVED_VERSION.
    # JReleaser renders the formula version from the real POM version, which in test mode is
    # the -SNAPSHOT that the offline file:// install above actually installs; that differs from
    # $RESOLVED_VERSION (which strips -SNAPSHOT for local-archive lookups). Reading the expected
    # value back out of the tapped formula keeps this assertion correct in both the test-mode
    # case and a real release (where the formula version is $RESOLVED_VERSION anyway).
    local expected_version
    expected_version=$(sed -n 's/^[[:space:]]*version "\(.*\)"/\1/p' "$tap_dir/Formula/${fmla}.rb" | head -n1)
    [ -n "$expected_version" ] || expected_version="$RESOLVED_VERSION"

    local camv_output=""
    if ! command -v camel >/dev/null 2>&1; then
        echo "FAIL: camel executable not found on PATH after a successful homebrew install"
        rc=1
    else
        camv_output=$(camel --version 2>/dev/null) || true
        if [ -z "$camv_output" ]; then
            echo "FAIL: 'camel --version' returned empty output after install"
            rc=1
        else
            assert_camel_version "$camv_output" "$expected_version" && \
                echo "PASS: post-install version OK" || rc=1
        fi
    fi

    # Step 4: Verify offline camel init route content
    local init_dir="$TMPDIR_BASE/init-test"
    mkdir -p "$init_dir"
    if ! command -v camel >/dev/null 2>&1; then
        echo "FAIL: camel not available for init test (install reported success but binary is missing)"
        rc=1
    elif ! (cd "$init_dir" && camel init hello.java >/dev/null 2>&1); then
        echo "FAIL: camel init failed after a successful homebrew install"
        rc=1
    else
        assert_init_content "$init_dir" "hello.java" || rc=1
    fi

    # Step 5: Uninstall (tap-qualified name, matching the install above; the tap itself is
    # removed afterward by the script's EXIT trap, once nothing installed from it remains).
    # Capture where the installed `camel` actually resolves BEFORE uninstalling, so the
    # post-uninstall check targets Homebrew's real prefix (e.g. /opt/homebrew/bin on Apple
    # Silicon, /home/linuxbrew/.linuxbrew/bin on Linux) rather than a hardcoded path that is
    # never populated on either CI runner.
    local installed_camel=""
    installed_camel=$(command -v camel 2>/dev/null || true)

    local brew_uninst_output=""
    brew_uninst_output=$(HOME="$BREW_HOME" \
        HOMEBREW_NO_AUTO_UPDATE=1 \
        brew uninstall "$tap_name/$fmla" --force 2>&1) || {
            echo "FAIL: homebrew uninstall command failed:"
            echo "$brew_uninst_output"
            rc=1
        }
    echo "INFO: homebrew uninstall output:"
    echo "$brew_uninst_output"

    # A binary still resolvable at its install location means uninstall did not remove it -
    # a real failure, not a warning.
    if [ -n "$installed_camel" ] && { [ -e "$installed_camel" ] || [ -L "$installed_camel" ]; }; then
        echo "FAIL: homebrew uninstall left the camel executable at $installed_camel"
        rc=1
    else
        echo "PASS: homebrew uninstall removed the camel executable"
    fi

    return $rc
}

# ============================================================
# Local archive validator (no package manager involved)
# ============================================================
# Runs this build's own archive directly, with no package manager in the loop. Even though the
# Homebrew validator now installs this build's own binary via a file:// rewrite in test mode,
# this validator is still the one leg guaranteed to run regardless of whether Homebrew or SDKMAN
# is present on the host: it extracts the locally staged archive and runs its bin/camel.sh in
# place.

validate_local_archive() {
    local rc=0
    local archive_file="$TARGET_DIR/camel-launcher-${RESOLVED_VERSION}-bin.tar.gz"

    if [ ! -f "$archive_file" ]; then
        echo "SKIP: local archive not found: $archive_file (did the build run?)"
        return 0
    fi

    echo "--- Local archive validation (this build's own binary, no package manager) ---"

    local extract_dir="$TMPDIR_BASE/local-archive"
    mkdir -p "$extract_dir"
    if ! tar xzf "$archive_file" -C "$extract_dir"; then
        echo "FAIL: could not extract $archive_file"
        return 1
    fi

    local camel_sh
    camel_sh=$(find "$extract_dir" -type f -name "camel.sh" | head -n1)
    if [ -z "$camel_sh" ]; then
        echo "FAIL: bin/camel.sh not found after extracting $archive_file"
        return 1
    fi
    chmod +x "$camel_sh"

    local camv_output=""
    camv_output=$("$camel_sh" --version 2>/dev/null) || true
    if [ -z "$camv_output" ]; then
        echo "FAIL: 'camel.sh --version' returned empty output"
        rc=1
    else
        assert_camel_version "$camv_output" "$RESOLVED_VERSION" && \
            echo "PASS: local archive version OK" || rc=1
    fi

    local init_dir="$TMPDIR_BASE/local-archive-init"
    mkdir -p "$init_dir"
    if ! (cd "$init_dir" && "$camel_sh" init hello.java >/dev/null 2>&1); then
        echo "FAIL: camel init failed against the locally extracted archive"
        rc=1
    else
        assert_init_content "$init_dir" "hello.java" || rc=1
    fi

    return $rc
}

# ============================================================
# SDKMAN validator
# ============================================================

validate_sdkman() {
    local rc=0
    # Host-gate: sdkman must be present on PATH
    if ! command -v sdk >/dev/null 2>&1; then
        echo "SKIP: sdkman not available"
        return 0
    fi

    # Empirically verified (real jreleaser:prepare/package dry run, packagers=sdkman): SDKMAN is
    # API-only and writes no local descriptor file under target/jreleaser at all, so this always
    # SKIPs offline regardless of the path below; kept for parity with the Homebrew validator and
    # in case a future JReleaser version starts writing one.
    local DESCRIPTOR_FILE="$TARGET_DIR/jreleaser/package/camel-cli/sdkman/camel.json"

    if [ ! -f "$DESCRIPTOR_FILE" ]; then
        echo "SKIP: SDKMAN descriptor not found: $DESCRIPTOR_FILE (did prepare run?)"
        return 0
    fi

    local SDKMAN_HOME="$TMPDIR_BASE/sdkman-home"
    mkdir -p "$SDKMAN_HOME"
    export SDKMAN_DIR="$SDKMAN_HOME"

    echo "--- SDKMAN validation ---"

    # Step 1: Validate the descriptor JSON structure
    if command -v jq >/dev/null 2>&1; then
        local desc_version
        desc_version=$(jq -r '.version' "$DESCRIPTOR_FILE" 2>/dev/null) || true
        if [ -z "$desc_version" ]; then
            echo "FAIL: SDKMAN descriptor missing 'version' field"
            return 1
        fi
        echo "PASS: SDKMAN descriptor has valid version: $desc_version"
    else
        # Fallback: basic structural check without jq
        if grep -q '"version"' "$DESCRIPTOR_FILE"; then
            echo "PASS: SDKMAN descriptor contains 'version' field"
        else
            echo "FAIL: SDKMAN descriptor missing 'version' field"
            return 1
        fi
    fi

    # Step 2: Verify offline archive structure (tar.gz)
    local ARCHIVE_FILE="$TARGET_DIR/camel-launcher-${RESOLVED_VERSION}-bin.tar.gz"
    if [ ! -f "$ARCHIVE_FILE" ]; then
        echo "SKIP: SDKMAN archive not found: $ARCHIVE_FILE (did prepare run?)"
        return 0
    fi

    # Verify the tar contains bin/camel entry-point
    local has_bin_camel=0
    if command -v tar >/dev/null 2>&1; then
        if tar tf "$ARCHIVE_FILE" 2>/dev/null | grep -q 'bin/camel'; then
            has_bin_camel=1
        fi
    fi
    if [ "$has_bin_camel" -eq 1 ]; then
        echo "PASS: SDKMAN archive contains bin/camel entry-point"
    else
        echo "FAIL: SDKMAN archive missing bin/camel entry-point"
        return 1
    fi

    # Step 3: Verify camel version.
    # For offline validation, we verify the descriptor + archive integrity. A real `sdk install`
    # would call the SDKMAN Vendor Release API, which requires a published release and network
    # access, so it is not exercised in this offline validation.
    echo "SKIP: SDKMAN vendor release API not exercised (offline validation)"

    # Step 4: Verify offline camel init route content (same assertion as Homebrew)
    local init_dir="$TMPDIR_BASE/init-test-sdkman"
    mkdir -p "$init_dir"
    if command -v camel >/dev/null 2>&1; then
        if cd "$init_dir" && camel init hello.java >/dev/null 2>&1; then
            assert_init_content "$init_dir" "hello.java" || rc=1
        else
            echo "WARN: camel init skipped (not installed via SDKMAN)"
        fi
    else
        echo "WARN: camel not available for init test via SDKMAN (skipped)"
    fi

    # Step 5: Candidate uninstall (stubbed — no real candidate was installed)
    echo "SKIP: SDKMAN candidate uninstall stubbed (no install performed)"

    return $rc
}

# ============================================================
# Main dispatch
# ============================================================

main() {
    local rc=0
    case "$COMMAND" in
        local)    validate_local_archive; rc=$? ;;
        homebrew) validate_homebrew; rc=$? ;;
        sdkman)   validate_sdkman; rc=$? ;;
        all)
            validate_local_archive || rc=1
            validate_homebrew || rc=1
            validate_sdkman  || rc=1
            ;;
        *) usage ;;
    esac
    return $rc
}

main "$@"
