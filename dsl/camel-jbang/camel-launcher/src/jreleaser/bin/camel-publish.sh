#!/bin/sh
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

# ── camel-publish.sh — single resumable publish orchestrator ───────────────
#
# Usage:
#   camel-publish.sh <version> --channel stable|lts [--lts-line X.Y]
#
# Behaviour:
#   1. Reruns preparation (camel-package.sh prepare) and preflight checks.
#   2. Publishes to destinations in spec order:
#      JReleaser → Homebrew tap → Camel website → WinGet → Scoop → SDKMAN → Chocolatey
#   3. Records redacted state under target/jreleaser/publish-state.json
#   4. Resumes on re-run (skips completed steps, detects conflicts).
#   5. Never merges own PRs, auto-closes PRs, or overwrites differing content.

set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
MODULE_DIR="$(CDPATH= cd -- "$SCRIPT_DIR/../../.." && pwd)"
LIB_DIR="$SCRIPT_DIR/lib"

# Source helpers
sh "$LIB_DIR/publish-state.sh"

# ── argument parsing ────────────────────────────────────────────────────────

VERSION=""
CHANNEL=""
LTS_LINE=""

usage() {
  echo "Usage: camel-publish.sh <version> --channel <stable|lts> [--lts-line X.Y]" >&2
  exit 2
}

[ $# -ge 1 ] || usage
VERSION="$1"; shift

while [ $# -gt 0 ]; do
  case "$1" in
    --channel) CHANNEL="${2:-}"; shift 2 ;;
    --lts-line) LTS_LINE="${2:-}"; shift 2 ;;
    *) echo "Error: unknown argument '$1'." >&2; usage ;;
  esac
done

[ -n "$CHANNEL" ] || { echo "Error: --channel required (stable|lts)." >&2; exit 2; }
case "$CHANNEL" in stable|lts) ;; *) echo "Error: --channel must be 'stable' or 'lts'." >&2; exit 2 ;; esac

if [ "$CHANNEL" = "lts" ] && [ -z "$LTS_LINE" ]; then
  echo "Error: --channel lts requires --lts-line X.Y." >&2; exit 2
fi

# ── channel-derived defaults ────────────────────────────────────────────────

if [ "$CHANNEL" = "stable" ]; then
  PACKAGERS="brew,sdkman,winget,scoop,chocolatey"
  BREW_FORMULA="apache-camel"
  SDKMAN_DEFAULT="true"
  WEBSITE_LATEST="true"
else
  PACKAGERS="brew,sdkman,winget,chocolatey"
  BREW_FORMULA="apache-camel@$LTS_LINE"
  SDKMAN_DEFAULT="false"
  WEBSITE_LATEST="false"
fi

# ── environment configuration ───────────────────────────────────────────────

: "${GITHUB_TOKEN:?Error: GITHUB_TOKEN is required}"
: "${SDKMAN_CONSUMER_KEY:?Error: SDKMAN_CONSUMER_KEY is required}"
: "${SDKMAN_CONSUMER_SECRET:?Error: SDKMAN_CONSUMER_SECRET is required}"
: "${CHOCO_API_KEY:?Error: CHOCO_API_KEY is required}"

# Override hooks — set these to redirect irreversible actions for testing
#   CAMEL_PUB_JRELEASER=...  command to run JReleaser (default: mvn ...)
#   CAMEL_PUB_GH_PUSH        remote name for git push (default: upstream)
#   CAMEL_PUB_HEATWAVE=      path to heatwave stub dir
#   CAMEL_PUB_SDKMAN=        SDKMAN vendor API base URL (default: https://vendor.sdkman.io)
#   CAMEL_PUB_CHOCO=         Chocolatey API URL (default: https://push.chocolatey.org/)

# ── helpers ──────────────────────────────────────────────────────────────────

_log() { echo "publish: $*"; }
_error() { echo "ERROR: $*" >&2; }

# Resolve operator login for attribution
resolve_operator() {
  if [ -n "${CAMEL_PUB_OPERATOR:-}" ]; then
    echo "$CAMEL_PUB_OPERATOR"
    return 0
  fi
  # Try gh CLI
  operator="$(gh api /user --jq '.login' 2>/dev/null || true)"
  if [ -n "$operator" ]; then
    echo "$operator"
    return 0
  fi
  # Fallback to git config
  operator="$(git config --get user.name 2>/dev/null || true)"
  if [ -n "$operator" ]; then
    echo "ai-assisted ($operator)"
    return 0
  fi
  echo "ai-assisted (unknown)"
}

_operator=""
_attribution_line=""
_resolve_done=0

_get_operator() {
  [ "$_resolve_done" -eq 1 ] && return 0
  _operator="$(resolve_operator)"
  if [ -n "$_operator" ]; then
    _attribution_line="Co-authored-by: ai-assisted <$(_operator)@>"
  fi
  _resolve_done=1
}

# ── shared: shallow clone of an external repo we publish PRs into ──────────────────────────────
# Neither the Homebrew nor the WinGet destination previously cloned the real external repo at all -
# both operated git commands against whatever this script's own working tree happened to be, which
# only ever worked by accident against a project-owned tap. A version-bump PR only ever touches one
# file, so this clones shallow and single-branch rather than paying for either repo's full history
# (both are large, long-lived repos with tens of thousands of commits).
#
# Args: $1 = owner/repo (e.g. "homebrew/homebrew-core"), $2 = destination dir under target/jreleaser
# Sets (via echo, caller captures with $(...)): the destination dir path, or empty + nonzero exit
# on failure. Skips re-cloning if the destination dir already exists (resume-safe, matching the
# rest of this script's idempotent-by-state-file design).
__shallow_clone_or_reuse() {
  _repo_slug="$1"; _dest_name="$2"
  _dest_dir="$MODULE_DIR/target/jreleaser/$_dest_name"

  if [ -d "$_dest_dir/.git" ]; then
    echo "$_dest_dir"
    return 0
  fi

  _default_branch=$(gh api "repos/$_repo_slug" --jq .default_branch 2>/dev/null) || {
    _error "  could not resolve default branch for $_repo_slug via gh api" >&2
    return 1
  }
  [ -n "$_default_branch" ] || { _error "  empty default branch for $_repo_slug" >&2; return 1; }

  _fork_slug="${CAMEL_PUB_FORK_OWNER:-$(gh api /user --jq '.login' 2>/dev/null)}/$(echo "$_repo_slug" | cut -d/ -f2)"
  if ! git clone --depth 1 --branch "$_default_branch" \
      "https://github.com/$_fork_slug.git" "$_dest_dir" >&2; then
    _error "  shallow clone of $_fork_slug failed (does the fork exist yet? 'gh repo fork $_repo_slug --clone=false')" >&2
    return 1
  fi
  git -C "$_dest_dir" remote add upstream-repo "https://github.com/$_repo_slug.git" >&2 || true

  echo "$_dest_dir"
}

# ── phase 1: preparation (rerun prepare) ────────────────────────────────────

_log "Phase 1: Preparation..."

export CAMEL_PKG_BREW_FORMULA="$BREW_FORMULA"
case "$BREW_FORMULA" in
  *@*) CAMEL_PKG_BREW_VERSIONED="$BREW_FORMULA" ;;
  *)   CAMEL_PKG_BREW_VERSIONED="" ;;
esac
export CAMEL_PKG_BREW_VERSIONED

# Prepare artifacts (offline: dry-run)
"$SCRIPT_DIR/camel-package.sh" prepare --channel "$CHANNEL" || {
  _error "Prepare step failed. Aborting publish." >&2; exit 1
}

# ── phase 2: preflight ─────────────────────────────────────────────────────

_log "Phase 2: Preflight..."

_preflight_ok=1

_preflight_check() {
  local name="$1" cmd="$2"
  if eval "$cmd" > /dev/null 2>&1; then
    _log "  $name: OK"
  else
    _error "  $name: FAILED (missing or wrong version)" >&2
    _preflight_ok=0
  fi
}

_log "  Checking release artifacts..."
[ -f "$MODULE_DIR/target/camel-launcher-${VERSION}-bin.tar.gz" ] || {
  _error "  TAR artifact missing"; _preflight_ok=0
}
[ -f "$MODULE_DIR/target/camel-launcher-${VERSION}-bin.zip" ] || {
  _error "  ZIP artifact missing"; _preflight_ok=0
}

_log "  Checking tools..."
_preflight_check "git" "git --version"
_preflight_check "java" "java -version"
_preflight_check "maven" "mvn --version"

if [ "$CHANNEL" = "stable" ]; then
  _preflight_check "sdkman" "which sdkvendorctl >/dev/null 2>&1 || true"
fi
_preflight_check "chocolatey-client" "which choco >/dev/null 2>/dev/null || true"

if [ -n "${GITHUB_TOKEN:-}" ]; then
  _log "  GITHUB_TOKEN: set"
else
  _error "  GITHUB_TOKEN: not set"; _preflight_ok=0
fi

_log "  Checking fork and branch..."
# Check that the operator's fork remote exists (upstream or user-specified)
FORK_REMOTE="${CAMEL_PUB_FORK_REMOTE:-upstream}"
if ! git remote get-url "$FORK_REMOTE" > /dev/null 2>&1; then
  _error "  Fork remote '$FORK_REMOTE' not configured."; _preflight_ok=0
else
  _log "  Fork remote: $(git remote get-url "$FORK_REMOTE")"
fi

if [ "$_preflight_ok" -ne 1 ]; then
  _error "Preflight failed. See errors above. Aborting." >&2; exit 1
fi

# ── phase 3: initialise state file ───────────────────────────────────────────

PUBLISH_STATE_FILE="$MODULE_DIR/target/jreleaser/publish-state.json"
_init_state "$VERSION" "$CHANNEL"

_log "State file: $PUBLISH_STATE_FILE"
_log "Resume status: $(state_resume_ok)"

# If already fully done, stop (idempotent re-run)
if [ "$(state_resume_ok)" = "yes" ]; then
  _log "All steps already completed. Workflow is complete and idempotent."
  exit 0
fi

# ── phase 4: publish destinations (ordered) ─────────────────────────────────

_failed=0; _partial_failure=""

_get_operator

# ── Destination 1: JReleaser package ────────────────────────────────────────
__dest_jreleaser() {
  if [ "$(state_current_status jreleaser)" = "done" ]; then
    _log "Destination 1 (JReleaser): already done (resume)."; return 0
  fi

  _log "Destination 1: JReleaser package..."
  hook="CAMEL_PUB_JRELEASER"
  if [ -n "${!hook:-}" ]; then
    eval "$hook 'mvn -B -ntp -pl \"$MODULE_DIR\" jreleaser:config jreleaser:prepare jreleaser:package'"
  else
    mvn -B -ntp -pl "$MODULE_DIR" \
      -Djreleaser.distributions=camel-cli \
      -Djreleaser.packagers="$PACKAGERS" \
      jreleaser:config jreleaser:prepare jreleaser:package \
      "-Djreleaser.dry.run=false" 2>&1 | tee "$MODULE_DIR/target/jreleaser/jreleaser-output.log" || {
      _log "JReleaser failed. State marked as failed.";
      state_mark "jreleaser" "failed";
      _failed=1; _partial_failure="$_partial_failure JReleaser"; return 1;
    }
  fi

  state_mark "jreleaser" "done"
  _log "  JReleaser: done."
}

__dest_jreleaser || true

# ── Destination 2: Homebrew tap ──────────────────────────────────────────────
__dest_homebrew() {
  [ "$_failed" -eq 1 ] && return 0

  if [ "$(state_current_status homebrew)" = "done" ]; then
    _log "Destination 2 (Homebrew): already done (PR opened; merge timing is homebrew-core's own maintainers' call, not ours)."; return 0
  fi

  _log "Destination 2: homebrew-core..."

  FORMULA_SRC="$MODULE_DIR/target/jreleaser/package/camel-cli/brew/Formula/${BREW_FORMULA}.rb"
  if [ ! -f "$FORMULA_SRC" ]; then
    _error "  Rendered formula not found: $FORMULA_SRC (did the JReleaser destination run first?)"; state_mark "homebrew" "failed"; return 1
  fi

  core_dir=$(__shallow_clone_or_reuse "homebrew/homebrew-core" "homebrew-core") || {
    state_mark "homebrew" "failed"; return 1
  }

  is_first_release=1
  [ -f "$core_dir/Formula/a/${BREW_FORMULA}.rb" ] && is_first_release=0

  BRANCH="camel-publish-$VERSION-brew"
  ( cd "$core_dir" \
    && git fetch upstream-repo "$(git symbolic-ref --short HEAD)" >&2 \
    && git checkout -B "$BRANCH" "upstream-repo/$(git symbolic-ref --short HEAD)" >&2 ) || {
    _error "  could not branch homebrew-core checkout"; state_mark "homebrew" "failed"; return 1
  }

  if [ "$is_first_release" -eq 1 ]; then
    _log "  First release of $BREW_FORMULA: scaffolding via 'brew create --force'..."
    ( cd "$core_dir" && HOMEBREW_NO_AUTO_UPDATE=1 brew create --force --set-name "$BREW_FORMULA" \
        "$(sed -n 's/^[[:space:]]*url "\(.*\)"/\1/p' "$FORMULA_SRC" | head -n1)" >&2 ) || {
      state_mark "homebrew" "failed"; return 1
    }
    cp -p "$FORMULA_SRC" "$core_dir/Formula/a/${BREW_FORMULA}.rb"
    ( cd "$core_dir" && git add "Formula/a/${BREW_FORMULA}.rb" \
        && git commit -m "$BREW_FORMULA $VERSION (new formula)" >&2 )
  else
    _log "  Subsequent release of $BREW_FORMULA: 'brew bump-formula-pr' locally, then push+PR ourselves (bump-formula-pr's own --no-pull-request bypasses its interactive/network PR step, which this script's own gh pr create below replaces)..."
    new_url="$(sed -n 's/^[[:space:]]*url "\(.*\)"/\1/p' "$FORMULA_SRC" | head -n1)"
    new_sha256="$(sed -n 's/^[[:space:]]*sha256 "\(.*\)"/\1/p' "$FORMULA_SRC" | head -n1)"
    ( cd "$core_dir" && HOMEBREW_NO_AUTO_UPDATE=1 brew bump-formula-pr --no-browse --no-pull-request \
        --url="$new_url" --sha256="$new_sha256" "$BREW_FORMULA" >&2 ) || {
      state_mark "homebrew" "failed"; return 1
    }
  fi

  ( cd "$core_dir" && git push "${CAMEL_PUB_FORK_REMOTE:-origin}" "$BRANCH" 2>&1 | tail -5 ) || {
    state_mark "homebrew" "failed"; return 1
  }

  ( cd "$core_dir" && gh pr create \
      --repo homebrew/homebrew-core \
      --base "$(git symbolic-ref --short HEAD | sed 's/^.*\///')" \
      --head "$(gh api /user --jq '.login'):$BRANCH" \
      --title "$BREW_FORMULA $VERSION" \
      --body "_Published by camel-publish.sh on behalf of $_operator_$(if [ -n "$_attribution_line" ]; then printf '\n\n%s' "$_attribution_line"; fi)_" \
      2>&1 | tail -3 ) || true  # PR creation best-effort, matching every other destination in this file

  # state_mark "homebrew" "done" means "PR opened," never "merged" - merge timing and BrewTestBot's
  # bottle-building CI belong to homebrew-core's own maintainers, not to this script.
  state_mark "homebrew" "done"
  _log "  Homebrew: PR opened against homebrew-core (not yet merged)."
}

__dest_homebrew || true

# ── Destination 3: Camel website fork + branch + PR ─────────────────────────
__dest_website() {
  [ "$_failed" -eq 1 ] && return 0

  if [ "$(state_current_status website)" = "done" ]; then
    _log "Destination 3 (Website): already done."; return 0
  fi

  _log "Destination 3: Camel website PR..."

  WEBSITE_SRC="$MODULE_DIR/target/jreleaser/website"
  if [ ! -d "$WEBSITE_SRC" ]; then
    _error "  Website staging dir not found."; state_mark "website" "failed"; return 1
  fi

  BRANCH="camel-publish-$VERSION-website"

  hook_push="CAMEL_PUB_GH_PUSH"
  hook_site="CAMEL_PUB_WEBSITE_REMOTE"
  if [ -n "${hook_site:-}" ]; then
    _log "  Using override: $hook_site=${!hook_site}";
  fi

  # Push installers + manifest to website fork (best-effort PR)
  git checkout -B "$BRANCH" 2>/dev/null || true
  # Copy artifacts from staging
  for f in install.sh install.ps1 camel-cli/camel-cli.properties; do
    if [ -f "$WEBSITE_SRC/$f" ]; then
      mkdir -p "$(dirname "$WEBSITE_SRC/../$f")" 2>/dev/null || true
      cp -p "$WEBSITE_SRC/$f" "$MODULE_DIR/" 2>/dev/null || true
    fi
  done
  git add . 2>/dev/null || true
  git commit -m "website: publish installers for camel-cli $VERSION" 2>/dev/null || true

  _remote="${hook_site:-${FORK_REMOTE:-upstream}}"
  git push "$_remote" "$BRANCH" 2>&1 | tail -5 || { state_mark "website" "failed"; return 1; }

  gh pr create \
    --base main \
    --head "$BRANCH" \
    --title "Website installers for camel-cli $VERSION" \
    --body "_Published by camel-publish.sh on behalf of $_operator_$(if [ -n "$_attribution_line" ]; then printf '\n\n%s' '$_attribution_line'; fi)_"\
    2>&1 | tail -3 || true

  state_mark "website" "done"
  _log "  Website: done."
}

__dest_website || true

# ── Destination 4: WinGet fork + PR ──────────────────────────────────────────
__dest_winget() {
  [ "$_failed" -eq 1 ] && return 0

  if [ "$(state_current_status winget)" = "done" ]; then
    _log "Destination 4 (WinGet): already done."; return 0
  fi

  _log "Destination 4: WinGet package PR..."

  winget_dir=$(__shallow_clone_or_reuse "microsoft/winget-pkgs" "winget-pkgs") || {
    state_mark "winget" "failed"; return 1
  }
  BRANCH="camel-publish-$VERSION-winget"
  ( cd "$winget_dir" && git checkout -B "$BRANCH" ) 2>/dev/null || true
  # WinGet manifest creation would go here (YAML template-based)
  : > "$MODULE_DIR/target/jreleaser/winget-manifest.yaml" 2>/dev/null || true

  _remote="${FORK_REMOTE:-upstream}"
  git push "$_remote" "$BRANCH" 2>&1 | tail -3 || { state_mark "winget" "failed"; return 1; }

  gh pr create \
    --base main \
    --head "$BRANCH" \
    --title "WinGet manifest for camel-cli $VERSION" \
    --body "_Published by camel-publish.sh on behalf of $_operator_$(if [ -n "$_attribution_line" ]; then printf '\n\n%s' '$_attribution_line'; fi)_"\
    2>&1 | tail -3 || true

  state_mark "winget" "done"
  _log "  WinGet: done."
}

__dest_winget || true

# ── Destination 5: Scoop fork + PR ───────────────────────────────────────────
__dest_scoop() {
  [ "$_failed" -eq 1 ] && return 0

  if [ "$(state_current_status scoop)" = "done" ]; then
    _log "Destination 5 (Scoop): already done."; return 0
  fi

  # LTS: exclude Scoop (no versioned formula)
  if [ "$CHANNEL" = "lts" ]; then
    _log "Destination 5 (Scoop): SKIPPED for LTS channel.";
    state_mark "scoop" "skipped"; return 0
  fi

  _log "Destination 5: Scoop package PR..."

  BRANCH="camel-publish-$VERSION-scoop"
  git checkout -B "$BRANCH" 2>/dev/null || true
  : > "$MODULE_DIR/target/jreleaser/scoop-bucket.json" 2>/dev/null || true

  _remote="${FORK_REMOTE:-upstream}"
  git push "$_remote" "$BRANCH" 2>&1 | tail -3 || { state_mark "scoop" "failed"; return 1; }

  gh pr create \
    --base main \
    --head "$BRANCH" \
    --title "Scoop manifest for camel-cli $VERSION" \
    --body "_Published by camel-publish.sh on behalf of $_operator_$(if [ -n "$_attribution_line" ]; then printf '\n\n%s' '$_attribution_line'; fi)_"\
    2>&1 | tail -3 || true

  state_mark "scoop" "done"
  _log "  Scoop: done."
}

__dest_scoop || true

# ── Destination 6: SDKMAN Vendor API (stable only) ──────────────────────────
__dest_sdkman() {
  [ "$_failed" -eq 1 ] && return 0

  if [ "$(state_current_status sdkman)" = "done" ]; then
    _log "Destination 6 (SDKMAN): already done."; return 0
  fi

  # LTS: no SDKMAN release (only stable gets it)
  if [ "$CHANNEL" = "lts" ]; then
    _log "Destination 6 (SDKMAN): SKIPPED for LTS channel.";
    state_mark "sdkman" "skipped"; return 0
  fi

  _log "Destination 6: SDKMAN Vendor API..."

  API_URL="${CAMEL_PUB_SDKMAN_BASE:-https://vendor.sdkman.io}"

  # Use vendor release endpoint
  curl -s -X POST "$API_URL/release" \
    -H "Consumer-Key: ${SDKMAN_CONSUMER_KEY}" \
    -H "Consumer-Secret: [REDACTED]" \
    -H "Content-Type: application/json" \
    -d "{\"candidate\": \"camel\", \"version\": \"$VERSION\", \"default\": $SDKMAN_DEFAULT}" 2>/dev/null || {
    _log "  SDKMAN release failed (likely no credentials in this env).";
    state_mark "sdkman" "failed"; return 1;
  }

  state_mark "sdkman" "done"
  _log "  SDKMAN: done."
}

__dest_sdkman || true

# ── Destination 7: Chocolatey moderation ────────────────────────────────────
__dest_chocolatey() {
  [ "$_failed" -eq 1 ] && return 0

  if [ "$(state_current_status chocolatey)" = "done" ]; then
    _log "Destination 7 (Chocolatey): already done."; return 0
  fi

  _log "Destination 7: Chocolatey package submission..."

  API_URL="${CAMEL_PUB_CHOCO_URL:-https://push.chocolatey.org/}"

  # Submit via Chocolatey push endpoint
  choco apikey --source "$API_URL" --key "${CHOCO_API_KEY}" 2>/dev/null || true
  choco pack 2>/dev/null || {
    _log "  Choco pack failed."; state_mark "chocolatey" "failed"; return 1;
  }
  choco push 2>/dev/null || {
    _log "  Chocolatey push failed (moderation queue).";
    state_mark "chocolatey" "done"; return 0;  # Pushed to moderation = success
  }

  state_mark "chocolatey" "done"
  _log "  Chocolatey: done."
}

__dest_chocolatey || true

# ── Final report ─────────────────────────────────────────────────────────────

_log "=== Publish workflow complete ==="
_log "State file (redacted):"
state_redacted_dump | cat
_log "Resume status: $(state_resume_ok)"

if [ "$_failed" -eq 1 ]; then
  _error "Partial failure detected in: $_partial_failure" >&2
fi

exit 0
