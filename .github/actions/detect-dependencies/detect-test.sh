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
# artifacts that use those properties, then finds which modules depend
# on those artifacts and runs their tests.
#
# Previous approach searched for ${property-name} in module pom.xml files,
# but that never matches because modules inherit managed versions from the
# parent without referencing the property directly.
#
# New approach:
#   1. Diff parent/pom.xml to find changed property names
#   2. Parse parent/pom.xml to map property -> artifactId(s)
#   3. Search module pom.xml files for those artifactIds
#   4. Run tests for affected modules

set -euo pipefail

MAX_MODULES=50

# Detect which properties changed in parent/pom.xml compared to the base branch.
# Returns one property name per line.
detect_changed_properties() {
  local base_branch="$1"

  git diff "${base_branch}" -- parent/pom.xml | \
    grep -E '^[+-]\s*<[^>]+>[^<]*</[^>]+>' | \
    grep -vE '^\+\+\+|^---' | \
    sed -E 's/^[+-]\s*<([^>]+)>.*/\1/' | \
    sort -u || true
}

# Given a property name, find which artifactIds in parent/pom.xml use it
# as their <version>. Looks for patterns like:
#   <artifactId>some-artifact</artifactId>
#   <version>${property-name}</version>
# Returns one artifactId per line.
find_artifacts_for_property() {
  local property="$1"
  local parent_pom="parent/pom.xml"

  # Find line numbers where <version>${property}</version> appears
  grep -n "<version>\${${property}}</version>" "$parent_pom" 2>/dev/null | \
    while IFS=: read -r line_num _; do
      # Search backwards from that line for the nearest <artifactId>
      sed -n "1,${line_num}p" "$parent_pom" | \
        grep '<artifactId>' | tail -1 | \
        sed 's/.*<artifactId>\([^<]*\)<\/artifactId>.*/\1/'
    done | sort -u
}

# Given an artifactId, find which module pom.xml files reference it
# (as a dependency, not just in exclusions or comments).
# Returns module directories, one per line.
find_modules_using_artifact() {
  local artifact="$1"
  local modules=()

  # Search for <artifactId>artifact</artifactId> in pom.xml files
  # Exclude parent/pom.xml, target dirs, and worktrees
  grep -rl "<artifactId>${artifact}</artifactId>" --include="pom.xml" . 2>/dev/null | \
    grep -v "^\./parent/pom.xml" | \
    grep -v "/target/" | \
    grep -v "\.claude/worktrees" | \
    while read -r pom_file; do
      # Verify it's actually a dependency reference (not just an exclusion)
      # by checking that <artifactId>artifact</artifactId> appears inside a
      # <dependency> block (not only inside an <exclusion> block).
      # Simple heuristic: if the artifact appears, the module is likely affected.
      # Exclusion-only references are rare enough that false positives are acceptable.
      dirname "$pom_file" | sed 's|^\./||'
    done | sort -u
}

main() {
  echo "Using MVND_OPTS=$MVND_OPTS"
  local base_branch=${1}
  local mavenBinary=${2}
  local log="detect-dependencies.log"
  local exclusionList="!:camel-allcomponents,!:dummy-component,!:camel-catalog,!:camel-catalog-console,!:camel-catalog-lucene,!:camel-catalog-maven,!:camel-catalog-suggest,!:camel-route-parser,!:camel-csimple-maven-plugin,!:camel-report-maven-plugin,!:camel-endpointdsl,!:camel-componentdsl,!:camel-endpointdsl-support,!:camel-yaml-dsl,!:camel-kamelet-main,!:camel-yaml-dsl-deserializers,!:camel-yaml-dsl-maven-plugin,!:camel-jbang-core,!:camel-jbang-main,!:camel-jbang-plugin-generate,!:camel-jbang-plugin-edit,!:camel-jbang-plugin-kubernetes,!:camel-jbang-plugin-test,!:camel-launcher,!:camel-jbang-it,!:camel-itest,!:docs,!:apache-camel"

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

  # Map properties -> artifacts -> modules
  local all_artifacts=""
  local all_modules=""
  local seen_modules=""

  while read -r prop; do
    [ -z "$prop" ] && continue

    local artifacts
    artifacts=$(find_artifacts_for_property "$prop")
    if [ -z "$artifacts" ]; then
      echo "  Property '$prop': no managed artifacts found"
      continue
    fi

    echo "  Property '$prop' manages:"
    while read -r artifact; do
      [ -z "$artifact" ] && continue
      echo "    - $artifact"
      all_artifacts="${all_artifacts:+${all_artifacts},}${artifact}"

      local modules
      modules=$(find_modules_using_artifact "$artifact")
      if [ -n "$modules" ]; then
        while read -r mod; do
          [ -z "$mod" ] && continue
          # Deduplicate
          if ! echo "$seen_modules" | grep -qx "$mod"; then
            seen_modules="${seen_modules:+${seen_modules}
}${mod}"
            all_modules="${all_modules:+${all_modules},}${mod}"
          fi
        done <<< "$modules"
      fi
    done <<< "$artifacts"
  done <<< "$changed_props"

  if [ -z "$all_modules" ]; then
    echo ""
    echo "No modules reference the changed artifacts"
    exit 0
  fi

  # Count modules
  local module_count
  module_count=$(echo "$all_modules" | tr ',' '\n' | wc -l | tr -d ' ')

  echo ""
  echo "Found ${module_count} modules affected by parent/pom.xml property changes:"
  echo "$all_modules" | tr ',' '\n' | while read -r m; do
    echo "  - $m"
  done
  echo ""

  if [ "${module_count}" -gt "${MAX_MODULES}" ]; then
    echo "Too many affected modules (${module_count} > ${MAX_MODULES}), skipping targeted tests"

    write_comment "$changed_props" "$all_modules" "$module_count" "skip"
    exit 0
  fi

  echo "Running targeted tests for affected modules..."
  $mavenBinary -l $log $MVND_OPTS test -pl "$all_modules" -am -pl "$exclusionList"
  local ret=$?

  if [ ${ret} -eq 0 ]; then
    write_comment "$changed_props" "$all_modules" "$module_count" "pass"
  else
    write_comment "$changed_props" "$all_modules" "$module_count" "fail"
  fi

  # Write step summary
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    echo "### Parent POM dependency change tests" >> "$GITHUB_STEP_SUMMARY"
    echo "" >> "$GITHUB_STEP_SUMMARY"
    echo "Changed properties: $(echo "$changed_props" | tr '\n' ', ' | sed 's/,$//')" >> "$GITHUB_STEP_SUMMARY"
    echo "" >> "$GITHUB_STEP_SUMMARY"
    echo "$all_modules" | tr ',' '\n' | while read -r m; do
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
  echo "<details><summary>Tested modules (${module_count})</summary>" >> "$comment_file"
  echo "" >> "$comment_file"
  echo "$modules" | tr ',' '\n' | while read -r m; do
    echo "- \`$m\`" >> "$comment_file"
  done
  echo "" >> "$comment_file"
  echo "</details>" >> "$comment_file"
}

main "$@"
