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

SCRIPT_DIR=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
MODULE_DIR=$(CDPATH='' cd -- "$SCRIPT_DIR/../../.." && pwd)
SUPPORTED_LTS="$SCRIPT_DIR/../supported-lts.yml"

# Tests may point this at a synthetic LTS allowlist so expiry-date assertions don't ride on
# real production supportEnds dates (mirrors CAMEL_PACKAGE_TEST_VERSION further below).
if [ -n "${CAMEL_PACKAGE_TEST_SUPPORTED_LTS:-}" ]; then
  if [ "${CAMEL_PACKAGE_TEST_MODE:-}" != "true" ]; then
    echo "Error: CAMEL_PACKAGE_TEST_SUPPORTED_LTS requires CAMEL_PACKAGE_TEST_MODE=true." 1>&2
    exit 2
  fi
  SUPPORTED_LTS="$CAMEL_PACKAGE_TEST_SUPPORTED_LTS"
fi

usage() {
  echo "Usage: camel-package.sh <prepare|publish> --channel <stable|lts> [--lts-line X.Y] [--print-plan]" 1>&2
  exit 2
}

SUBCOMMAND=""
CHANNEL=""
LTS_LINE=""
PRINT_PLAN=0

[ $# -ge 1 ] || usage
SUBCOMMAND="$1"; shift
case "$SUBCOMMAND" in
  prepare|publish) ;;
  *) echo "Error: unknown subcommand '$SUBCOMMAND'." 1>&2; usage ;;
esac

# Each value-taking option checks for its value before shifting past it: a bare
# `shift 2` with only one argument left fails, and `set -e` would then kill the script
# silently with exit 1 instead of reporting the usage error.
require_value() {
  [ $# -ge 2 ] || { echo "Error: '$1' requires a value." 1>&2; usage; }
}

while [ $# -gt 0 ]; do
  case "$1" in
    --channel) require_value "$@"; CHANNEL="$2"; shift 2 ;;
    --lts-line) require_value "$@"; LTS_LINE="$2"; shift 2 ;;
    --print-plan) PRINT_PLAN=1; shift ;;
    *) echo "Error: unknown argument '$1'." 1>&2; usage ;;
  esac
done

case "$CHANNEL" in
  stable|lts) ;;
  *) echo "Error: --channel must be 'stable' or 'lts' (got '$CHANNEL')." 1>&2; usage ;;
esac

if [ "$CHANNEL" = "lts" ] && [ -z "$LTS_LINE" ]; then
  echo "Error: --channel lts requires --lts-line X.Y." 1>&2
  usage
fi

# Validate --lts-line against the allowlist and its support-end date.
if [ -n "$LTS_LINE" ]; then
  if [ ! -r "$SUPPORTED_LTS" ]; then
    echo "Error: supported LTS metadata is not readable: $SUPPORTED_LTS" 1>&2
    exit 1
  fi
  if ! grep -Eq '^[[:space:]]*-[[:space:]]+line:' "$SUPPORTED_LTS"; then
    echo "Error: supported LTS metadata is malformed: $SUPPORTED_LTS" 1>&2
    exit 1
  fi
  today=$(date +%F)
  supported_ends=$(awk -v line="$LTS_LINE" '
    $1 == "-" && $2 == "line:" { cur = $3; gsub(/"/, "", cur) }
    $1 == "supportEnds:" && cur == line { v = $2; gsub(/"/, "", v); print v; exit }
  ' "$SUPPORTED_LTS")
  if [ -z "$supported_ends" ]; then
    echo "Error: '$LTS_LINE' is not a supported LTS line (see supported-lts.yml)." 1>&2
    exit 2
  fi
  # ISO-8601 dates compare correctly as strings.
  if expr "$today" \> "$supported_ends" > /dev/null; then
    echo "Error: LTS line '$LTS_LINE' support ended on $supported_ends." 1>&2
    exit 2
  fi
fi

# --- Channel -> packaging plan ---
if [ "$CHANNEL" = "stable" ]; then
  PACKAGERS="brew,sdkman,winget,scoop,chocolatey"
  BREW_FORMULA="apache-camel"
  BREW_CLASS="ApacheCamel"
  SDKMAN_DEFAULT="true"
  BREW_LTS_FORMULA=""
  WEBSITE_LATEST="true"
  # The maven-metadata.xml <release> tag always reflects the newest published release across every
  # line, which is exactly "latest stable" for this formula - no line-prefix filtering needed.
  CAMEL_PKG_BREW_LIVECHECK_REGEX='<release>([0-9]+\.[0-9]+\.[0-9]+)<\/release>'
  [ -n "$LTS_LINE" ] && BREW_LTS_FORMULA="apache-camel@$LTS_LINE"
else
  # Homebrew's own versioned-formula convention names the *file* "apache-camel@X.Y.rb" but the
  # Ruby *class* "ApacheCamelATxy" (dot removed) - e.g. real homebrew-core "python@3.11.rb"
  # contains "class PythonAT311", and the real homebrew-core "apache-flink@1.rb" contains
  # "class ApacheFlinkAT1" (confirmed by reading both directly from homebrew-core during this
  # plan's research). As of the pinned JReleaser plugin version in pom.xml, JReleaser does not
  # apply this convention itself: a literal formulaName "apache-camel@4.20" renders invalid Ruby
  # (`class ApacheCamel@4.20 < Formula`) and a wrong output filename ("20.rb"). So BREW_CLASS below
  # is passed as formulaName (giving valid Ruby), and JReleaser's output file (itself kebab-cased
  # from that class name, e.g. "apache-camel-at-420.rb") is renamed to the real
  # "apache-camel@X.Y.rb" after packaging - see the rename step below the mvn invocation.
  PACKAGERS="brew,sdkman,winget,chocolatey"
  BREW_FORMULA="apache-camel@$LTS_LINE"
  BREW_CLASS="ApacheCamelAT$(echo "$LTS_LINE" | tr -d '.')"
  SDKMAN_DEFAULT="false"
  BREW_LTS_FORMULA=""
  WEBSITE_LATEST="false"
  # maven-metadata.xml lists every published version, not just the newest, so the LTS formula's
  # livecheck must filter to this LTS line's own X.Y prefix (escaping the dot so it matches
  # literally, not "any character") or it would report the project's overall latest release
  # instead of this line's own latest patch.
  lts_line_escaped=$(echo "$LTS_LINE" | sed 's/\./\\./g')
  CAMEL_PKG_BREW_LIVECHECK_REGEX="<version>(${lts_line_escaped}\.[0-9]+)<\/version>"
  # deprecate! fires 6 months before the line's own documented support end; disable! fires on the
  # support-end date itself. supported_ends was already resolved and validated (non-empty, a real
  # entry in supported-lts.yml) by the --lts-line validation earlier in this script - re-used here
  # rather than re-parsed.
  CAMEL_PKG_BREW_DEPRECATE_DATE=$(date -u -d "$supported_ends -6 months" +%F 2>/dev/null \
    || date -u -j -v-6m -f %Y-%m-%d "$supported_ends" +%F)
  CAMEL_PKG_BREW_DISABLE_DATE="$supported_ends"
fi
export CAMEL_PKG_BREW_LIVECHECK_REGEX
if [ "$CHANNEL" = "lts" ]; then
  export CAMEL_PKG_BREW_DEPRECATE_DATE
  export CAMEL_PKG_BREW_DISABLE_DATE
fi

if [ "$PRINT_PLAN" -eq 1 ]; then
  echo "CHANNEL=$CHANNEL"
  echo "LTS_LINE=$LTS_LINE"
  echo "PACKAGERS=$PACKAGERS"
  echo "SDKMAN_CANDIDATE=camel"
  echo "SDKMAN_DEFAULT=$SDKMAN_DEFAULT"
  echo "WEBSITE_VERSION_MANIFEST=true"
  echo "WEBSITE_LATEST=$WEBSITE_LATEST"
  [ -n "$BREW_LTS_FORMULA" ] && echo "BREW_LTS_FORMULA=$BREW_LTS_FORMULA"
  [ -n "$BREW_FORMULA" ] && echo "BREW_FORMULA=$BREW_FORMULA"
  [ -n "$BREW_CLASS" ] && echo "BREW_CLASS=$BREW_CLASS"
  exit 0
fi

if [ "$SUBCOMMAND" = "publish" ]; then
  # Publication is intentionally unimplemented: where each packager's artifacts get
  # pushed (e.g. Homebrew to the project's own tap vs. homebrew-core) is a decision
  # for the Apache Camel PMC, not something this script should default on its own.
  # This also covers the Homebrew dual-formula gap noted in jreleaser.yml above -
  # both are blocked on that same publish-destination decision.
  echo "Error: 'publish' is not yet implemented; awaiting a PMC decision on publish destinations." 1>&2
  exit 2
fi

# --- prepare: no remote mutation, no credentials ---

# Resolve the release version. Production always reads Maven's project.version; tests/CI may
# override it, but only with both CAMEL_PACKAGE_TEST_MODE=true and CAMEL_PACKAGE_TEST_VERSION set,
# so production can never accidentally skip the real Maven version.
if [ -n "${CAMEL_PACKAGE_TEST_VERSION:-}" ]; then
  if [ "${CAMEL_PACKAGE_TEST_MODE:-}" != "true" ]; then
    echo "Error: CAMEL_PACKAGE_TEST_VERSION requires CAMEL_PACKAGE_TEST_MODE=true." 1>&2
    exit 2
  fi
  PROJECT_VERSION="$CAMEL_PACKAGE_TEST_VERSION"
else
  PROJECT_VERSION=$(mvn -q -B -ntp -f "$MODULE_DIR/pom.xml" org.apache.maven.plugins:maven-help-plugin:3.5.1:evaluate \
    -Dexpression=project.version -DforceStdout)
fi

case "$PROJECT_VERSION" in
  *-SNAPSHOT)
    echo "Error: refusing to prepare packages for a snapshot version '$PROJECT_VERSION'." 1>&2
    exit 2
    ;;
esac

if [ -n "${CAMEL_PACKAGE_TEST_WINGET_REMOTE:-}" ] && [ "${CAMEL_PACKAGE_TEST_MODE:-}" != "true" ]; then
  echo "Error: CAMEL_PACKAGE_TEST_WINGET_REMOTE requires CAMEL_PACKAGE_TEST_MODE=true." 1>&2
  exit 2
fi

# Locate the release artifacts and canonical website installer sources.
TAR="$MODULE_DIR/target/camel-launcher-$PROJECT_VERSION-bin.tar.gz"
ZIP="$MODULE_DIR/target/camel-launcher-$PROJECT_VERSION-bin.zip"
WINGET_ZIP="$MODULE_DIR/target/camel-launcher-$PROJECT_VERSION-winget-bin.zip"
WINGET_URL="https://archive.apache.org/dist/camel/apache-camel/$PROJECT_VERSION/$(basename -- "$WINGET_ZIP")"
INSTALL_SH_SRC="$MODULE_DIR/src/install/install.sh"
INSTALL_PS1_SRC="$MODULE_DIR/src/install/install.ps1"

if [ ! -f "$TAR" ]; then
  echo "Error: release TAR not found: $TAR" 1>&2
  exit 1
fi
if [ ! -f "$ZIP" ]; then
  echo "Error: release ZIP not found: $ZIP" 1>&2
  exit 1
fi
if [ ! -f "$WINGET_ZIP" ]; then
  echo "Error: WinGet release ZIP not found: $WINGET_ZIP" 1>&2
  exit 1
fi
if [ ! -f "$INSTALL_SH_SRC" ]; then
  echo "Error: installer source not found: $INSTALL_SH_SRC" 1>&2
  exit 1
fi
if [ ! -f "$INSTALL_PS1_SRC" ]; then
  echo "Error: installer source not found: $INSTALL_PS1_SRC" 1>&2
  exit 1
fi

archived_winget=$(mktemp)
cleanup_archived_winget() {
  rm -f "$archived_winget"
}
trap cleanup_archived_winget EXIT

if [ -n "${CAMEL_PACKAGE_TEST_WINGET_REMOTE:-}" ]; then
  cp "$CAMEL_PACKAGE_TEST_WINGET_REMOTE" "$archived_winget"
elif ! curl -fsSL -o "$archived_winget" "$WINGET_URL"; then
  echo "Error: archived WinGet payload is not available at $WINGET_URL" 1>&2
  exit 1
fi

if ! cmp -s "$WINGET_ZIP" "$archived_winget"; then
  echo "Error: local WinGet ZIP does not match the archived WinGet payload at $WINGET_URL" 1>&2
  exit 1
fi

cleanup_archived_winget
trap - EXIT

# Recreate only the prepared website staging directory (leave the rest of target/jreleaser alone).
WEBSITE_DIR="$MODULE_DIR/target/jreleaser/website"
rm -rf "$WEBSITE_DIR"
mkdir -p "$WEBSITE_DIR"

cp -p "$INSTALL_SH_SRC" "$WEBSITE_DIR/install.sh"
cp -p "$INSTALL_PS1_SRC" "$WEBSITE_DIR/install.ps1"

if ! cmp -s "$INSTALL_SH_SRC" "$WEBSITE_DIR/install.sh"; then
  echo "Error: copied install.sh does not match its source." 1>&2
  exit 1
fi
if ! cmp -s "$INSTALL_PS1_SRC" "$WEBSITE_DIR/install.ps1"; then
  echo "Error: copied install.ps1 does not match its source." 1>&2
  exit 1
fi

if ! java "$MODULE_DIR/src/jreleaser/java/WebsiteManifestGenerator.java" \
    --version "$PROJECT_VERSION" --tar "$TAR" --zip "$ZIP" \
    --install-sh "$WEBSITE_DIR/install.sh" --install-ps1 "$WEBSITE_DIR/install.ps1" \
    --output "$WEBSITE_DIR/camel-cli" \
    --latest "$WEBSITE_LATEST"; then
  echo "Error: website manifest generation failed." 1>&2
  exit 1
fi

# jreleaser.yml reads the Homebrew formula name via `{{ Env.CAMEL_PKG_BREW_FORMULA }}`,
# which becomes the generated Ruby class name directly (`class {{brewFormulaName}}`), so
# this must be BREW_CLASS (a valid Ruby class name), not the human-facing BREW_FORMULA.
# JReleaser's `Env.` template prefix resolves real OS environment variables
# (java.lang.System#getenv), not Maven -D system properties, so this must be
# exported rather than passed as -D.
export CAMEL_PKG_BREW_FORMULA="$BREW_CLASS"

# Non-empty only for a versioned formula (e.g. "camel@4.20"); formula.rb.tpl uses
# this to add `keg_only :versioned_formula` and its PATH caveat.
case "$BREW_FORMULA" in
  *@*) CAMEL_PKG_BREW_VERSIONED="$BREW_FORMULA" ;;
  *) CAMEL_PKG_BREW_VERSIONED="" ;;
esac
export CAMEL_PKG_BREW_VERSIONED

# `-Djreleaser.dry.run=true` below never performs a network call, so a placeholder
# satisfies JReleaser's config validation (it requires a non-blank release token)
# without requiring a real credential. Never overrides a token the caller already set.
: "${JRELEASER_GITHUB_TOKEN:=dry-run-placeholder}"
export JRELEASER_GITHUB_TOKEN

# `-Djreleaser.distributions` / `-Djreleaser.packagers` are the JReleaser Maven
# plugin's own include filters (confirmed via `mvn help:describe` on the pinned
# 1.25.0 plugin), used to select which packagers run for this channel; e.g. `lts`
# excludes `scoop` by omitting it from $PACKAGERS.
#
# `-Djreleaser.project.version` is NOT a real parameter of this Mojo (confirmed via
# `mvn help:describe -Dgoal=prepare/config/package -Ddetail=true`: none of them expose it).
# JReleaser always reads the real Maven project.version from the POM itself, regardless of
# $PROJECT_VERSION above (which only governs our own SNAPSHOT guard, artifact lookup, and the
# website manifest). What actually gates every packager above (`active: RELEASE`) is JReleaser's
# own snapshot detection, which matches the *real* POM version against
# `jreleaser.project.snapshot.pattern` (default `.*-SNAPSHOT`) - so test-mode runs from
# a SNAPSHOT checkout would otherwise skip every packager even once our own guard has been
# satisfied via a CAMEL_PACKAGE_TEST_VERSION override. In that one case, override the pattern
# to something that can never match, so JReleaser treats the real POM version as a release too.
SNAPSHOT_PATTERN_ARGS=()
if [ "${CAMEL_PACKAGE_TEST_MODE:-}" = "true" ] && [ -n "${CAMEL_PACKAGE_TEST_VERSION:-}" ]; then
  SNAPSHOT_PATTERN_ARGS=(-Djreleaser.project.snapshot.pattern=CAMEL_LAUNCHER_NEVER_MATCH_SNAPSHOT_PATTERN)
fi
echo "Preparing packages for channel '$CHANNEL' (packagers: $PACKAGERS)..."
mvn -B -ntp -f "$MODULE_DIR/pom.xml" \
  -Djreleaser.distributions=camel-cli,camel-cli-winget \
  -Djreleaser.packagers="$PACKAGERS" \
  "${SNAPSHOT_PATTERN_ARGS[@]}" \
  jreleaser:config jreleaser:prepare jreleaser:package \
  -Djreleaser.dry.run=true

# Homebrew's own versioned-formula convention names the *file* "camel@X.Y.rb" but
# the pinned JReleaser plugin derives the output filename from formulaName's literal text
# (kebab-casing our "CamelAT420" class name to "camel-at-420.rb"), not from Homebrew's
# file-naming rule. The generated formula content already has the correct class name, so only
# the filename needs fixing up here.
if [ "$CHANNEL" = "lts" ]; then
  brew_formula_dir="$MODULE_DIR/target/jreleaser/package/camel-cli/brew/Formula"
  # `brew` is always in $PACKAGERS for this channel, so a missing output directory means
  # JReleaser's layout changed and the rename below silently stopped happening. Fail rather
  # than ship a formula under JReleaser's kebab-cased filename.
  if [ ! -d "$brew_formula_dir" ]; then
    echo "Error: no generated Homebrew formula directory at $brew_formula_dir." 1>&2
    exit 1
  fi
  generated_file=""
  for f in "$brew_formula_dir"/*.rb; do
    [ -e "$f" ] || continue
    if [ -n "$generated_file" ]; then
      echo "Error: expected exactly one generated Homebrew formula file in $brew_formula_dir, found multiple." 1>&2
      exit 1
    fi
    generated_file="$f"
  done
  if [ -z "$generated_file" ]; then
    echo "Error: expected exactly one generated Homebrew formula file in $brew_formula_dir, found none." 1>&2
    exit 1
  fi
  if [ "$generated_file" != "$brew_formula_dir/$BREW_FORMULA.rb" ]; then
    mv -f "$generated_file" "$brew_formula_dir/$BREW_FORMULA.rb"
    echo "Renamed generated Homebrew formula to Homebrew's versioned-formula file convention: $BREW_FORMULA.rb"
  fi
fi
