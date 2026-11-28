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

# Unit tests for the functions of incremental-build.sh that keep a component change
# from testing the modules downstream of camel-allcomponents and of a regenerated
# catalog: findGeneratedOnlyModules, moduleArtifactId, subtractModules and
# computeCutModules.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Extract and load only the functions under test from incremental-build.sh without
# running main() (same approach as incremental_build_filter_test.sh).
for fn in findProjectRoot findGeneratedOnlyModules moduleArtifactId subtractModules computeCutModules; do
  _fn_body=$(awk -v name="$fn" '
    $0 ~ "^" name "\\(\\)" { in_fn=1; depth=0 }
    in_fn {
      for (i=1; i<=length($0); i++) {
        c = substr($0, i, 1)
        if (c == "{") depth++
        if (c == "}") depth--
      }
      print
      if (in_fn && depth == 0) { in_fn=0 }
    }
  ' "${SCRIPT_DIR}/incremental-build.sh")
  eval "$_fn_body"
done

pass=0
fail=0

assert_eq() {
  local expected="$1"
  local actual="$2"
  local message="$3"
  if [[ "$expected" == "$actual" ]]; then
    pass=$((pass + 1))
  else
    echo "FAIL: $message"
    echo "  expected: [$expected]"
    echo "  actual:   [$actual]"
    fail=$((fail + 1))
  fi
}

# A small module tree to resolve module roots against
work_dir=$(mktemp -d)
trap 'rm -rf "$work_dir"' EXIT
mkpom() {
  mkdir -p "$work_dir/$1"
  cat > "$work_dir/$1/pom.xml" <<EOF
<project>
  <parent>
    <groupId>org.apache.camel</groupId>
    <artifactId>$2</artifactId>
  </parent>
  <artifactId>$3</artifactId>
  <dependencies>
    <dependency>
      <artifactId>camel-support</artifactId>
    </dependency>
  </dependencies>
</project>
EOF
}
mkpom catalog/camel-catalog catalog camel-catalog
mkpom components/camel-thrift components camel-thrift
mkpom dsl/camel-endpointdsl dsl camel-endpointdsl
mkpom docs camel-parent docs
cd "$work_dir"

catalog_doc="catalog/camel-catalog/src/generated/resources/org/apache/camel/catalog/docs/thrift-component.adoc"
catalog_json="catalog/camel-catalog/src/generated/resources/org/apache/camel/catalog/components/thrift.json"
thrift_doc="components/camel-thrift/src/main/docs/thrift-component.adoc"
thrift_java="components/camel-thrift/src/main/java/org/apache/camel/component/thrift/ThriftProducer.java"
thrift_generated="components/camel-thrift/src/generated/java/org/apache/camel/component/thrift/ThriftEndpointConfigurer.java"
endpointdsl_generated="dsl/camel-endpointdsl/src/generated/java/org/apache/camel/builder/endpoint/dsl/ThriftEndpointBuilderFactory.java"

# ── findGeneratedOnlyModules ──

# 1. A component change regenerates the catalog doc: only the catalog is generated-only
result=$(findGeneratedOnlyModules "$(printf '%s\n' "$catalog_doc" "$catalog_json" "$thrift_doc" "$thrift_java" "$thrift_generated")")
assert_eq "catalog/camel-catalog" "$result" \
  "catalog with only generated files is generated-only, the component is not"

# 2. Endpoint DSL and catalog both regenerated
result=$(findGeneratedOnlyModules "$(printf '%s\n' "$catalog_json" "$endpointdsl_generated" "$thrift_java")")
assert_eq "$(printf '%s\n' catalog/camel-catalog dsl/camel-endpointdsl)" "$result" \
  "catalog and endpoint DSL are both generated-only"

# 3. A hand-written change in the catalog keeps it a normal module
result=$(findGeneratedOnlyModules "$(printf '%s\n' "$catalog_doc" "catalog/camel-catalog/src/main/java/org/apache/camel/catalog/DefaultCamelCatalog.java")")
assert_eq "" "$result" \
  "catalog with a src/main change is not generated-only"

# 4. A change to the pom of the catalog keeps it a normal module
result=$(findGeneratedOnlyModules "$(printf '%s\n' "$catalog_json" "catalog/camel-catalog/pom.xml")")
assert_eq "" "$result" \
  "catalog with a pom.xml change is not generated-only"

# 5. Files outside any module and docs pages are ignored or hand-written
result=$(findGeneratedOnlyModules "$(printf '%s\n' ".github/workflows/pr-build-main.yml" "docs/user-manual/modules/ROOT/pages/index.adoc" "CLAUDE.md")")
assert_eq "" "$result" \
  "root files and docs pages are not generated-only modules"

# 6. Empty input
result=$(findGeneratedOnlyModules "")
assert_eq "" "$result" \
  "no changed files: no generated-only modules"

# ── moduleArtifactId ──

# 7. The artifactId of the module, not the one of its parent
assert_eq "camel-thrift" "$(moduleArtifactId components/camel-thrift/pom.xml)" \
  "moduleArtifactId skips the parent and reads the module's own artifactId"

# ── subtractModules ──

# 8. Set difference, sorted
result=$(subtractModules "$(printf '%s\n' c a b)" "$(printf '%s\n' b)")
assert_eq "$(printf '%s\n' a c)" "$result" \
  "subtractModules removes the second list from the first"

# 9. Nothing to remove
result=$(subtractModules "$(printf '%s\n' b a)" "")
assert_eq "$(printf '%s\n' a b)" "$result" \
  "subtractModules with an empty second list returns the first, sorted"

# 10. Names are matched exactly, not by prefix
result=$(subtractModules "$(printf '%s\n' camel-yaml-dsl camel-yaml-dsl-validator)" "camel-yaml-dsl")
assert_eq "camel-yaml-dsl-validator" "$result" \
  "subtractModules does not remove a module whose name only starts with a removed one"

# ── computeCutModules ──

cut_reactor=$(printf '%s\n' camel-allcomponents camel-catalog camel-catalog-cve camel-yaml-dsl \
  camel-jbang-core camel-jbang-run camel-jbang-plugin-tui camel-jbang-plugin-mcp)

# 11. A component with no direct user downstream of camel-allcomponents: all is cut
result=$(computeCutModules "$cut_reactor" "" "camel-thrift")
assert_eq "$(echo "$cut_reactor" | sort)" "$result" \
  "component change: every module downstream of camel-allcomponents is cut"

# 12. A component used by camel-jbang-plugin-tui (camel-chaos): the tui plugin stays
result=$(computeCutModules "$cut_reactor" "camel-jbang-plugin-tui" "camel-chaos")
assert_eq "$(echo "$cut_reactor" | grep -vx camel-jbang-plugin-tui | sort)" "$result" \
  "direct user of the changed component is kept"

# 13. A change to camel-jbang-core keeps its own dependents
result=$(computeCutModules "$cut_reactor" "$(printf '%s\n' camel-jbang-core camel-jbang-run camel-jbang-plugin-tui camel-jbang-plugin-mcp)" "$(printf '%s\n' camel-thrift camel-jbang-core)")
assert_eq "$(printf '%s\n' camel-allcomponents camel-catalog camel-catalog-cve camel-yaml-dsl)" "$result" \
  "changed module downstream of the cut point keeps its -amd reactor"

echo ""
echo "incremental_build_cut_test.sh: ${pass} passed, ${fail} failed"
if [[ "$fail" -ne 0 ]]; then
  exit 1
fi
