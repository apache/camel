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
#   2. POM dependency analysis via Maveniverse Scalpel: uses effective POM
#      model comparison to detect changed properties, managed dependencies,
#      plugin changes, BOM imports, and transitive dependency impacts
#   3. Extra modules: additional modules passed via /component-test
#
# Affected modules from all sources are merged and deduplicated before testing.

set -euo pipefail

echo "Using MVND_OPTS=$MVND_OPTS"
echo "Using MAVEN_EXTRA_ARGS=${MAVEN_EXTRA_ARGS:-}"

maxNumberOfTestableProjects=50

# Modules excluded from targeted testing (generated code, meta-modules, etc.)
EXCLUSION_LIST="!:camel-allcomponents,!:dummy-component,!:camel-catalog,!:camel-catalog-console,!:camel-catalog-lucene,!:camel-catalog-maven,!:camel-catalog-suggest,!:camel-route-parser,!:camel-csimple-maven-plugin,!:camel-report-maven-plugin,!:camel-endpointdsl,!:camel-componentdsl,!:camel-endpointdsl-support,!:camel-yaml-dsl,!:camel-kamelet-main,!:camel-yaml-dsl-deserializers,!:camel-yaml-dsl-maven-plugin,!:camel-jbang-core,!:camel-jbang-main,!:camel-jbang-plugin-generate,!:camel-jbang-plugin-edit,!:camel-jbang-plugin-kubernetes,!:camel-jbang-plugin-test,!:camel-launcher,!:camel-jbang-it,!:camel-itest,!:docs,!:apache-camel,!:coverage"

# Allow projects to override the exclusion list
# (e.g., camel-spring-boot has different modules than main Camel)
if [[ -f ".github/actions/incremental-build/exclusions.sh" ]]; then
  echo "Loading project-specific exclusions from .github/actions/incremental-build/exclusions.sh"
  source .github/actions/incremental-build/exclusions.sh
fi

# ── Utility functions ──────────────────────────────────────────────────

# Walk up from a file path to find the nearest directory containing a pom.xml
findProjectRoot() {
  local path=${1}
  while [[ "$path" != "." ]]; do
    if [[ ! -e "$path/pom.xml" ]]; then
      path=$(dirname "$path")
    elif [[ $(dirname "$path") == */src/it ]]; then
      path=$(dirname "$(dirname "$path")")
    else
      break
    fi
  done
  echo "$path"
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

# Fetch the PR diff from the GitHub API.  Returns the full unified diff.
fetchDiff() {
  local prId="$1"
  local repository="$2"

  local diff_output
  diff_output=$(curl -s -w "\n%{http_code}" \
    -H "Authorization: Bearer ${GITHUB_TOKEN}" \
    -H "Accept: application/vnd.github.v3.diff" \
    "https://api.github.com/repos/${repository}/pulls/${prId}")

  local http_code
  http_code=$(echo "$diff_output" | tail -n 1)
  local diff_body
  diff_body=$(echo "$diff_output" | sed '$d')

  if [[ "$http_code" -lt 200 || "$http_code" -ge 300 || -z "$diff_body" ]]; then
    echo "WARNING: Failed to fetch PR diff (HTTP $http_code). Falling back to full build." >&2
    return
  fi
  echo "$diff_body"
}

# ── POM dependency analysis via Scalpel ────────────────────────────────
#
# Uses Maveniverse Scalpel (Maven extension) for effective POM model
# comparison.  Detects changed properties, managed dependencies, managed
# plugins, and transitive dependency impacts.
# See https://github.com/maveniverse/scalpel

# Run Scalpel in report mode to detect modules affected by POM changes.
# Sets caller-visible variables: scalpel_module_ids,
#   scalpel_props, scalpel_managed_deps, scalpel_managed_plugins
runScalpelDetection() {
  echo "  Running Scalpel change detection..."

  # Scalpel is permanently configured in .mvn/extensions.xml.
  # On developer machines it's a no-op (disabled via -Dscalpel.enabled=false in .mvn/maven.config).
  # The CI script overrides this with -Dscalpel.enabled=true.
  # Base branch is pre-fetched by the CI workflow (fetchBaseBranch=false).
  # Run Maven validate with Scalpel in report mode:
  # - mode=report: write JSON report without trimming the reactor
  # - fullBuildTriggers="": override .mvn/** default (Scalpel lives in .mvn/extensions.xml)
  # - fetchBaseBranch=false: base branch is pre-fetched by the CI workflow
  # Always pass baseBranch explicitly — relying on Scalpel's env.GITHUB_BASE_REF
  # auto-detection is fragile across Maven wrappers and CI rerun contexts.
  local base_branch="origin/${GITHUB_BASE_REF:-main}"
  local scalpel_args="-Dscalpel.enabled=true -Dscalpel.mode=report -Dscalpel.fullBuildTriggers= -Dscalpel.fetchBaseBranch=false -Dscalpel.baseBranch=${base_branch} -Dscalpel.excludePaths=.github/**"

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

  # Extract affected module artifactIds (colon-prefixed for Maven -pl compatibility)
  scalpel_module_ids=$(jq -r '.affectedModules[].artifactId' "$report" 2>/dev/null | sort -u | sed 's/^/:/' | tr '\n' ',' | sed 's/,$//' || true)
  scalpel_props=$(jq -r '(.changedProperties // []) | if length > 0 then join(", ") else "" end' "$report" 2>/dev/null || true)
  scalpel_managed_deps=$(jq -r '(.changedManagedDependencies // []) | if length > 0 then join(", ") else "" end' "$report" 2>/dev/null || true)
  scalpel_managed_plugins=$(jq -r '(.changedManagedPlugins // []) | if length > 0 then join(", ") else "" end' "$report" 2>/dev/null || true)

  local mod_count
  mod_count=$(jq '.affectedModules | length' "$report" 2>/dev/null || echo "0")
  echo "  Scalpel detected $mod_count affected modules"
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

# ── Comment generation ─────────────────────────────────────────────────

writeComment() {
  local comment_file="$1"
  local pl="$2"
  local dep_ids="$3"
  local changed_props_summary="$4"
  local testedDependents="$5"
  local extra_modules="$6"
  local managed_deps_summary="${7:-}"
  local managed_plugins_summary="${8:-}"

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

  # Section 2: Scalpel-detected POM dependency modules
  if [ -n "$dep_ids" ]; then
    echo "" >> "$comment_file"
    echo ":white_check_mark: **POM dependency changes detected by [Scalpel](https://github.com/maveniverse/scalpel): targeted tests included**" >> "$comment_file"
    echo "" >> "$comment_file"
    if [ -n "$changed_props_summary" ]; then
      echo "Changed properties: ${changed_props_summary}" >> "$comment_file"
      echo "" >> "$comment_file"
    fi
    if [ -n "$managed_deps_summary" ]; then
      echo "Changed managed dependencies: ${managed_deps_summary}" >> "$comment_file"
      echo "" >> "$comment_file"
    fi
    if [ -n "$managed_plugins_summary" ]; then
      echo "Changed managed plugins: ${managed_plugins_summary}" >> "$comment_file"
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

  # Section 3: Scalpel failure warning
  if [ -n "${scalpel_failure_reason:-}" ]; then
    echo "" >> "$comment_file"
    echo ":warning: **Scalpel POM analysis unavailable**: $scalpel_failure_reason" >> "$comment_file"
  fi

  # Section 4: extra modules (from /component-test)
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

  # Fetch the diff (PR diff via API, or git diff for push builds)
  local diff_body
  if [ -n "$prId" ]; then
    echo "Fetching PR #${prId} diff..."
    diff_body=$(fetchDiff "$prId" "$repository")
  else
    echo "No PR ID, using git diff HEAD~1..."
    diff_body=$(git diff HEAD~1 2>/dev/null || true)
  fi

  if [ -z "$diff_body" ]; then
    echo "Could not fetch diff, skipping tests"
    exit 0
  fi

  # ── Step 1: File-path analysis ──
  echo "Searching for affected projects by file path..."
  local projects
  projects=$(echo "$diff_body" | sed -n -e '/^diff --git a/p' | awk '{print $3}' | cut -b 3- | sed 's|\(.*\)/.*|\1|' | uniq | sort)

  local pl=""
  local lastProjectRoot=""
  local totalAffected=0

  for project in ${projects}; do
    if [[ ${project} == */archetype-resources ]]; then
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


  # ── Step 2: POM dependency analysis via Scalpel ──
  local dep_module_ids=""
  local all_changed_props=""
  # Scalpel results (not local — set by runScalpelDetection)
  scalpel_module_ids=""
  scalpel_props=""
  scalpel_managed_deps=""
  scalpel_managed_plugins=""
  scalpel_failure_reason=""

  echo ""
  echo "Running Scalpel change detection..."
  runScalpelDetection

  # Use Scalpel results for dependency-detected modules
  if [ -n "$scalpel_module_ids" ]; then
    dep_module_ids="$scalpel_module_ids"
  fi
  if [ -n "$scalpel_props" ]; then
    all_changed_props="$scalpel_props"
  fi

  # ── Step 3: Merge and deduplicate ──
  # Separate file-path modules into testable (has source code) and pom-only.
  # Pom-only modules (e.g. "parent", aggregator poms) are kept in the build
  # list but must NOT be expanded with -amd, since that would pull in every
  # dependent module. Modules with src/main (including test-infra modules)
  # are treated as testable so their dependents get tested.
  local testable_pl=""
  local pom_only_pl=""
  for w in $(echo "$pl" | tr ',' '\n'); do
    if [ -d "$w/src/main" ]; then
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
    writeComment "incremental-test-comment.md" "" "" "" "" "" "" ""
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
      totalTestableProjects=$(echo "$reactor_ids" | grep -c . || true)

      if [[ ${totalTestableProjects} -gt ${maxNumberOfTestableProjects} ]]; then
        echo "Too many dependent modules (${totalTestableProjects} > ${maxNumberOfTestableProjects}), testing only the affected modules"
        testedDependents=false
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
  writeComment "$comment_file" "$pl" "$dep_module_ids" "$all_changed_props" "$testedDependents" "$extraModules" "$scalpel_managed_deps" "$scalpel_managed_plugins"

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

  # Append reactor module list from build log
  if [[ -f "$log" ]]; then
    local reactor_modules
    reactor_modules=$(grep '^\[INFO\] Camel ::' "$log" | sed 's/\[INFO\] //' | sed 's/ \..*$//' | sed 's/  *\[.*\]$//' | sed 's/ SUCCESS$//' | sed 's/ FAILURE$//' | sed 's/ SKIPPED$//' | sed 's/  *$//' | sort -u || true)
    if [[ -n "$reactor_modules" ]]; then
      local count
      count=$(echo "$reactor_modules" | wc -l | tr -d ' ')
      local reactor_label
      if [[ "${testedDependents}" = "false" ]]; then
        reactor_label="Build reactor — dependencies compiled but only changed modules were tested"
      else
        reactor_label="All tested modules"
      fi

      echo "" >> "$comment_file"
      echo "<details><summary>${reactor_label} ($count modules)</summary>" >> "$comment_file"
      echo "" >> "$comment_file"

      if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
        echo "" >> "$GITHUB_STEP_SUMMARY"
        echo "<details><summary><b>${reactor_label} ($count)</b></summary>" >> "$GITHUB_STEP_SUMMARY"
        echo "" >> "$GITHUB_STEP_SUMMARY"
      fi

      echo "$reactor_modules" | while read -r m; do
        [ -n "${GITHUB_STEP_SUMMARY:-}" ] && echo "- $m" >> "$GITHUB_STEP_SUMMARY"
        echo "- $m" >> "$comment_file"
      done

      if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
        echo "" >> "$GITHUB_STEP_SUMMARY"
        echo "</details>" >> "$GITHUB_STEP_SUMMARY"
      fi
      echo "" >> "$comment_file"
      echo "</details>" >> "$comment_file"
    fi
  fi

  # Write step summary header
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
