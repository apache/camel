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

# ── publish-state.sh: redacted state-file helpers for publish workflow ──────
#
# Simple line-oriented flat-key-value store (one key=value per line).
# Sourced by camel-publish.sh.  All public functions use $STATE_FILE or default.

STATE_FILE="${PUBLISH_STATE_FILE:-}"

# POSIX sh has no $'\n' (bash ANSI-C quoting); use a literal newline instead.
_NL='
'

__ensure_file() {
  if [ -z "$STATE_FILE" ]; then
    STATE_FILE="$(pwd)/target/jreleaser/publish-state.json"
    mkdir -p "$(dirname "$STATE_FILE")"
  fi
}

_is_secret_key() {
  case "$(echo "$1" | tr '[:lower:]' '[:upper:]')" in
    *KEY*|*TOKEN*|*SECRET*|*PASS*) return 0 ;;
    *) return 1 ;;
  esac
}

_json_escape() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g; s/\t/\\t/g'
}

_read_val() {
  _rf="$1"; _rk="$2"
  [ -f "$_rf" ] || return 0
  grep "^${_rk}=" "$_rf" 2>/dev/null | head -1 | sed "s|^${_rk}=||"
}

# ── public API ───────────────────────────────────────────────────────────────

state_get() {
  _key="$1"
  __ensure_file; _f="$STATE_FILE"

  if [ ! -f "$_f" ]; then
    return 1
  fi

  val="$(_read_val "$_f" "$_key")"
  # Check if key exists at all (even with empty value)
  if ! grep -q "^${_key}=" "$_f" 2>/dev/null; then
    return 1
  fi

  if _is_secret_key "$_key"; then
    echo "[REDACTED]"
  else
    echo "$val"
  fi
}

state_set() {
  _key="$1"; _val="$2"
  __ensure_file; _f="$STATE_FILE"

  if _is_secret_key "$_key"; then
    echo "Error: cannot store secret-looking key '$_key' in state file." >&2
    return 1
  fi

  __state_pairs=""
  if [ -f "$_f" ]; then
    _found=0
    while IFS= read -r line; do
      [ -z "$line" ] && continue
      k="$(echo "$line" | sed 's|=.*||')"
      if [ "$k" = "$_key" ]; then
        __state_pairs="${__state_pairs}${_key}=$(_json_escape "$_val")${_NL}"
        _found=1
      else
        __state_pairs="${__state_pairs}${line}${_NL}"
      fi
    done < "$_f"
    if [ "$_found" -eq 0 ]; then
      __state_pairs="${__state_pairs}${_key}=$(_json_escape "$_val")${_NL}"
    fi
  else
    printf '%s\n' "${_key}=$(_json_escape "$_val")" > "$_f"
    chmod 600 "$_f"
    return 0
  fi

  printf '%s' "$__state_pairs" | grep -v '^$' > "$_f"
  chmod 600 "$_f"
}

state_mark() {
  _step="$1"; _status="$2"
  state_set "${_step}/status" "$_status"
  state_set "${_step}/timestamp" "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
}

state_resume_ok() {
  __ensure_file; _f="$STATE_FILE"

  if [ ! -f "$_f" ]; then
    echo "no"
    return 0
  fi

  count=0; done_count=0
  while IFS= read -r kline; do
    [ -z "$kline" ] && continue
    k="$(echo "$kline" | sed 's|=.*||')"
    val="$(_read_val "$_f" "$k")"
    count=$((count + 1))
    if [ "$val" = "done" ]; then
      done_count=$((done_count + 1))
    fi
  done <<EOF
$(grep '/status=' "$_f" 2>/dev/null || true)
EOF

  if [ "$count" -gt 0 ] && [ "$count" -eq "$done_count" ]; then
    echo "yes"
  else
    echo "no"
  fi
}

state_current_status() {
  __ensure_file; _f="$STATE_FILE"
  _step="$1"

  [ ! -f "$_f" ] && { echo ""; return 0; }

  val="$(_read_val "$_f" "${_step}/status")"
  echo "$val"
}

state_marked_count() {
  __ensure_file; _f="$STATE_FILE"

  if [ ! -f "$_f" ]; then
    echo "0"
    return 0
  fi

  cnt="$(grep '/status=' "$_f" 2>/dev/null | wc -l | tr -d ' ')"
  echo "${cnt:-0}"
}

state_redacted_dump() {
  __ensure_file; _f="$STATE_FILE"

  if [ ! -f "$_f" ]; then
    echo "{}"
    return 0
  fi

  cat "$_f" | sed -E 's/^(GITHUB_TOKEN|SDKMAN_CONSUMER_KEY|SDKMAN_CONSUMER_SECRET|CHOCO_API_KEY)=.*/\1=[REDACTED]/I'
}

_init_state() {
  _ver="$1"; _ch="$2"
  __ensure_file > /dev/null

  if [ ! -f "$STATE_FILE" ]; then
    _ve="$(printf '%s' "$_ver" | sed 's/\\/\\\\/g; s/"/\\"/g')"
    _ce="$(printf '%s' "$_ch" | sed 's/\\/\\\\/g; s/"/\\"/g')"
    _ts="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    {
      printf 'version=%s\n' "$_ve"
      printf 'channel=%s\n' "$_ce"
      printf 'timestamp_start=%s\n' "$_ts"
    } > "$STATE_FILE"
    chmod 600 "$STATE_FILE"
  fi
}
