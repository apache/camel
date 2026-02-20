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

detect_changed_properties() {
  local base_branch="$1"

  git diff "${base_branch}" -- parent/pom.xml | \
    grep -E '^[+-]\s*<[^>]+>[^<]*</[^>]+>' | \
    grep -vE '^\+\+\+|---' | \
    grep -E 'version|dependency|artifact' | \
    sed -E 's/^[+-]\s*<([^>]+)>.*/\1/' | \
    sort -u || true
}

find_affected_modules() {
  local property_name="$1"
  local mavenBinary=${2}
  local affected=()

  while IFS= read -r pom; do
    # skip any target that may have been built previously
    if [[ "$pom" != */target/* ]]; then
      # only consider certain modules, nothing else
      if [[ "$pom" == */catalog/* ]] || \
          [[ "$pom" == */components/* ]] || \
          [[ "$pom" == */core/* ]] || \
          ([[ "$pom" == */dsl/* ]] && [[ "$pom" != */dsl/camel-jbang* ]]); then
        if grep -q "\${${property_name}}" "$pom"; then
          affected+=("$pom")
        fi
      fi
    fi
  done < <(find . -name "pom.xml")

  affected_transformed=""

  for pom in "${affected[@]}"; do
    if [[ -f "$pom" ]]; then
      artifactId=$($mavenBinary -f "$pom" help:evaluate -Dexpression=project.artifactId -q --raw-streams -DforceStdout)
      if [ ! -z "$artifactId" ]; then
        affected_transformed+=":$artifactId,"
      fi
    fi
  done

  echo "$affected_transformed"
}

main() {
  echo "Using MVND_OPTS=$MVND_OPTS"
  local base_branch=${1}
  local mavenBinary=${2}
  local exclusionList="!:camel-allcomponents,!:dummy-component,!:camel-catalog,!:camel-catalog-console,!:camel-catalog-lucene,!:camel-catalog-maven,!:camel-catalog-suggest,!:camel-route-parser,!:camel-csimple-maven-plugin,!:camel-report-maven-plugin,!:camel-endpointdsl,!:camel-componentdsl,!:camel-endpointdsl-support,!:camel-yaml-dsl,!:camel-kamelet-main,!:camel-yaml-dsl-deserializers,!:camel-yaml-dsl-maven-plugin,!:camel-jbang-core,!:camel-jbang-main,!:camel-jbang-plugin-generate,!:camel-jbang-plugin-edit,!:camel-jbang-plugin-kubernetes,!:camel-jbang-plugin-test,!:camel-launcher,!:camel-jbang-it,!:camel-itest,!:docs,!:apache-camel,!:camel-test-infra-all"

  git fetch origin $base_branch:$base_branch

  changed_props=$(detect_changed_properties "$base_branch")

  if [ -z "$changed_props" ]; then
    echo "✅ No property changes detected."
    exit 0
  fi

  modules_affected=""

  while read -r prop; do
    modules=$(find_affected_modules "$prop" $mavenBinary)
    modules_affected+="$modules"
  done <<< "$changed_props"

  if [ -z "$modules_affected" ]; then
    echo "✅ No components affected by property changes detected."
    exit 0
  fi

  echo "🧪 Testing the following modules $modules_affected and its dependents"
  $mavenBinary $MVND_OPTS test -pl "$modules_affected$exclusionList" -amd
}

main "$@"
