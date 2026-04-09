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
#   2. POM dependency analysis: for changed pom.xml files, detects property
#      changes and finds modules that reference the affected properties
#
# Both sets of affected modules are merged and deduplicated before testing.

set -euo pipefail

echo "Using MVND_OPTS=$MVND_OPTS"

maxNumberOfTestableProjects=50

# Modules excluded from targeted testing (generated code, meta-modules, etc.)
EXCLUSION_LIST="!:camel-allcomponents,!:dummy-component,!:camel-catalog,!:camel-catalog-console,!:camel-catalog-lucene,!:camel-catalog-maven,!:camel-catalog-suggest,!:camel-route-parser,!:camel-csimple-maven-plugin,!:camel-report-maven-plugin,!:camel-endpointdsl,!:camel-componentdsl,!:camel-endpointdsl-support,!:camel-yaml-dsl,!:camel-kamelet-main,!:camel-yaml-dsl-deserializers,!:camel-yaml-dsl-maven-plugin,!:camel-jbang-core,!:camel-jbang-main,!:camel-jbang-plugin-generate,!:camel-jbang-plugin-edit,!:camel-jbang-plugin-kubernetes,!:camel-jbang-plugin-test,!:camel-launcher,!:camel-jbang-it,!:camel-itest,!:docs,!:apache-camel,!:coverage"

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

  echo "$diff_body" | awk -v target="a/${pom_path}" '
    /^diff --git/ && found { exit }
    /^diff --git/ && index($0, target) { found=1 }
    found { print }
  '
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

  # Section 2: pom dependency-detected modules
  if [ -n "$dep_ids" ]; then
    echo "" >> "$comment_file"
    if [ -n "$changed_props_summary" ]; then
      echo ":white_check_mark: **POM dependency changes: targeted tests included**" >> "$comment_file"
      echo "" >> "$comment_file"
      echo "Changed properties: ${changed_props_summary}" >> "$comment_file"
      echo "" >> "$comment_file"
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
  local pom_files=""

  for project in ${projects}; do
    if [[ ${project} == */archetype-resources ]]; then
      continue
    elif [[ ${project} != .* ]]; then
      local projectRoot
      projectRoot=$(findProjectRoot "${project}")
      if [[ ${projectRoot} = "." ]]; then
        echo "The root project is affected, skipping targeted module testing"
        echo "<!-- ci-tested-modules -->" > "incremental-test-comment.md"
        echo ":information_source: CI did not run targeted module tests (root project files changed)." >> "incremental-test-comment.md"
        exit 0
      elif [[ ${projectRoot} != "${lastProjectRoot}" ]]; then
        totalAffected=$((totalAffected + 1))
        pl="$pl,${projectRoot}"
        lastProjectRoot=${projectRoot}
      fi
    fi
  done
  pl="${pl:1}"  # strip leading comma

  # Only analyze parent/pom.xml for dependency detection
  # (matches original detect-test.sh behavior; detection improvements deferred to follow-up PR)
  if echo "$diff_body" | grep -q '^diff --git a/parent/pom.xml'; then
    pom_files="parent/pom.xml"
  fi

  # ── Step 2: POM dependency analysis ──
  # Variables shared with analyzePomDependencies/findAffectedModules
  local dep_module_ids=""
  local all_changed_props=""

  if [ -n "$pom_files" ]; then
    echo ""
    echo "Analyzing parent POM dependency changes..."
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

  # ── Step 3: Merge and deduplicate ──
  # Separate file-path modules into testable (has src/test) and pom-only.
  # Pom-only modules (e.g. "parent") are kept in the build list but must NOT
  # be expanded with -amd, since that would pull in every dependent module.
  local testable_pl=""
  local pom_only_pl=""
  for w in $(echo "$pl" | tr ',' '\n'); do
    if [ -d "$w/src/test" ]; then
      testable_pl="${testable_pl:+${testable_pl},}${w}"
    else
      pom_only_pl="${pom_only_pl:+${pom_only_pl},}${w}"
      echo "  Pom-only module (no src/test, won't expand dependents): $w"
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
      local totalTestableProjects
      totalTestableProjects=$(./mvnw -B -q -amd exec:exec -Dexec.executable="pwd" -pl "$threshold_pl" 2>/dev/null | wc -l) || true
      totalTestableProjects=$(echo "$totalTestableProjects" | tail -1 | tr -d '[:space:]')
      totalTestableProjects=${totalTestableProjects:-0}

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
  if [[ "$use_amd" = true ]]; then
    $mavenBinary -l "$log" $MVND_OPTS install -pl "${build_pl},${EXCLUSION_LIST}" -amd || ret=$?
  else
    $mavenBinary -l "$log" $MVND_OPTS install -pl "$build_pl" || ret=$?
  fi

  # ── Step 5: Write comment and summary ──
  local comment_file="incremental-test-comment.md"
  writeComment "$comment_file" "$pl" "$dep_module_ids" "$all_changed_props" "$testedDependents" "$extraModules"

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
    reactor_modules=$(grep '^\[INFO\] Camel ::' "$log" | sed 's/\[INFO\] //' | sed 's/ \..*$//' | sed 's/  *\[.*\]$//' | sort -u || true)
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
    echo "Processing surefire and failsafe reports to create the summary"
    if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
      echo -e "| Failed Test | Duration | Failure Type |\n| --- | --- | --- |" >> "$GITHUB_STEP_SUMMARY"
    fi
    find . -path '*target/*-reports*' -iname '*.txt' -exec .github/actions/incremental-build/parse_errors.sh {} \;
  fi

  exit $ret
}

main "$@"
