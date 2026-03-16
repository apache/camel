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

# Detects property changes in parent/pom.xml, maps them to the managed
# artifacts that use those properties, then uses Maveniverse Toolbox to
# find which modules depend on those artifacts (including transitive
# dependencies) and runs their tests.
#
# Approach:
#   1. Diff parent/pom.xml to find changed property names
#   2. Parse parent/pom.xml to map property -> groupId:artifactId
#      (detecting BOM imports vs regular dependencies)
#   3. Use toolbox:tree-find to find all modules depending on those
#      artifacts (direct + transitive)
#   4. Run tests for affected modules

set -euo pipefail

MAX_MODULES=50
TOOLBOX_PLUGIN="eu.maveniverse.maven.plugins:toolbox"

# Detect which properties changed in parent/pom.xml compared to the base branch.
# Returns one property name per line.
detect_changed_properties() {
  local base_branch="$1"

  git diff "${base_branch}" -- parent/pom.xml | \
    grep -E '^[+-][[:space:]]*<[^>]+>[^<]*</[^>]+>' | \
    grep -vE '^\+\+\+|^---' | \
    sed -E 's/^[+-][[:space:]]*<([^>]+)>.*/\1/' | \
    sort -u || true
}

# Given a property name, find which groupId:artifactId pairs in
# parent/pom.xml use it as their <version>.
# Also detects if the artifact is a BOM import (<type>pom</type> +
# <scope>import</scope>), in which case it outputs "bom:groupId"
# so the caller can search by groupId wildcard.
# Returns one entry per line: either "groupId:artifactId" or "bom:groupId".
find_gav_for_property() {
  local property="$1"
  local parent_pom="parent/pom.xml"

  local matches
  matches=$(grep -n "<version>\${${property}}</version>" "$parent_pom" 2>/dev/null || true)

  if [ -z "$matches" ]; then
    return
  fi

  echo "$matches" | while IFS=: read -r line_num _; do
    local block
    block=$(sed -n "1,${line_num}p" "$parent_pom")
    local artifactId
    artifactId=$(echo "$block" | grep '<artifactId>' | tail -1 | sed 's/.*<artifactId>\([^<]*\)<\/artifactId>.*/\1/')
    local groupId
    groupId=$(echo "$block" | grep '<groupId>' | tail -1 | sed 's/.*<groupId>\([^<]*\)<\/groupId>.*/\1/')

    # Check if this is a BOM import by looking at lines after the version
    local after_version
    after_version=$(sed -n "$((line_num+1)),$((line_num+3))p" "$parent_pom")
    if echo "$after_version" | grep -q '<type>pom</type>' && echo "$after_version" | grep -q '<scope>import</scope>'; then
      echo "bom:${groupId}"
    else
      echo "${groupId}:${artifactId}"
    fi
  done | sort -u
}

# Use Maveniverse Toolbox tree-find to discover all modules that depend
# on a given artifact (including transitive dependencies).
# Returns one module artifactId per line.
find_modules_with_toolbox() {
  local mavenBinary="$1"
  local matcher_spec="$2"
  local search_pattern="$3"

  local output
  output=$($mavenBinary -B ${TOOLBOX_PLUGIN}:tree-find \
    -DartifactMatcherSpec="${matcher_spec}" 2>&1 || true)

  if echo "$output" | grep -q 'BUILD FAILURE'; then
    echo "  WARNING: toolbox tree-find failed, skipping" >&2
    return
  fi

  # Parse output: track current module from "Paths found in project" lines,
  # then when a dependency match is found, output that module's artifactId.
  # Note: mvnd strips [module] prefixes when output is captured to a
  # variable, so we track the current module from "Paths found" headers.
  echo "$output" | awk -v pattern="$search_pattern" '
    /Paths found in project/ {
      split($0, a, "project ")
      split(a[2], b, ":")
      current = b[2]
    }
    index($0, pattern) && /->/ {
      print current
    }
  ' | sort -u
}

main() {
  echo "Using MVND_OPTS=$MVND_OPTS"
  local base_branch=${1}
  local mavenBinary=${2}
  local log="detect-dependencies.log"
  local exclusionList="!:camel-allcomponents,!:dummy-component,!:camel-catalog,!:camel-catalog-console,!:camel-catalog-lucene,!:camel-catalog-maven,!:camel-catalog-suggest,!:camel-route-parser,!:camel-csimple-maven-plugin,!:camel-report-maven-plugin,!:camel-endpointdsl,!:camel-componentdsl,!:camel-endpointdsl-support,!:camel-yaml-dsl,!:camel-kamelet-main,!:camel-yaml-dsl-deserializers,!:camel-yaml-dsl-maven-plugin,!:camel-jbang-core,!:camel-jbang-main,!:camel-jbang-plugin-generate,!:camel-jbang-plugin-edit,!:camel-jbang-plugin-kubernetes,!:camel-jbang-plugin-test,!:camel-launcher,!:camel-jbang-it,!:camel-itest,!:docs,!:apache-camel,!:coverage"

  git fetch origin "$base_branch":"$base_branch" 2>/dev/null || true

  # Check if parent/pom.xml was actually changed
  if ! git diff --name-only "${base_branch}" -- parent/pom.xml | grep -q .; then
    echo "parent/pom.xml not changed, nothing to do"
    exit 0
  fi

  local changed_props
  changed_props=$(detect_changed_properties "$base_branch")

  if [ -z "$changed_props" ]; then
    echo "No property changes detected in parent/pom.xml"
    exit 0
  fi

  echo "Changed properties in parent/pom.xml:"
  echo "$changed_props"
  echo ""

  # Map properties -> GAV coordinates for toolbox lookup
  # For properties not used in parent's dependencyManagement, fall back
  # to grepping for ${property} in module pom.xml files directly
  local all_gavs=""
  local fallback_props=""
  while read -r prop; do
    [ -z "$prop" ] && continue

    local gavs
    gavs=$(find_gav_for_property "$prop")
    if [ -z "$gavs" ]; then
      echo "  Property '$prop': not in dependencyManagement, will search modules directly"
      fallback_props="${fallback_props:+${fallback_props}
}${prop}"
      continue
    fi

    echo "  Property '$prop' manages:"
    while read -r gav; do
      [ -z "$gav" ] && continue
      echo "    - $gav"
      all_gavs="${all_gavs:+${all_gavs}
}${gav}"
    done <<< "$gavs"
  done <<< "$changed_props"

  if [ -z "$all_gavs" ] && [ -z "$fallback_props" ]; then
    echo ""
    echo "No managed artifacts found for changed properties"
    exit 0
  fi

  local all_module_ids=""
  local seen_modules=""

  # Step 1: Use Toolbox tree-find for properties with managed artifacts
  if [ -n "$all_gavs" ]; then
    echo ""
    echo "Searching for affected modules using Maveniverse Toolbox..."

    local unique_gavs
    unique_gavs=$(echo "$all_gavs" | sort -u)

    while read -r gav; do
      [ -z "$gav" ] && continue

      local matcher_spec search_pattern
      if [[ "$gav" == bom:* ]]; then
        # BOM import: search by groupId wildcard
        local groupId="${gav#bom:}"
        matcher_spec="artifact(${groupId}:*)"
        search_pattern="${groupId}:"
        echo "  Searching for modules using ${groupId}:* (BOM)..."
      else
        matcher_spec="artifact(${gav})"
        search_pattern="${gav}"
        echo "  Searching for modules using ${gav}..."
      fi

      local modules
      modules=$(find_modules_with_toolbox "$mavenBinary" "$matcher_spec" "$search_pattern")
      if [ -n "$modules" ]; then
        while read -r mod; do
          [ -z "$mod" ] && continue
          if ! echo "$seen_modules" | grep -qx "$mod"; then
            seen_modules="${seen_modules:+${seen_modules}
}${mod}"
            all_module_ids="${all_module_ids:+${all_module_ids},}:${mod}"
          fi
        done <<< "$modules"
      fi
    done <<< "$unique_gavs"
  fi

  # Step 2: Fallback for properties used directly in module pom.xml files
  # (not through parent's dependencyManagement)
  if [ -n "$fallback_props" ]; then
    echo ""
    echo "Searching for modules referencing properties directly..."

    while read -r prop; do
      [ -z "$prop" ] && continue

      local matches
      matches=$(grep -rl "\${${prop}}" --include="pom.xml" . 2>/dev/null | \
        grep -v "^\./parent/pom.xml" | \
        grep -v "/target/" | \
        grep -v "\.claude/worktrees" || true)

      if [ -n "$matches" ]; then
        echo "  Property '\${${prop}}' referenced by:"
        while read -r pom_file; do
          [ -z "$pom_file" ] && continue
          # Extract artifactId from the module's pom.xml
          local mod_artifact
          mod_artifact=$(sed -n '/<parent>/,/<\/parent>/!{ s/.*<artifactId>\([^<]*\)<\/artifactId>.*/\1/p }' "$pom_file" | head -1)
          if [ -n "$mod_artifact" ] && ! echo "$seen_modules" | grep -qx "$mod_artifact"; then
            echo "    - $mod_artifact"
            seen_modules="${seen_modules:+${seen_modules}
}${mod_artifact}"
            all_module_ids="${all_module_ids:+${all_module_ids},}:${mod_artifact}"
          fi
        done <<< "$matches"
      fi
    done <<< "$fallback_props"
  fi

  if [ -z "$all_module_ids" ]; then
    echo ""
    echo "No modules depend on the changed artifacts"
    exit 0
  fi

  # Count modules
  local module_count
  module_count=$(echo "$all_module_ids" | tr ',' '\n' | wc -l | tr -d ' ')

  echo ""
  echo "Found ${module_count} modules affected by parent/pom.xml property changes:"
  echo "$all_module_ids" | tr ',' '\n' | while read -r m; do
    echo "  - $m"
  done
  echo ""

  if [ "${module_count}" -gt "${MAX_MODULES}" ]; then
    echo "Too many affected modules (${module_count} > ${MAX_MODULES}), skipping targeted tests"

    write_comment "$changed_props" "$all_module_ids" "$module_count" "skip"
    exit 0
  fi

  echo "Running targeted tests for affected modules..."
  $mavenBinary -l $log $MVND_OPTS test -pl "${all_module_ids},${exclusionList}" -amd
  local ret=$?

  if [ ${ret} -eq 0 ]; then
    write_comment "$changed_props" "$all_module_ids" "$module_count" "pass"
  else
    write_comment "$changed_props" "$all_module_ids" "$module_count" "fail"
  fi

  # Write step summary
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    echo "### Parent POM dependency change tests" >> "$GITHUB_STEP_SUMMARY"
    echo "" >> "$GITHUB_STEP_SUMMARY"
    echo "Changed properties: $(echo "$changed_props" | tr '\n' ', ' | sed 's/,$//')" >> "$GITHUB_STEP_SUMMARY"
    echo "" >> "$GITHUB_STEP_SUMMARY"
    echo "$all_module_ids" | tr ',' '\n' | while read -r m; do
      echo "- \`$m\`" >> "$GITHUB_STEP_SUMMARY"
    done

    if [ ${ret} -ne 0 ]; then
      echo "" >> "$GITHUB_STEP_SUMMARY"
      echo "Processing surefire and failsafe reports to create the summary" >> "$GITHUB_STEP_SUMMARY"
      echo -e "| Failed Test | Duration | Failure Type |\n| --- | --- | --- |" >> "$GITHUB_STEP_SUMMARY"
      find . -path '*target/*-reports*' -iname '*.txt' -exec .github/actions/incremental-build/parse_errors.sh {} \;
    fi
  fi

  exit $ret
}

write_comment() {
  local changed_props="$1"
  local modules="$2"
  local module_count="$3"
  local status="$4"
  local comment_file="detect-dependencies-comment.md"

  echo "<!-- ci-parent-pom-deps -->" > "$comment_file"

  case "$status" in
    pass)
      echo ":white_check_mark: **Parent POM dependency changes: targeted tests passed**" >> "$comment_file"
      ;;
    fail)
      echo ":x: **Parent POM dependency changes: targeted tests failed**" >> "$comment_file"
      ;;
    skip)
      echo ":information_source: **Parent POM dependency changes detected** but too many modules affected (${module_count}) to run targeted tests." >> "$comment_file"
      ;;
  esac

  echo "" >> "$comment_file"
  echo "Changed properties: $(echo "$changed_props" | tr '\n' ', ' | sed 's/,$//')" >> "$comment_file"
  echo "" >> "$comment_file"
  echo "<details><summary>Affected modules (${module_count})</summary>" >> "$comment_file"
  echo "" >> "$comment_file"
  echo "$modules" | tr ',' '\n' | while read -r m; do
    echo "- \`$m\`" >> "$comment_file"
  done
  echo "" >> "$comment_file"
  echo "</details>" >> "$comment_file"
}

main "$@"
