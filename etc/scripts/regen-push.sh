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

#
# regen-push.sh — Automatically push regen patches to PRs that need them.
#
# When the CI build regenerates files and finds uncommitted changes, it uploads
# a "regen-patch" artifact. This script discovers those artifacts, downloads the
# patch, and pushes a "Regen" commit to the PR branch.
#
# Works for both same-repo and fork PRs (requires maintainer access and
# "Allow edits from maintainers" enabled on the PR).
#
# Uses ETag-based conditional requests on the Artifacts API to avoid
# unnecessary work when no new regen artifacts have appeared.
#
# Usage:
#   ./regen-push.sh [--repo OWNER/REPO] [--state PATH] [--dry-run]
#
# Environment:
#   GH_TOKEN — GitHub token with repo scope (or gh CLI already authenticated)
#
# Exit codes:
#   0 — success (patches pushed, or nothing to do)
#   1 — error
#
# Designed to run as a cron job (e.g. every 5 minutes). ETag polling makes
# idle ticks free (no API quota consumed on 304).

set -euo pipefail

REPO="apache/camel"
STATE_FILE=""
DRY_RUN=false
VERBOSE=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo)      REPO="$2"; shift 2 ;;
    --state)     STATE_FILE="$2"; shift 2 ;;
    --dry-run)   DRY_RUN=true; shift ;;
    --verbose)   VERBOSE=true; shift ;;
    -h|--help)
      echo "Usage: $0 [--repo OWNER/REPO] [--state PATH] [--dry-run] [--verbose]"
      exit 0
      ;;
    *)           echo "Unknown option: $1" >&2; exit 1 ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# State/ETag files default to XDG_STATE_HOME (~/.local/state) to keep the
# source tree clean. Override with --state if needed.
_STATE_DIR="${XDG_STATE_HOME:-$HOME/.local/state}/regen-push"
mkdir -p "$_STATE_DIR"
STATE_FILE="${STATE_FILE:-$_STATE_DIR/$(echo "$REPO" | tr '/' '-').json}"

log() { echo "$(date -u +%H:%M:%S) $*" >&2; }
debug() { $VERBOSE && log "[debug] $*" || true; }

# Ensure gh CLI is in PATH
export PATH="/opt/data/toolchain/bin:$PATH"

if ! command -v gh &>/dev/null; then
  log "ERROR: gh CLI not found"
  exit 1
fi

# Ensure we have a token
if [[ -z "${GH_TOKEN:-}" ]]; then
  if [[ -f /opt/data/.secrets/gh-token ]]; then
    GH_TOKEN=$(cat /opt/data/.secrets/gh-token)
  else
    GH_TOKEN=$(gh auth token 2>/dev/null || true)
  fi
  if [[ -z "$GH_TOKEN" ]]; then
    log "ERROR: No GitHub token available"
    exit 1
  fi
  export GH_TOKEN
fi

# Initialize state file if missing
if [[ ! -f "$STATE_FILE" ]]; then
  echo '{"pushed": {}}' > "$STATE_FILE"
fi

# ── ETag-based activity check ──────────────────────────────────────────
# Uses the Artifacts API directly with conditional requests (If-Modified-Since).
# The artifacts endpoint returns new results when CI completes with a regen-patch.
# This is a single API call per tick — when nothing changed, GitHub returns 304
# (no quota consumed).

ETAG_FILE="${STATE_FILE%.json}.etag"
CACHED_ETAG=""
[[ -f "$ETAG_FILE" ]] && CACHED_ETAG=$(cat "$ETAG_FILE")

HEADER_TMP=$(mktemp)
BODY_TMP=$(mktemp)
trap "rm -f $HEADER_TMP $BODY_TMP" EXIT

ETAG_HEADER=()
[[ -n "$CACHED_ETAG" ]] && ETAG_HEADER=(-H "If-None-Match: $CACHED_ETAG")

HTTP_CODE=$(curl -s -o "$BODY_TMP" -D "$HEADER_TMP" -w "%{http_code}" \
  -H "Authorization: token $GH_TOKEN" \
  -H "Accept: application/vnd.github+json" \
  "${ETAG_HEADER[@]}" \
  "https://api.github.com/repos/${REPO}/actions/artifacts?name=regen-patch&per_page=5" 2>/dev/null) || true

NEW_ETAG=$(grep -i '^etag:' "$HEADER_TMP" 2>/dev/null \
  | awk '{print $2}' | tr -d '\r\n' || true)
[[ -n "$NEW_ETAG" ]] && echo -n "$NEW_ETAG" > "$ETAG_FILE"

if [[ "$HTTP_CODE" == "304" ]]; then
  debug "No new artifacts (304) — nothing to do"
  exit 0
fi

debug "Artifact list changed (HTTP $HTTP_CODE) — checking for regen-patch artifacts"

# ── Find regen-patch artifacts ─────────────────────────────────────────
# The Artifacts API lets us search by name directly — one call.

ARTIFACTS_JSON=$(gh api "repos/$REPO/actions/artifacts?name=regen-patch&per_page=30" \
  --jq '[.artifacts[] | select(.expired == false) | {
    artifact_id: .id,
    run_id: .workflow_run.id,
    head_sha: .workflow_run.head_sha,
    head_branch: .workflow_run.head_branch,
    created_at: .created_at
  }]' 2>/dev/null) || {
  log "ERROR: Failed to list artifacts"
  exit 1
}

ARTIFACT_COUNT=$(echo "$ARTIFACTS_JSON" | python3 -c "import sys,json; print(len(json.load(sys.stdin)))")
debug "Found $ARTIFACT_COUNT non-expired regen-patch artifacts"

if [[ "$ARTIFACT_COUNT" -eq 0 ]]; then
  exit 0
fi

# ── Process each artifact ──────────────────────────────────────────────
# Write artifacts to a temp file and read with a redirect (not a pipe)
# so the loop runs in the current shell and can update PUSHED_COUNT.

ARTIFACTS_LIST=$(mktemp)
echo "$ARTIFACTS_JSON" | python3 -c "
import sys, json
for a in json.load(sys.stdin):
    print(f\"{a['artifact_id']}|{a['run_id']}|{a['head_sha']}|{a['head_branch']}|{a['created_at']}\")
" > "$ARTIFACTS_LIST"

PUSHED_COUNT=0
ORIG_DIR=$(pwd)

while IFS='|' read -r ARTIFACT_ID RUN_ID HEAD_SHA HEAD_BRANCH CREATED_AT; do

  debug "Checking artifact $ARTIFACT_ID (run $RUN_ID, branch $HEAD_BRANCH)"

  # Skip if already pushed for this run
  ALREADY_PUSHED=$(python3 -c "
import json
state = json.load(open('$STATE_FILE'))
print('yes' if str($RUN_ID) in state.get('pushed', {}) else 'no')
" 2>/dev/null)

  if [[ "$ALREADY_PUSHED" == "yes" ]]; then
    debug "  Already pushed for run $RUN_ID — skipping"
    continue
  fi

  # Find the open PR for this branch
  PR_JSON=$(gh pr list --repo "$REPO" --state open --head "$HEAD_BRANCH" \
    --json number,headRepositoryOwner,headRepository,maintainerCanModify,headRefName \
    --jq '.[0] // empty' 2>/dev/null) || true

  if [[ -z "$PR_JSON" ]]; then
    debug "  No open PR found for branch $HEAD_BRANCH — skipping"
    continue
  fi

  PR_NUMBER=$(echo "$PR_JSON" | python3 -c "import sys,json; print(json.load(sys.stdin)['number'])")
  CAN_MODIFY=$(echo "$PR_JSON" | python3 -c "import sys,json; print(json.load(sys.stdin).get('maintainerCanModify', False))")
  FORK_OWNER=$(echo "$PR_JSON" | python3 -c "import sys,json; print(json.load(sys.stdin)['headRepositoryOwner']['login'])")

  debug "  PR #$PR_NUMBER (fork: $FORK_OWNER, maintainerCanModify: $CAN_MODIFY)"

  if [[ "$CAN_MODIFY" != "True" ]]; then
    log "  PR #$PR_NUMBER: maintainerCanModify=false — skipping"
    continue
  fi

  # Verify this artifact is from the latest run for this PR's head SHA.
  # If the PR was updated after the regen-patch run, the artifact is stale.
  PR_HEAD_SHA=$(gh pr view "$PR_NUMBER" --repo "$REPO" --json headRefOid --jq '.headRefOid' 2>/dev/null)
  if [[ "$PR_HEAD_SHA" != "$HEAD_SHA" ]]; then
    debug "  PR #$PR_NUMBER head SHA ($PR_HEAD_SHA) != artifact SHA ($HEAD_SHA) — stale, skipping"
    continue
  fi

  log "PR #$PR_NUMBER needs regen (run $RUN_ID)"

  if $DRY_RUN; then
    log "  [dry-run] Would download and push regen patch"
    continue
  fi

  # Download the patch
  WORK_DIR=$(mktemp -d)
  if ! gh run download "$RUN_ID" --repo "$REPO" -n regen-patch -D "$WORK_DIR" 2>/dev/null; then
    log "  ERROR: Failed to download regen-patch artifact"
    rm -rf "$WORK_DIR"
    continue
  fi

  PATCH_FILE="$WORK_DIR/regen.patch"
  if [[ ! -s "$PATCH_FILE" ]]; then
    log "  ERROR: Patch file is empty or missing"
    rm -rf "$WORK_DIR"
    continue
  fi

  # Clone the PR branch (shallow, single-branch) into a temp directory.
  # For fork PRs, clone the fork repo directly so we can push to it.
  CLONE_DIR=$(mktemp -d)
  REPO_NAME=$(echo "$REPO" | cut -d/ -f2)
  OWNER=$(echo "$REPO" | cut -d/ -f1)
  if [[ "$FORK_OWNER" == "$OWNER" ]]; then
    CLONE_REPO="$REPO"
  else
    CLONE_REPO="$FORK_OWNER/$REPO_NAME"
  fi

  debug "  Cloning $CLONE_REPO (branch $HEAD_BRANCH) into $CLONE_DIR"

  if ! git clone --depth=1 --branch "$HEAD_BRANCH" \
    "https://x-access-token:${GH_TOKEN}@github.com/${CLONE_REPO}.git" \
    "$CLONE_DIR" 2>/dev/null; then
    log "  ERROR: Failed to clone $CLONE_REPO branch $HEAD_BRANCH"
    rm -rf "$WORK_DIR" "$CLONE_DIR"
    continue
  fi

  cd "$CLONE_DIR"
  git config user.name "github-actions[bot]"
  git config user.email "github-actions[bot]@users.noreply.github.com"

  # Apply the patch.
  # The patch was generated against the merge with the base branch, so it
  # may not apply cleanly. Try with --3way as a fallback.
  if git apply --index "$PATCH_FILE" 2>/dev/null; then
    debug "  Patch applied cleanly"
  elif git apply --index --3way "$PATCH_FILE" 2>/dev/null; then
    log "  Patch applied with 3-way merge"
  else
    log "  ERROR: Patch failed to apply for PR #$PR_NUMBER — skipping"
    cd "$ORIG_DIR"
    rm -rf "$WORK_DIR" "$CLONE_DIR"
    continue
  fi

  # Commit and push
  git commit -m "Regen" >/dev/null 2>&1 || {
    log "  Nothing to commit after applying patch — skipping"
    cd "$ORIG_DIR"
    rm -rf "$WORK_DIR" "$CLONE_DIR"
    continue
  }

  if git push 2>/dev/null; then
    log "✅ PR #$PR_NUMBER — pushed regen commit"
    echo "✅ https://github.com/$REPO/pull/$PR_NUMBER — pushed regen commit"
    PUSHED_COUNT=$((PUSHED_COUNT + 1))

    # Update the <!-- regen-patch --> comment on the PR to mark it resolved
    REGEN_COMMENT_ID=$(gh api "repos/$REPO/issues/$PR_NUMBER/comments" --paginate \
      --jq '[.[] | select(.body | contains("<!-- regen-patch -->"))] | last | .id' 2>/dev/null || true)
    if [[ -n "$REGEN_COMMENT_ID" && "$REGEN_COMMENT_ID" != "null" ]]; then
      RESOLVED_BODY="<!-- regen-patch -->
### :white_check_mark: Generated files have been updated

A regen commit was automatically pushed to this branch. CI will re-run shortly."
      gh api "repos/$REPO/issues/comments/$REGEN_COMMENT_ID" \
        -X PATCH -f body="$RESOLVED_BODY" >/dev/null 2>&1 \
        && debug "  Updated regen-patch comment to resolved" \
        || log "  WARNING: Failed to update regen-patch comment"
    fi

    # Record in state file
    python3 -c "
import json
state = json.load(open('$STATE_FILE'))
state.setdefault('pushed', {})[str($RUN_ID)] = {
    'pr': $PR_NUMBER,
    'branch': '$HEAD_BRANCH',
    'sha': '$HEAD_SHA',
    'pushed_at': '$(date -u +%Y-%m-%dT%H:%M:%SZ)'
}
# Keep only the last 100 entries to avoid unbounded growth
pushed = state['pushed']
if len(pushed) > 100:
    oldest = sorted(pushed, key=lambda k: pushed[k].get('pushed_at', ''))[:len(pushed)-100]
    for k in oldest:
        del pushed[k]
with open('$STATE_FILE', 'w') as f:
    json.dump(state, f, indent=2)
" 2>/dev/null
  else
    log "  ERROR: Failed to push to $CLONE_REPO branch $HEAD_BRANCH"
  fi

  # Cleanup
  cd "$ORIG_DIR"
  rm -rf "$WORK_DIR" "$CLONE_DIR"

done < "$ARTIFACTS_LIST"

rm -f "$ARTIFACTS_LIST"
