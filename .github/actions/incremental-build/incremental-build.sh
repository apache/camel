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

# Incremental test runner for Apache Camel PRs.
#
# Determines which modules to test by:
#   1. File-path analysis: maps changed files to their Maven modules
#   2. POM dependency analysis (dual detection):
#      a. Grep-based: detects property changes in parent/pom.xml and finds
#         modules that explicitly reference the affected properties
#      b. Scalpel-based: uses Maveniverse Scalpel extension for effective POM
#         model comparison — catches managed deps, plugin changes, BOM imports,
#         and transitive dependency impacts that grep misses
#
# All sets of affected modules are merged and deduplicated before testing.

set -euo pipefail
# Ignore SIGPIPE to prevent spurious failures on long GitHub Actions jobs.
# When the runner closes the script's stdout (log line limit reached), external
# commands writing to it (e.g. `tail -500 "$log"` in the failure report block)
# are killed by SIGPIPE (exit 141).  Ignoring SIGPIPE lets children inherit
# SIG_IGN, exit via EPIPE instead, and preserves Maven's real return code.
trap '' PIPE

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=reactor_timing.sh
source "${SCRIPT_DIR}/reactor_timing.sh"
# shellcheck source=pr_diff.sh
source "${SCRIPT_DIR}/pr_diff.sh"

echo "Using MVND_OPTS=$MVND_OPTS"
echo "Using MAVEN_EXTRA_ARGS=${MAVEN_EXTRA_ARGS:-}"

maxNumberOfTestableProjects=50

# Modules excluded from targeted testing (generated code, meta-modules, etc.)
EXCLUSION_LIST="!:camel-allcomponents,!:dummy-component,!:camel-catalog,!:camel-catalog-console,!:camel-catalog-maven,!:camel-catalog-suggest,!:camel-route-parser,!:camel-report-maven-plugin,!:camel-endpointdsl,!:camel-componentdsl,!:camel-endpointdsl-support,!:camel-yaml-dsl,!:camel-kamelet-main,!:camel-yaml-dsl-deserializers,!:camel-yaml-dsl-maven-plugin,!:camel-jbang-core,!:camel-jbang-main,!:camel-jbang-plugin-generate,!:camel-jbang-plugin-edit,!:camel-jbang-plugin-kubernetes,!:camel-jbang-plugin-test,!:camel-launcher,!:camel-jbang-mcp,!:camel-jbang-plugin-validate,!:camel-launcher-container,!:camel-yaml-dsl-validator,!:camel-yaml-dsl-validator-maven-plugin,!:camel-jbang-it,!:camel-itest,!:docs,!:apache-camel"

# Sync-point modules that depend on every component (artifact IDs, comma-separated).
# The modules downstream of a cut point (camel-catalog, camel-yaml-dsl, camel-jbang-*,
# camel-launcher, ...) are not tested for a component change, as a component reaches
# them only through the cut point. They are still tested when a module downstream of
# the cut point changed itself (e.g. camel-jbang-core tests camel-jbang-plugin-tui).
# Maven's !: exclusion is not enough for this: it removes the module itself from the
# -amd reactor, not the modules downstream of it.
CUT_POINT_MODULES="camel-allcomponents"


# Allow projects to override the exclusion list
# (e.g., camel-spring-boot has different modules than main Camel)
if [[ -f ".github/actions/incremental-build/exclusions.sh" ]]; then
  echo "Loading project-specific exclusions from .github/actions/incremental-build/exclusions.sh"
  source .github/actions/incremental-build/exclusions.sh
fi

# ── Utility functions ──────────────────────────────────────────────────

# Walk up from a file path to find the nearest directory containing a pom.xml
# that is a real reactor module. A pom.xml located under a module's src/ directory
# (src/it, src/main/resources, src/test/resources, ...) is a test fixture or a
# template, not a reactor module, so keep walking up to the owning module.
findProjectRoot() {
  local path=${1}
  while [[ "$path" != "." ]]; do
    if [[ ! -e "$path/pom.xml" || "$path" == */src/* ]]; then
      path=$(dirname "$path")
    else
      break
    fi
  done
  echo "$path"
}

# Find the modules whose changed files are all generated (under the module's src/generated/).
# A component change regenerates files in other modules: the catalog
# (catalog/camel-catalog/src/generated/...), the endpoint DSL and the component DSL.
# Such a module did not change on its own: the change is in the component, which is
# tested. Testing the dependents of the regenerated module (-amd of camel-catalog is
# camel-yaml-dsl, camel-jbang-*, camel-launcher, ...) tests code the PR did not touch.
# Args: $1=changed file paths (newline-separated, relative to the repository root)
# Prints the module root of each generated-only module, one per line.
findGeneratedOnlyModules() {
  local changed_files="$1"
  local all_roots=","
  local hand_written_roots=","
  local file dir root
  while read -r file; do
    [ -z "$file" ] && continue
    dir=$(dirname "$file")
    root=$(findProjectRoot "$dir")
    [[ "$root" = "." ]] && continue
    all_roots="${all_roots}${root},"
    if [[ "$file" != "${root}/src/generated/"* ]]; then
      hand_written_roots="${hand_written_roots}${root},"
    fi
  done <<< "$changed_files"
  for root in $(echo "$all_roots" | tr ',' '\n' | sort -u); do
    if [[ "$hand_written_roots" != *",${root},"* ]]; then
      echo "$root"
    fi
  done
}

# Remove exclusion entries that conflict with explicitly included modules.
# When -amd pulls in dependents, the EXCLUSION_LIST prevents testing generated/meta
# modules.  But when those modules are the *primary* changed modules, excluding them
# cancels them out of the reactor and breaks the build.
filterExclusions() {
  local build_pl="$1"
  local exclusions="$2"

  # Collect artifact IDs from build_pl (path → basename, :id → id)
  local included_ids=","
  for mod in $(echo "$build_pl" | tr ',' '\n'); do
    if [[ "$mod" == :* ]]; then
      included_ids="${included_ids}${mod#:},"
    else
      included_ids="${included_ids}$(basename "$mod"),"
    fi
  done

  # Keep only exclusions whose artifact ID is not in the included set
  local result=""
  for excl in $(echo "$exclusions" | tr ',' '\n'); do
    local excl_id="${excl#!:}"
    if [[ "$included_ids" == *",${excl_id},"* ]]; then
      echo "  Removing exclusion ${excl} (conflicts with explicitly included module)" >&2
    else
      result="${result:+${result},}${excl}"
    fi
  done

  echo "$result"
}

# Print the artifact IDs of $1 that are not in $2 (both newline-separated), sorted.
subtractModules() {
  local from="$1"
  local remove="$2"
  if [ -z "$remove" ]; then
    echo "$from" | grep . | sort -u || true
    return
  fi
  comm -23 <(echo "$from" | grep . | sort -u) <(echo "$remove" | grep . | sort -u) || true
}

# Modules to cut from the -amd reactor: the modules downstream of a cut point, minus
# the modules downstream of a changed module that is itself downstream of the cut point,
# minus the changed modules.
# Args: $1=-amd reactor of the cut points, $2=-amd reactor of the changed modules that
#   are downstream of the cut points, $3=artifact IDs of the changed modules
#   (all newline-separated)
computeCutModules() {
  local cut_reactor="$1"
  local keep_reactor="$2"
  local changed_ids="$3"
  subtractModules "$cut_reactor" "$(printf '%s\n%s\n' "$keep_reactor" "$changed_ids")"
}

# Print the artifact ID of the module of a pom.xml (the first one outside <parent>).
moduleArtifactId() {
  sed -n '/<parent>/,/<\/parent>/!{ s/.*<artifactId>\([^<]*\)<\/artifactId>.*/\1/p; }' "$1" | head -1
}

# Print the artifact IDs of the -amd reactor of a -pl list (newline-separated).
amdReactorIds() {
  ./mvnw -B -q -amd exec:exec -Dexec.executable="echo" \
    -Dexec.args='${project.artifactId}' -pl "$1" 2>/dev/null | grep -v '^\[' || true
}

# Find the modules to cut from the -amd reactor of the changed modules (see CUT_POINT_MODULES).
# Kept are the changed modules, and the -amd reactor of the changed modules located
# downstream of a cut point and of the modules downstream of a cut point that declare a
# dependency on a changed module (e.g. camel-jbang-plugin-tui uses camel-chaos).
# Args: $1=changed modules (comma-separated paths or :artifactIds), $2=-amd reactor of them
# Prints the artifact IDs to cut, one per line.
findCutModules() {
  local changed_pl="$1"
  local reactor="$2"

  local changed_ids="" path
  for path in $(echo "$changed_pl" | tr ',' '\n'); do
    if [[ "$path" == :* ]]; then
      changed_ids="${changed_ids}${path#:}"$'\n'
    elif [ -f "$path/pom.xml" ]; then
      changed_ids="${changed_ids}$(moduleArtifactId "$path/pom.xml")"$'\n'
    fi
  done

  local cut_points="" cp
  for cp in $(echo "$CUT_POINT_MODULES" | tr ',' '\n'); do
    if echo "$reactor" | grep -qxF "$cp" && ! echo "$changed_ids" | grep -qxF "$cp"; then
      cut_points="${cut_points:+${cut_points},}:${cp}"
    fi
  done
  if [ -z "$cut_points" ]; then
    return 0
  fi

  local cut_reactor
  cut_reactor=$(amdReactorIds "$cut_points")
  if [ -z "$cut_reactor" ]; then
    return 0
  fi

  # Changed modules downstream of a cut point, and modules downstream of a cut point
  # that depend on a changed module directly: their -amd reactor stays.
  local keep_roots="" id pom dep_id
  for id in $changed_ids; do
    if echo "$cut_reactor" | grep -qxF "$id"; then
      keep_roots="${keep_roots}${id}"$'\n'
    fi
    for pom in $(git grep -l -F "<artifactId>${id}</artifactId>" -- '*pom.xml' ':!*/src/*' 2>/dev/null); do
      dep_id=$(moduleArtifactId "$pom")
      # A cut point depends on every component: it is not a direct user
      if [[ ",${CUT_POINT_MODULES}," == *",${dep_id},"* ]]; then
        continue
      fi
      if [ "$dep_id" != "$id" ] && echo "$cut_reactor" | grep -qxF "$dep_id"; then
        keep_roots="${keep_roots}${dep_id}"$'\n'
      fi
    done
  done
  local keep_reactor=""
  if [ -n "$(echo "$keep_roots" | grep .)" ]; then
    keep_reactor=$(amdReactorIds "$(echo "$keep_roots" | grep . | sort -u | sed 's/^/:/' | paste -sd, -)")
  fi

  computeCutModules "$cut_reactor" "$keep_reactor" "$changed_ids"
}

# Count the modules a build would test: the -amd reactor of the changed modules
# plus the dependency-detected modules (grep + Scalpel), without duplicates.
# The dependency-detected modules are added to -pl next to the -amd reactor, so the
# threshold must count them too. Otherwise a change to a module with many
# downstream modules in Scalpel's graph (camel-catalog, camel-endpointdsl, ...)
# passes the threshold with its few -amd dependents and then tests ~700 modules.
# Args: $1=reactor artifact IDs (newline-separated), $2=dep_module_ids (":id,:id,...")
countTestableModules() {
  local reactor="$1"
  local deps="$2"
  { echo "$reactor"; echo "$deps" | tr ',' '\n' | sed 's/^://'; } | grep . | sort -u | grep -c . || true
}

# Check whether a PR label exists
hasLabel() {
  local issueNumber=${1}
  local label="incremental-${2}"
  local repository=${3}
  curl -s \
    -H "Accept: application/vnd.github+json" \
    -H "Authorization: Bearer ${GITHUB_TOKEN}" \
    -H "X-GitHub-Api-Version: 2022-11-28" \
    "https://api.github.com/repos/${repository}/issues/${issueNumber}/labels" | jq -r '.[].name' | { grep -c "$label" || true; }
}

# ── POM dependency analysis (previously detect-dependencies) ───────────
#
# Uses the pre-#22022 approach: grep for ${property-name} references in
# module pom.xml files. The Maveniverse Toolbox approach (introduced in
# #22022, reverted in #22279) is intentionally not used here. See
# CI-ARCHITECTURE.md for known limitations of the grep approach.

# Extract the diff section for a specific pom.xml file from the full diff
extractPomDiff() {
  local diff_body="$1"
  local pom_path="$2"

  # Feed awk via here-string: piping from echo fails with pipefail when awk exits
  # early at the next "diff --git" hunk (common for parent/pom.xml PR changes).
  awk -v target="a/${pom_path}" '
    /^diff --git/ && found { exit }
    /^diff --git/ && index($0, target) { found=1 }
    found { print }
  ' <<< "$diff_body"
}

# Detect which properties changed in a pom.xml diff.
# Returns one property name per line.
# Filters out structural XML elements (groupId, artifactId, version, etc.)
# to only return actual property names (e.g. openai-java-version).
detectChangedProperties() {
  local diff_content="$1"

  # Known structural POM elements that are NOT property names
  local structural_elements="groupId|artifactId|version|scope|type|classifier|optional|systemPath|exclusions|exclusion|dependency|dependencies|dependencyManagement|parent|modules|module|packaging|name|description|url|relativePath"

  echo "$diff_content" | \
    grep -E '^[+-][[:space:]]*<[^>]+>[^<]*</[^>]+>' | \
    grep -vE '^\+\+\+|^---' | \
    sed -E 's/^[+-][[:space:]]*<([^>]+)>.*/\1/' | \
    grep -vE "^(${structural_elements})$" | \
    sort -u || true
}

# Find modules that reference a property in their pom.xml.
# Searches pom.xml files under catalog/, components/, core/, dsl/ for
# ${property_name} references and extracts the module's artifactId.
# Adds discovered artifactIds to the dep_module_ids variable
# (which must be declared in the caller).
findAffectedModules() {
  local property="$1"

  local matches
  matches=$(grep -rl "\${${property}}" --include="pom.xml" . 2>/dev/null | \
    grep -v "^\./parent/pom.xml" | \
    grep -v "/target/" || true)

  if [ -z "$matches" ]; then
    return
  fi

  while read -r pom_file; do
    [ -z "$pom_file" ] && continue

    # Only consider catalog, components, core, dsl paths (same as original detect-test.sh)
    if [[ "$pom_file" == */catalog/* ]] || \
       [[ "$pom_file" == */components/* ]] || \
       [[ "$pom_file" == */core/* ]] || \
       ([[ "$pom_file" == */dsl/* ]] && [[ "$pom_file" != */dsl/camel-jbang* ]]); then
      local mod_artifact
      mod_artifact=$(sed -n '/<parent>/,/<\/parent>/!{ s/.*<artifactId>\([^<]*\)<\/artifactId>.*/\1/p }' "$pom_file" | head -1)
      if [ -n "$mod_artifact" ] && ! echo ",$dep_module_ids," | grep -q ",:${mod_artifact},"; then
        echo "    Property '\${${property}}' referenced by: $mod_artifact"
        dep_module_ids="${dep_module_ids:+${dep_module_ids},}:${mod_artifact}"
      fi
    fi
  done <<< "$matches"
}

# Analyze pom.xml changes to find affected modules via property grep.
# Adds discovered module artifactIds to the dep_module_ids variable
# (which must be declared in the caller).
analyzePomDependencies() {
  local diff_body="$1"
  local pom_path="$2"  # e.g. "parent/pom.xml" or "components/camel-foo/pom.xml"

  local pom_diff
  pom_diff=$(extractPomDiff "$diff_body" "$pom_path")
  if [ -z "$pom_diff" ]; then
    return
  fi

  local changed_props
  changed_props=$(detectChangedProperties "$pom_diff")
  if [ -z "$changed_props" ]; then
    return
  fi

  echo "  Property changes detected in ${pom_path}:"
  echo "$changed_props" | while read -r p; do echo "    - $p"; done

  while read -r prop; do
    [ -z "$prop" ] && continue
    findAffectedModules "$prop"
  done <<< "$changed_props"
}

# ── POM dependency analysis via Scalpel (parallel) ─────────────────────
#
# Uses Maveniverse Scalpel (Maven extension) for effective POM model
# comparison.  Detects changed properties, managed dependencies, managed
# plugins, and transitive dependency impacts that the grep approach misses.
# Runs alongside grep — results are merged (union) for testing.
# See https://github.com/maveniverse/scalpel

# Run Scalpel in report mode to detect modules affected by POM changes.
# Sets caller-visible variables: scalpel_module_ids, scalpel_module_paths,
#   scalpel_props, scalpel_managed_deps, scalpel_managed_plugins
# Args: $1=module roots where only generated files changed (newline-separated, may be empty)
runScalpelDetection() {
  local generated_only_modules="${1:-}"
  echo "  Running Scalpel change detection..."

  # Scalpel is permanently configured in .mvn/extensions.xml.
  # On developer machines it's a no-op (disabled via -Dscalpel.enabled=false in .mvn/maven.config).
  # The CI script overrides this with -Dscalpel.enabled=true.
  # Base branch is pre-fetched by the CI workflow (fetchBaseBranch=false).
  # Run Maven validate with Scalpel in report mode:
  # - mode=report: write JSON report without trimming the reactor
  # - explain=true: add per-module evidence[] to the report (exact file/property/dep that triggered each module)
  # - fullBuildTriggers="": override .mvn/** default (Scalpel lives in .mvn/extensions.xml)
  # - fetchBaseBranch=false: base branch is pre-fetched by the CI workflow
  # - excludePaths: .github/** plus the root-level files that do not affect the build.
  #   Since Scalpel 0.5.0 a changed root-level file that no module owns triggers a full
  #   build, so a PR that only edits CLAUDE.md or README.md would lose its module list.
  #   mvnw, mvnw.cmd and pom.xml are left out on purpose: they do affect the build.
  #   A pattern without a "/" also matches at any depth (e.g. a module's README.md);
  #   the file-path step above still picks up the module of such a file.
  #   The src/generated/** of a generated-only module is excluded as well, so Scalpel
  #   does not report the dependents of a regenerated catalog or endpoint DSL.
  # - skipTestsForDownstreamModules: derived from EXCLUSION_LIST — tells Scalpel which
  #   downstream modules should not run tests in skip-tests mode (for shadow comparison)
  # Strip the Maven "!:" prefix from each entry to get bare artifact IDs for Scalpel.
  local skip_downstream
  skip_downstream=$(echo "$EXCLUSION_LIST" | sed 's/!://g')
  # Always pass baseBranch explicitly — relying on Scalpel's env.GITHUB_BASE_REF
  # auto-detection is fragile across Maven wrappers and CI rerun contexts.
  local base_branch="origin/${GITHUB_BASE_REF:-main}"
  local exclude_paths=".github/**,AGENTS.md,CLAUDE.md,CONTRIBUTING.md,README.md,SECURITY.md,KEYS,LICENSE.txt,NOTICE.txt,doap.rdf,Jenkinsfile*,.asf.yaml,.gitattributes,.gitignore,.gitmodules,.sdkmanrc,jbang-catalog.json"
  local gen_root
  for gen_root in $generated_only_modules; do
    exclude_paths="${exclude_paths},${gen_root}/src/generated/**"
  done
  local scalpel_args="-Dscalpel.enabled=true -Dscalpel.mode=report -Dscalpel.explain=true -Dscalpel.fullBuildTriggers= -Dscalpel.fetchBaseBranch=false -Dscalpel.baseBranch=${base_branch} -Dscalpel.excludePaths=${exclude_paths} -Dscalpel.skipTestsForDownstreamModules=${skip_downstream}"

  # Verify merge base is reachable (pre-fetched by the CI workflow step)
  if ! git merge-base HEAD "${base_branch}" >/dev/null 2>&1; then
    echo "  WARNING: merge base between HEAD and ${base_branch} is not reachable"
    echo "  HEAD=$(git rev-parse HEAD 2>/dev/null), ${base_branch}=$(git rev-parse ${base_branch} 2>/dev/null || echo 'NOT FOUND')"
    scalpel_failure_reason="Merge base not reachable between HEAD and ${base_branch} (shallow clone too shallow?)"
    return
  fi

  echo "  Scalpel: running mvn validate (report mode, base=${base_branch})..."
  ./mvnw -B -q validate $scalpel_args ${MAVEN_EXTRA_ARGS:-} -l /tmp/scalpel-validate.log 2>/dev/null || {
    echo "  WARNING: Scalpel detection failed (exit $?), skipping"
    grep -i "scalpel" /tmp/scalpel-validate.log 2>/dev/null | head -5 || true
    scalpel_failure_reason="Scalpel detection failed (mvn validate exited with error)"
    return
  }

  # Parse the Scalpel report
  local report="target/scalpel-report.json"
  if [ ! -f "$report" ]; then
    echo "  WARNING: Scalpel report not found at $report"
    echo "  Scalpel log (last 10 lines):"
    tail -10 /tmp/scalpel-validate.log 2>/dev/null || true
    echo "  Scalpel-specific messages:"
    grep -i "scalpel\|merge.base\|JGit\|no changes" /tmp/scalpel-validate.log 2>/dev/null | head -10 || true
    scalpel_failure_reason="Scalpel report not found (merge-base may be unreachable in shallow clone)"
    return
  fi

  # Check if full build was triggered
  local full_build
  full_build=$(jq -r '.fullBuildTriggered' "$report")
  if [ "$full_build" = "true" ]; then
    local trigger_file
    trigger_file=$(jq -r '.triggerFile // "unknown"' "$report")
    echo "  Scalpel: Full build triggered by change to $trigger_file"
    scalpel_failure_reason="Scalpel triggered a full build (changed file: $trigger_file)"
    return
  fi

  # Ignore pom.xml fixtures/templates located under a module's src/ directory
  # (e.g. src/it, src/main/resources): they are not part of the reactor.
  local filtered_report="target/scalpel-report-filtered.json"
  if jq '.affectedModules |= map(select((.path // "") | test("/src/(main|test|it)/") | not))' "$report" > "$filtered_report" 2>/dev/null; then
    report="$filtered_report"
  fi

  # Extract the artifactIds of the affected modules whose tests Scalpel would run
  # (colon-prefixed for Maven -pl compatibility). The modules with testsSkipped are the
  # EXCLUSION_LIST passed as skipTestsForDownstreamModules: putting them in -pl by name
  # would make filterExclusions drop their exclusions, and their tests would run.
  scalpel_module_ids=$(jq -r '.affectedModules[] | select(.testsSkipped != true) | .artifactId' "$report" 2>/dev/null | sort -u | sed 's/^/:/' | tr '\n' ',' | sed 's/,$//' || true)
  scalpel_module_paths=$(jq -r '.affectedModules[].path' "$report" 2>/dev/null | sort -u | tr '\n' ',' | sed 's/,$//' || true)
  scalpel_props=$(jq -r '(.changedProperties // []) | if length > 0 then join(", ") else "" end' "$report" 2>/dev/null || true)
  scalpel_managed_deps=$(jq -r '(.changedManagedDependencies // []) | if length > 0 then join(", ") else "" end' "$report" 2>/dev/null || true)
  scalpel_managed_plugins=$(jq -r '(.changedManagedPlugins // []) | if length > 0 then join(", ") else "" end' "$report" 2>/dev/null || true)

  # Scalpel shadow comparison data — read directly from report fields (0.4.x):
  # - testedModulesCount: modules whose tests will run (report field, avoids manual counting)
  # - reactorModuleCount: total reactor size for N-of-M display (report field, 0.4.1+)
  # - Per-module lists derived from affectedModules (for the comment detail sections)
  scalpel_would_test=$(jq -r '[.affectedModules[] | select(.testsSkipped != true)] | map(.artifactId) | sort | join(",")' "$report" 2>/dev/null || true)
  scalpel_would_skip=$(jq -r '[.affectedModules[] | select(.testsSkipped == true)] | map(.artifactId) | sort | join(",")' "$report" 2>/dev/null || true)
  scalpel_direct_count=$(jq '[.affectedModules[] | select(.category == "DIRECT")] | length' "$report" 2>/dev/null || echo "0")
  scalpel_downstream_tested=$(jq '[.affectedModules[] | select(.category == "DOWNSTREAM" and .testsSkipped != true)] | length' "$report" 2>/dev/null || echo "0")
  scalpel_downstream_skipped=$(jq '[.affectedModules[] | select(.category == "DOWNSTREAM" and .testsSkipped == true)] | length' "$report" 2>/dev/null || echo "0")
  # testedModulesCount and reactorModuleCount are written directly by Scalpel (0.4.x report fields)
  scalpel_tested_count=$(jq '.testedModulesCount // (.affectedModules | map(select(.testsSkipped != true)) | length)' "$report" 2>/dev/null || echo "0")
  scalpel_reactor_count=$(jq '.reactorModuleCount // 0' "$report" 2>/dev/null || echo "0")

  local mod_count
  mod_count=$(jq '.affectedModules | length' "$report" 2>/dev/null || echo "0")
  echo "  Scalpel detected $mod_count affected modules ($scalpel_tested_count would be tested, reactor: $scalpel_reactor_count)"
  echo "    Direct: $scalpel_direct_count, Downstream tested: $scalpel_downstream_tested, Downstream skipped: $scalpel_downstream_skipped"
  if [ -n "$scalpel_props" ]; then
    echo "    Changed properties: $scalpel_props"
  fi
  if [ -n "$scalpel_managed_deps" ]; then
    echo "    Changed managed deps: $scalpel_managed_deps"
  fi
  if [ -n "$scalpel_managed_plugins" ]; then
    echo "    Changed managed plugins: $scalpel_managed_plugins"
  fi
}

# ── Disabled-test detection ─────────────────────────────────────────────

# Scan tested modules for @DisabledIfSystemProperty(named = "ci.env.name")
# and return a markdown warning listing affected files.
detectDisabledTests() {
  local final_pl="$1"
  local skipped=""

  for mod_path in $(echo "$final_pl" | tr ',' '\n'); do
    # Skip artifactId-style references (e.g. :camel-openai) — only scan paths
    if [[ "$mod_path" == :* ]]; then
      continue
    fi
    if [ -d "$mod_path" ]; then
      local matches
      matches=$(grep -rl 'DisabledIfSystemProperty' "$mod_path" --include="*.java" 2>/dev/null \
        | xargs grep -l 'ci.env.name' 2>/dev/null || true)
      if [ -n "$matches" ]; then
        local count
        count=$(echo "$matches" | wc -l | tr -d ' ')
        skipped="${skipped}\n- \`${mod_path}\`: ${count} test(s) disabled on GitHub Actions"
      fi
    fi
  done

  if [ -n "$skipped" ]; then
    echo -e "$skipped"
  fi
}

# Check if changed modules have associated integration tests excluded from CI.
# Reads manual-it-mapping.txt and appends advisories to the PR comment.
checkManualItTests() {
  local final_pl="$1"
  local comment_file="$2"
  local script_dir
  script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  local mapping_file="${script_dir}/manual-it-mapping.txt"

  [[ ! -f "$mapping_file" ]] && return

  declare -A it_commands
  declare -A it_sources
  local it_found=0

  while IFS=: read -r source_id it_module command; do
    # Skip comments and empty lines
    [[ -z "$source_id" || "$source_id" == \#* ]] && continue
    source_id="${source_id// /}"
    it_module="${it_module// /}"
    command="${command#"${command%%[![:space:]]*}"}"

    # Check if any module in final_pl matches this source_id
    for module_path in $(echo "$final_pl" | tr ',' '\n'); do
      if [[ "$(basename "$module_path")" == "$source_id" ]]; then
        it_commands["$it_module"]="$command"
        it_sources["$it_module"]="${it_sources[$it_module]:-}${it_sources[$it_module]:+, }\`${module_path}\`"
        it_found=1
      fi
    done
  done < "$mapping_file"

  if [[ "$it_found" -eq 1 ]]; then
    echo "" >> "$comment_file"
    echo ":bulb: **Manual integration tests recommended:**" >> "$comment_file"
    for it_module in "${!it_sources[@]}"; do
      echo "" >> "$comment_file"
      echo "> You modified ${it_sources[$it_module]}. The related integration tests in \`${it_module}\` are excluded from CI. Consider running them manually:" >> "$comment_file"
      echo '> ```' >> "$comment_file"
      echo "> ${it_commands[$it_module]}" >> "$comment_file"
      echo '> ```' >> "$comment_file"
    done
  fi
}

# ── Recovered flake reporting ──────────────────────────────────────────
#
# Surefire retries failing tests (rerunFailingTestsCount, set project-wide in
# parent/pom.xml). A test that fails and then passes leaves the build green and
# leaves nothing in the console output, so it is invisible unless the XML
# reports are read. parse_errors.sh cannot cover this: it reads .txt reports,
# which carry no flake data, and it only runs when the build failed.
#
# Appends a section to the PR comment and writes flakes.json for aggregation
# across PRs. Never fails the build: by this point the verdict is already set.
reportRecoveredFlakes() {
  local comment_file="$1"
  local script_dir
  script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

  if ! command -v uv >/dev/null 2>&1; then
    echo "uv not found, skipping recovered-flake reporting"
    return
  fi

  local extra_args=()
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    extra_args+=(--step-summary "$GITHUB_STEP_SUMMARY")
  fi
  # Names the matrix entry in the section. The PR comment is overwritten
  # last-writer-wins across the JDK matrix, and flake data, unlike the rest of
  # the comment, differs between entries.
  if [ -n "${FLAKE_LABEL:-}" ]; then
    extra_args+=(--label "$FLAKE_LABEL")
  fi

  uv run --quiet "${script_dir}/collect-flakes.py" . \
    --comment-file "$comment_file" \
    --json-out flakes.json \
    "${extra_args[@]}" \
    || echo "WARNING: recovered-flake reporting failed, continuing"
}

# ── Scalpel shadow comparison ──────────────────────────────────────────

# Write Scalpel shadow comparison section to the PR comment.
# Shows what Scalpel would detect vs what the current approach actually tests,
# with a one-line diff summary. Observation only — does not affect test execution.
# Both sides of the comparison are filtered through EXCLUSION_LIST for a symmetric diff.
# Args: $1=comment_file, $2=tested_reactor_ids (newline-separated, already filtered by EXCLUSION_LIST)
writeScalpelComparison() {
  local comment_file="$1"
  local current_reactor_ids="${2:-}"
  local report="${3:-target/scalpel-report.json}"
  # Build exclusion set from EXCLUSION_LIST (strip "!:" prefix) for symmetric comparison
  local excl_set_cmp
  excl_set_cmp=$(echo "$EXCLUSION_LIST" | sed 's/!://g' | tr ',' '\n')

  # If Scalpel failed, show why in the PR comment
  if [ -n "$scalpel_failure_reason" ]; then
    echo "" >> "$comment_file"
    echo "---" >> "$comment_file"
    echo "" >> "$comment_file"
    echo "<details><summary>:microscope: Scalpel shadow comparison</summary>" >> "$comment_file"
    echo "" >> "$comment_file"
    echo ":warning: $scalpel_failure_reason" >> "$comment_file"
    echo "" >> "$comment_file"
    echo "> :information_source: Shadow mode — Scalpel observes but does not affect test execution. [Learn more](https://github.com/maveniverse/scalpel)" >> "$comment_file"
    echo "" >> "$comment_file"
    echo "</details>" >> "$comment_file"
    return
  fi

  # Skip if no Scalpel data (Scalpel was not invoked for this PR)
  if [ -z "$scalpel_would_test" ] && [ -z "$scalpel_would_skip" ]; then
    return
  fi

  # Counts — use report fields directly (0.4.x); fall back to counting lists for older reports
  local scalpel_total=0
  local scalpel_skip_count=0
  if [ -n "$scalpel_module_ids" ]; then
    # Count after EXCLUSION_LIST filter (scalpel_sorted is built below, compute after)
    # Defer: recomputed after scalpel_sorted is built
    true
  fi
  if [ -n "$scalpel_would_skip" ]; then
    scalpel_skip_count=$(echo "$scalpel_would_skip" | tr ',' '\n' | grep -c . || true)
  fi
  # scalpel_tested_count and scalpel_reactor_count already set from report fields in runScalpelDetection

  # Compare Scalpel vs current reactor (file-path + grep + -amd expansion)
  local current_total=0
  local current_sorted=""
  if [ -n "$current_reactor_ids" ]; then
    current_sorted=$(echo "$current_reactor_ids" | sort)
    current_total=$(echo "$current_sorted" | grep -c . || true)
  fi
  local scalpel_sorted=""
  if [ -n "$scalpel_module_ids" ]; then
    # Filter through EXCLUSION_LIST so the set-diff is symmetric with current_sorted,
    # which is already filtered (lines ~992-1001 in the main function).
    scalpel_sorted=$(echo "$scalpel_module_ids" | tr ',' '\n' | sed 's/^://' | while read -r rid; do
      if ! echo "$excl_set_cmp" | grep -qx "$rid"; then
        echo "$rid"
      fi
    done | sort)
  fi
  # Recompute scalpel_total from the filtered sorted list
  if [ -n "$scalpel_sorted" ]; then
    scalpel_total=$(echo "$scalpel_sorted" | grep -c . || true)
  fi

  # Set differences: modules Scalpel found that current missed, and vice versa
  local only_in_scalpel="" only_in_current=""
  local only_scalpel_count=0 only_current_count=0
  if [ -n "$scalpel_sorted" ] && [ -n "$current_sorted" ]; then
    only_in_scalpel=$(comm -23 <(echo "$scalpel_sorted") <(echo "$current_sorted") || true)
    only_in_current=$(comm -13 <(echo "$scalpel_sorted") <(echo "$current_sorted") || true)
    only_scalpel_count=$(echo "$only_in_scalpel" | grep -c . || true)
    only_current_count=$(echo "$only_in_current" | grep -c . || true)
  elif [ -n "$scalpel_sorted" ]; then
    only_in_scalpel="$scalpel_sorted"
    only_scalpel_count=$scalpel_total
  elif [ -n "$current_sorted" ]; then
    only_in_current="$current_sorted"
    only_current_count=$current_total
  fi

  # N-of-M framing: use reactorModuleCount from report when available
  local nm_suffix=""
  if [ "$scalpel_reactor_count" -gt 0 ] 2>/dev/null; then
    nm_suffix=" of ${scalpel_reactor_count}"
  fi

  # One-line summary with N-of-M framing
  local summary="Scalpel: ${scalpel_tested_count}${nm_suffix} tested, ${scalpel_skip_count} compile-only — current: ${current_total} all tested"

  echo "" >> "$comment_file"
  echo "---" >> "$comment_file"
  echo "" >> "$comment_file"
  echo "<details><summary>:microscope: Scalpel shadow comparison — ${summary}</summary>" >> "$comment_file"
  echo "" >> "$comment_file"

  echo "[Maveniverse Scalpel](https://github.com/maveniverse/scalpel) detected **${scalpel_total} affected modules** (current approach: ${current_total})." >> "$comment_file"
  echo "" >> "$comment_file"

  # Show Scalpel-detected change details
  if [ -n "$scalpel_props" ]; then
    echo "Changed properties: ${scalpel_props}" >> "$comment_file"
    echo "" >> "$comment_file"
  fi
  if [ -n "$scalpel_managed_deps" ]; then
    echo "Changed managed dependencies: ${scalpel_managed_deps}" >> "$comment_file"
    echo "" >> "$comment_file"
  fi
  if [ -n "$scalpel_managed_plugins" ]; then
    echo "Changed managed plugins: ${scalpel_managed_plugins}" >> "$comment_file"
    echo "" >> "$comment_file"
  fi

  echo "**Skip-tests mode would test ${scalpel_tested_count} modules** (${scalpel_direct_count} direct + ${scalpel_downstream_tested} downstream), **skip tests for ${scalpel_skip_count}** (generated code, meta-modules)" >> "$comment_file"

  # Show modules only Scalpel found (not in current reactor) — with explain evidence
  if [ "$only_scalpel_count" -gt 0 ]; then
    echo "" >> "$comment_file"
    echo "<details><summary>:warning: Modules only in Scalpel (${only_scalpel_count})</summary>" >> "$comment_file"
    echo "" >> "$comment_file"
    echo "$only_in_scalpel" | while read -r m; do
      [ -n "$m" ] && echo "- \`$m\`" >> "$comment_file"
    done
    echo "" >> "$comment_file"
    echo "</details>" >> "$comment_file"
    echo "" >> "$comment_file"
  fi

  # Show modules only current approach found (not in Scalpel)
  if [ "$only_current_count" -gt 0 ]; then
    echo "" >> "$comment_file"
    echo "<details><summary>Modules only in current approach (${only_current_count})</summary>" >> "$comment_file"
    echo "" >> "$comment_file"
    echo "$only_in_current" | while read -r m; do
      [ -n "$m" ] && echo "- \`$m\`" >> "$comment_file"
    done
    echo "" >> "$comment_file"
    echo "</details>" >> "$comment_file"
    echo "" >> "$comment_file"
  fi

  # Show which modules Scalpel would test, with per-module evidence (explain=true)
  if [ -n "$scalpel_would_test" ]; then
    echo "" >> "$comment_file"
    echo "<details><summary>Modules Scalpel would test (${scalpel_tested_count})</summary>" >> "$comment_file"
    echo "" >> "$comment_file"
    echo "$scalpel_would_test" | tr ',' '\n' | while read -r m; do
      if [ -n "$m" ]; then
        # Skip modules that are in EXCLUSION_LIST — they are intentionally not tested
        if echo "$excl_set_cmp" | grep -qx "$m"; then
          continue
        fi
        # Pull evidence[] for this module from the report (explain=true populates it)
        local evidence=""
        evidence=$(jq -r --arg art "$m" '
          .affectedModules[]
          | select(.artifactId == $art and (.testsSkipped != true))
          | (.evidence // [])
          | if length > 0 then " ← " + join(", ") else "" end
        ' "$report" 2>/dev/null | head -1 || true)
        echo "- \`$m\`${evidence}" >> "$comment_file"
      fi
    done
    echo "" >> "$comment_file"
    echo "</details>" >> "$comment_file"
  fi

  # Show which modules would have tests skipped
  if [ -n "$scalpel_would_skip" ]; then
    echo "" >> "$comment_file"
    echo "<details><summary>Modules with tests skipped (${scalpel_skip_count})</summary>" >> "$comment_file"
    echo "" >> "$comment_file"
    echo "$scalpel_would_skip" | tr ',' '\n' | while read -r m; do
      [ -n "$m" ] && echo "- \`$m\`" >> "$comment_file"
    done
    echo "" >> "$comment_file"
    echo "</details>" >> "$comment_file"
  fi

  echo "" >> "$comment_file"
  echo "> :information_source: Shadow mode — Scalpel observes but does not affect test execution. [Learn more](https://github.com/maveniverse/scalpel)" >> "$comment_file"
  echo "" >> "$comment_file"
  echo "</details>" >> "$comment_file"
}

# ── Comment generation ─────────────────────────────────────────────────

writeComment() {
  local comment_file="$1"
  local pl="$2"
  local dep_ids="$3"
  local changed_props_summary="$4"
  local testedDependents="$5"
  local extra_modules="$6"

  echo "<!-- ci-tested-modules -->" > "$comment_file"

  # Section 1: file-path-based modules
  if [ -n "$pl" ]; then
    echo ":test_tube: **CI tested the following changed modules:**" >> "$comment_file"
    echo "" >> "$comment_file"
    for w in $(echo "$pl" | tr ',' '\n'); do
      echo "- \`$w\`" >> "$comment_file"
    done

    if [[ "${testedDependents}" = "false" ]]; then
      echo "" >> "$comment_file"
      echo "> :information_source: Dependent modules were not tested because the total number of affected modules exceeded the threshold (${maxNumberOfTestableProjects}). Use the \`test-dependents\` label to force testing all dependents." >> "$comment_file"
    fi
  fi

  # Section 2: pom dependency-detected modules (grep-based only)
  if [ -n "$dep_ids" ]; then
    echo "" >> "$comment_file"
    echo ":white_check_mark: **POM dependency changes: targeted tests included**" >> "$comment_file"
    echo "" >> "$comment_file"
    if [ -n "$changed_props_summary" ]; then
      echo "Changed properties: ${changed_props_summary}" >> "$comment_file"
      echo "" >> "$comment_file"
    fi
    local dep_count
    dep_count=$(echo "$dep_ids" | tr ',' '\n' | wc -l | tr -d ' ')
    echo "<details><summary>Modules affected by dependency changes (${dep_count})</summary>" >> "$comment_file"
    echo "" >> "$comment_file"
    echo "$dep_ids" | tr ',' '\n' | while read -r m; do
      echo "- \`$m\`" >> "$comment_file"
    done
    echo "" >> "$comment_file"
    echo "</details>" >> "$comment_file"
  fi

  # Section 3: extra modules (from /component-test)
  if [ -n "$extra_modules" ]; then
    echo "" >> "$comment_file"
    echo ":heavy_plus_sign: **Additional modules tested** (via \`/component-test\`):" >> "$comment_file"
    echo "" >> "$comment_file"
    for w in $(echo "$extra_modules" | tr ',' '\n'); do
      echo "- \`$w\`" >> "$comment_file"
    done
  fi

  if [ -z "$pl" ] && [ -z "$dep_ids" ] && [ -z "$extra_modules" ]; then
    echo ":information_source: CI did not run targeted module tests." >> "$comment_file"
  fi
}

# ── Main ───────────────────────────────────────────────────────────────

main() {
  local mavenBinary=${1}
  local prId=${2}
  local repository=${3}
  local extraModules=${4:-}
  local log="incremental-test.log"
  local ret=0
  local testedDependents=""

  # Check for skip-tests label (only for PR builds)
  if [ -n "$prId" ]; then
    local mustSkipTests
    mustSkipTests=$(hasLabel "${prId}" "skip-tests" "${repository}")
    if [[ ${mustSkipTests} = "1" ]]; then
      echo "The skip-tests label has been detected, no tests will be launched"
      echo "<!-- ci-tested-modules -->" > "incremental-test-comment.md"
      echo ":information_source: CI did not run targeted module tests (skip-tests label detected)." >> "incremental-test-comment.md"
      exit 0
    fi
  fi

  # Fetch the diff (PR diff via API or the local checkout, or git diff for push builds)
  local diff_body
  if [ -n "$prId" ]; then
    echo "Fetching PR #${prId} diff..."
    diff_body=$(fetchDiff "$prId" "$repository" "${GITHUB_BASE_REF:-main}")
    if [ -z "$diff_body" ]; then
      # Without the diff no module is selected: fail rather than pass without testing anything
      echo "ERROR: Could not get the diff of PR #${prId}, so the modules to test are unknown"
      exit 1
    fi
  else
    echo "No PR ID, using git diff HEAD~1..."
    diff_body=$(git diff HEAD~1 2>/dev/null || true)
    if [ -z "$diff_body" ]; then
      echo "Could not get the diff of HEAD~1, skipping tests"
      exit 0
    fi
  fi

  # ── Step 1: File-path analysis ──
  echo "Searching for affected projects by file path..."
  local projects
  projects=$(echo "$diff_body" | sed -n -e '/^diff --git a/p' | awk '{print $3}' | cut -b 3- | sed 's|\(.*\)/.*|\1|' | uniq | sort)

  local pl=""
  local lastProjectRoot=""
  local totalAffected=0
  local pom_files=""

  for project in ${projects}; do
    if [[ ${project} == */archetype-resources || ${project} == */archetype-resources/* ]]; then
      continue
    elif [[ ${project} != .* ]]; then
      local projectRoot
      projectRoot=$(findProjectRoot "${project}")
      if [[ ${projectRoot} = "." ]]; then
        echo "  Skipping non-module file: ${project} (no parent pom.xml found)"
        continue
      elif [[ ${projectRoot} != "${lastProjectRoot}" ]]; then
        totalAffected=$((totalAffected + 1))
        pl="$pl,${projectRoot}"
        lastProjectRoot=${projectRoot}
      fi
    fi
  done
  pl="${pl:1}"  # strip leading comma

  # Modules where only generated files changed (catalog, endpoint DSL, component DSL
  # regenerated by a component change). They are built like pom-only modules (without
  # -amd), and Scalpel ignores their generated files, so their dependents are not tested.
  local generated_only_modules
  generated_only_modules=$(findGeneratedOnlyModules "$(echo "$diff_body" | sed -n -e '/^diff --git a/p' | awk '{print $3}' | cut -b 3-)")

  # Only analyze parent/pom.xml for grep-based dependency detection
  # (matches original detect-test.sh behavior)
  if echo "$diff_body" | grep -q '^diff --git a/parent/pom.xml'; then
    pom_files="parent/pom.xml"
  fi

  # ── Step 2: POM dependency analysis (dual: grep + Scalpel) ──
  # Variables shared with analyzePomDependencies/findAffectedModules
  local dep_module_ids=""
  local all_changed_props=""
  # Scalpel results (not local — set by runScalpelDetection)
  scalpel_module_ids=""
  scalpel_module_paths=""
  scalpel_props=""
  scalpel_managed_deps=""
  scalpel_managed_plugins=""
  # Scalpel shadow comparison data
  scalpel_would_test=""
  scalpel_would_skip=""
  scalpel_direct_count="0"
  scalpel_downstream_tested="0"
  scalpel_downstream_skipped="0"
  scalpel_tested_count="0"
  scalpel_reactor_count="0"
  scalpel_failure_reason=""

  # Step 2a: Grep-based detection (existing approach)
  if [ -n "$pom_files" ]; then
    echo ""
    echo "Analyzing parent POM dependency changes (grep)..."
    while read -r pom_file; do
      [ -z "$pom_file" ] && continue

      # Capture changed props for this pom before calling analyze
      local pom_diff
      pom_diff=$(extractPomDiff "$diff_body" "$pom_file")
      if [ -n "$pom_diff" ]; then
        local props
        props=$(detectChangedProperties "$pom_diff")
        if [ -n "$props" ]; then
          all_changed_props="${all_changed_props:+${all_changed_props}, }$(echo "$props" | tr '\n' ',' | sed 's/,$//')"
        fi
      fi

      analyzePomDependencies "$diff_body" "$pom_file"
    done <<< "$pom_files"
  fi

  # Step 2b: Scalpel detection (all PRs)
  # Scalpel uses effective POM model comparison and source-set analysis —
  # catches managed deps, plugin changes, transitive impacts, and
  # distinguishes main vs test-only source changes.
  # Runs in report (shadow) mode: observes what it would do, does not
  # affect actual test execution. Results appear in the PR comment for
  # comparison with the grep-based approach.
  echo ""
  echo "Running Scalpel change detection..."
  runScalpelDetection "$generated_only_modules"

  # Save grep-only results for comment attribution (before merging with Scalpel).
  # The comment's top section shows only what the current grep mechanism detected;
  # the Scalpel comparison section (after a separator) shows the diff.
  local grep_dep_module_ids="$dep_module_ids"
  local grep_changed_props="$all_changed_props"

  # Step 2c: Merge grep and Scalpel results (union, deduplicated) — for the build
  if [ -n "$scalpel_module_ids" ]; then
    dep_module_ids="${dep_module_ids:+${dep_module_ids},}${scalpel_module_ids}"
    dep_module_ids=$(echo "$dep_module_ids" | tr ',' '\n' | sort -u | tr '\n' ',' | sed 's/,$//')
  fi
  if [ -n "$scalpel_props" ]; then
    if [ -z "$all_changed_props" ]; then
      all_changed_props="$scalpel_props"
    else
      # Merge and deduplicate property names
      all_changed_props=$(printf '%s, %s' "$all_changed_props" "$scalpel_props" | tr ',' '\n' | sed 's/^ *//' | sort -u | tr '\n' ',' | sed 's/,$//' | sed 's/,/, /g')
    fi
  fi

  # ── Step 3: Merge and deduplicate ──
  # Separate file-path modules into testable (has source code) and pom-only.
  # Pom-only modules (e.g. "parent", aggregator poms) are kept in the build
  # list but must NOT be expanded with -amd, since that would pull in every
  # dependent module. Modules with src/main (including test-infra modules)
  # are treated as testable so their dependents get tested, unless only their
  # generated files changed (see findGeneratedOnlyModules).
  local testable_pl=""
  local pom_only_pl=""
  for w in $(echo "$pl" | tr ',' '\n'); do
    if echo "$generated_only_modules" | grep -qxF "$w"; then
      pom_only_pl="${pom_only_pl:+${pom_only_pl},}${w}"
      echo "  Generated-only module (only src/generated changed, won't expand dependents): $w"
    elif [ -d "$w/src/main" ]; then
      testable_pl="${testable_pl:+${testable_pl},}${w}"
    else
      pom_only_pl="${pom_only_pl:+${pom_only_pl},}${w}"
      echo "  Pom-only module (no src/main, won't expand dependents): $w"
    fi
  done

  # Build final_pl: testable file-path modules + dependency-detected + pom-only + extra
  local final_pl=""
  if [ -n "$testable_pl" ]; then
    final_pl="$testable_pl"
  fi
  if [ -n "$dep_module_ids" ]; then
    final_pl="${final_pl:+${final_pl},}${dep_module_ids}"
  fi
  if [ -n "$pom_only_pl" ]; then
    final_pl="${final_pl:+${final_pl},}${pom_only_pl}"
  fi

  # Merge extra modules (e.g. from /component-test)
  if [ -n "$extraModules" ]; then
    echo ""
    echo "Extra modules requested: $extraModules"
    final_pl="${final_pl:+${final_pl},}${extraModules}"
  fi

  if [ -z "$final_pl" ]; then
    echo ""
    echo "No modules to test"
    writeComment "incremental-test-comment.md" "" "" "" "" ""
    exit 0
  fi

  echo ""
  echo "Modules to test:"
  for w in $(echo "$final_pl" | tr ',' '\n'); do
    echo "  - $w"
  done
  echo ""

  # ── Step 4: Run tests ──
  # Decide whether to use -amd (also-make-dependents):
  # - Use -amd when there are testable file-path modules (to test their dependents)
  # - Subject to threshold check to avoid testing too many modules
  # - Pom-only modules are excluded from -pl to prevent -amd from pulling in everything
  #   (Maven builds them implicitly as dependencies of child modules)
  local use_amd=false
  local testDependents="0"
  # Reactor artifact IDs resolved by the threshold check below.
  # Reused later to validate -pl exclusions against the -amd reactor.
  local reactor_ids=""
  # Modules of the -amd reactor reached only through a cut point (see findCutModules)
  local cut_ids=""

  if [ -n "$testable_pl" ]; then
    # File-path modules with tests — use -amd to catch dependents
    if [ -n "$prId" ]; then
      testDependents=$(hasLabel "${prId}" "test-dependents" "${repository}")
    fi

    if [[ ${testDependents} = "1" ]]; then
      echo "The test-dependents label has been detected, testing dependents too"
      use_amd=true
      testedDependents=true
    else
      # Include extra modules in the count — with -amd, Maven expands all of them
      local threshold_pl="$testable_pl"
      if [ -n "$extraModules" ]; then
        threshold_pl="${threshold_pl},${extraModules}"
      fi
      # Resolve the -amd reactor: captures artifact IDs for both the threshold
      # count and later exclusion filtering (avoids a second Maven invocation).
      local totalTestableProjects
      reactor_ids=$(./mvnw -B -q -amd exec:exec -Dexec.executable="echo" \
        -Dexec.args='${project.artifactId}' -pl "$threshold_pl" 2>/dev/null || true)
      # Cut the modules that the changed modules reach only through a cut point
      # (camel-allcomponents): a component change does not test camel-yaml-dsl, camel-jbang-*, ...
      cut_ids=$(findCutModules "$threshold_pl" "$reactor_ids")
      if [ -n "$cut_ids" ]; then
        echo "Not testing $(echo "$cut_ids" | grep -c .) modules reached only through ${CUT_POINT_MODULES}:"
        echo "$cut_ids" | sed 's/^/  - /'
        if [ -n "$dep_module_ids" ]; then
          dep_module_ids=$(subtractModules "$(echo "$dep_module_ids" | tr ',' '\n' | sed 's/^://')" "$cut_ids" | sed 's/^/:/' | paste -sd, -)
        fi
        final_pl=$(echo "$final_pl" | tr ',' '\n' | grep -vxF -f <(echo "$cut_ids" | sed 's/^/:/') | paste -sd, - || true)
      fi
      totalTestableProjects=$(countTestableModules "$(subtractModules "$reactor_ids" "$cut_ids")" "$dep_module_ids")

      if [[ ${totalTestableProjects} -gt ${maxNumberOfTestableProjects} ]]; then
        echo "Too many dependent modules (${totalTestableProjects} > ${maxNumberOfTestableProjects}), testing only the affected modules"
        testedDependents=false
        # Strip dependency-detected modules (grep + Scalpel) from the build list.
        # These are "dependents" just like -amd expansion and should be subject
        # to the same threshold. Without this, Scalpel-detected modules bypass
        # the threshold and all ~N dependents get tested anyway.
        dep_module_ids=""
        final_pl=""
        if [ -n "$testable_pl" ]; then
          final_pl="$testable_pl"
        fi
        if [ -n "$pom_only_pl" ]; then
          final_pl="${final_pl:+${final_pl},}${pom_only_pl}"
        fi
        if [ -n "$extraModules" ]; then
          final_pl="${final_pl:+${final_pl},}${extraModules}"
        fi
      else
        echo "Testing affected modules and their dependents (${totalTestableProjects} modules)"
        use_amd=true
        testedDependents=true
      fi
    fi
  elif [ -n "$dep_module_ids" ]; then
    # Only dependency-detected modules (no file-path code changes)
    echo "POM dependency analysis found affected modules — testing specific modules"
    testedDependents=true
  else
    # Only pom-only modules, no testable code and no dependency results
    echo "Only pom-only modules changed with no detected dependency impact"
    testedDependents=true
  fi

  # Build the -pl argument:
  # - Exclude pom-only modules from -pl when using -amd (they'd pull in everything)
  # - Append exclusion list when dependency-detected modules are present
  local build_pl="$final_pl"
  if [[ "$use_amd" = true ]] && [ -n "$pom_only_pl" ]; then
    # Remove pom-only modules — Maven builds them implicitly as dependencies
    build_pl=""
    if [ -n "$testable_pl" ]; then
      build_pl="$testable_pl"
    fi
    if [ -n "$dep_module_ids" ]; then
      build_pl="${build_pl:+${build_pl},}${dep_module_ids}"
    fi
    if [ -n "$extraModules" ]; then
      build_pl="${build_pl:+${build_pl},}${extraModules}"
    fi
  fi
  # This needs to install, not just test, otherwise test-infra will fail due to jandex maven plugin
  # Exclusion list is only needed with -amd (to prevent testing generated/meta modules);
  # without -amd, only the explicitly listed modules are built.
  echo ""
  echo "============================================================"
  echo "Starting Maven build (logging to $log)..."
  echo "============================================================"
  if [[ "$use_amd" = true ]]; then
    local filtered_exclusions
    filtered_exclusions=$(filterExclusions "$build_pl" "$EXCLUSION_LIST")
    # Drop exclusions for modules not reachable as dependents of build_pl.
    # Maven errors if !:module references an artifact outside the -amd reactor.
    if [ -n "$filtered_exclusions" ]; then
      # Resolve reactor if not already captured by the threshold check
      if [ -z "$reactor_ids" ]; then
        reactor_ids=$(./mvnw -B -q -pl "$build_pl" -amd exec:exec \
          -Dexec.executable="echo" -Dexec.args='${project.artifactId}' 2>/dev/null || true)
      fi
      if [ -n "$reactor_ids" ]; then
        local valid_exclusions=""
        for excl in $(echo "$filtered_exclusions" | tr ',' '\n'); do
          local excl_id="${excl#!:}"
          if echo "$reactor_ids" | grep -qx "$excl_id"; then
            valid_exclusions="${valid_exclusions:+${valid_exclusions},}${excl}"
          else
            echo "  Dropping exclusion ${excl} (not in -amd reactor)"
          fi
        done
        filtered_exclusions="$valid_exclusions"
      fi
    fi
    # Exclude the cut modules too (all of them are in the -amd reactor of the threshold check)
    local cut_id
    for cut_id in $cut_ids; do
      if [[ ",${filtered_exclusions}," != *",!:${cut_id},"* ]] && echo "$reactor_ids" | grep -qxF "$cut_id"; then
        filtered_exclusions="${filtered_exclusions:+${filtered_exclusions},}!:${cut_id}"
      fi
    done
    local build_pl_with_exclusions="$build_pl"
    if [ -n "$filtered_exclusions" ]; then
      build_pl_with_exclusions="${build_pl},${filtered_exclusions}"
    fi
    echo "Command: $mavenBinary $MVND_OPTS ${MAVEN_EXTRA_ARGS:-} install -pl \"$build_pl_with_exclusions\" -amd"
    echo ""
    $mavenBinary -l "$log" $MVND_OPTS ${MAVEN_EXTRA_ARGS:-} install -pl "$build_pl_with_exclusions" -amd || ret=$?
  else
    echo "Command: $mavenBinary $MVND_OPTS ${MAVEN_EXTRA_ARGS:-} install -pl \"$build_pl\""
    echo ""
    $mavenBinary -l "$log" $MVND_OPTS ${MAVEN_EXTRA_ARGS:-} install -pl "$build_pl" || ret=$?
  fi
  echo ""
  echo "Maven build completed with exit code: $ret"
  echo "============================================================"
  echo ""

  # ── Step 5: Write comment and summary ──
  local comment_file="incremental-test-comment.md"
  writeComment "$comment_file" "$pl" "$grep_dep_module_ids" "$grep_changed_props" "$testedDependents" "$extraModules"

  # Step summary header. Written before anything else appends to the summary so
  # the reader gets "what was built" before "what happened while building it".
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    {
      echo ""
      echo "### Tested modules"
      echo ""
      for w in $(echo "$final_pl" | tr ',' '\n'); do
        echo "- \`$w\`"
      done
      echo ""
    } >> "$GITHUB_STEP_SUMMARY"
  fi

  # Recovered flakes: a green build can still have retried its way there
  reportRecoveredFlakes "$comment_file"

  # Scalpel shadow comparison (observation only — after separator)
  # Filter reactor_ids through EXCLUSION_LIST so the comparison is
  # apples-to-apples: both sides exclude the same meta/generated modules.
  local tested_reactor_ids=""
  if [ -n "$reactor_ids" ]; then
    local excl_set
    excl_set=$(printf '%s\n%s\n' "$(echo "$EXCLUSION_LIST" | sed 's/!://g' | tr ',' '\n')" "$cut_ids")
    tested_reactor_ids=$(echo "$reactor_ids" | while read -r rid; do
      if ! echo "$excl_set" | grep -qx "$rid"; then
        echo "$rid"
      fi
    done)
  fi
  writeScalpelComparison "$comment_file" "$tested_reactor_ids" "target/scalpel-report.json"

  # Check for tests disabled in CI via @DisabledIfSystemProperty(named = "ci.env.name")
  local disabled_tests
  disabled_tests=$(detectDisabledTests "$final_pl")
  if [ -n "$disabled_tests" ]; then
    echo "" >> "$comment_file"
    echo ":warning: **Some tests are disabled on GitHub Actions** (\`@DisabledIfSystemProperty(named = \"ci.env.name\")\`) and require manual verification:" >> "$comment_file"
    echo "$disabled_tests" >> "$comment_file"
  fi

  # Check for excluded IT suites that should be run manually
  checkManualItTests "$final_pl" "$comment_file"

  # Append reactor module list from build log (with per-module elapsed time)
  if [[ -f "$log" ]]; then
    local reactor_label
    if [[ "${testedDependents}" = "false" ]]; then
      reactor_label="Build reactor — dependencies compiled but only changed modules were tested"
    else
      reactor_label="All tested modules"
    fi
    append_reactor_timing_report "$log" "$comment_file" "$reactor_label" "${GITHUB_STEP_SUMMARY:-}"
  fi

  if [[ ${ret} -ne 0 ]]; then
    echo ""
    echo "============================================================"
    echo "BUILD FAILED with exit code $ret"
    echo "============================================================"

    # Show end of build log
    if [[ -f "$log" ]]; then
      echo ""
      echo "Last 500 lines of build log:"
      echo "------------------------------------------------------------"
      tail -500 "$log"
      echo "------------------------------------------------------------"
      echo ""
    else
      echo "WARNING: Build log not found at $log"
      echo ""
    fi

    echo "Processing surefire and failsafe reports to create the summary"

    # Find test reports
    local report_files
    report_files=$(find . -path '*target/*-reports*' -iname '*.txt' 2>/dev/null || true)

    if [[ -z "$report_files" ]]; then
      echo ""
      echo "WARNING: No test report files found!"
      echo "This means tests never ran - build failed before test execution"
      echo ""
    else
      local report_count
      report_count=$(echo "$report_files" | wc -l)
      echo "Found $report_count test report files"
      echo ""

      if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
        echo -e "| Failed Test | Duration | Failure Type |\n| --- | --- | --- |" >> "$GITHUB_STEP_SUMMARY"
      fi

      echo "Invoking parse_errors.sh on each report file..."
      find . -path '*target/*-reports*' -iname '*.txt' -exec .github/actions/incremental-build/parse_errors.sh {} \;
      echo "Done processing test reports"
    fi
  fi

  exit $ret
}

main "$@"
