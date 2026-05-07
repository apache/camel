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

set -e

# Move to top directory
cd `dirname "$0"`/../..

# Conditionally enable OpenRewrite FQCN shortening only when needed.
# We diff HEAD~1 vs HEAD to check for FQCNs in the changeset.
# For PR builds, actions/checkout creates a merge commit whose first parent (HEAD~1)
# is the base branch tip, so the diff covers all PR changes.
# For main builds, HEAD~1 is the previous (squash-merged) commit.
# We only need --deepen=1 (depth 1 -> 2) since we compare adjacent commits,
# not a merge-base (which would require deeper history).
if ! git rev-parse HEAD~1 >/dev/null 2>&1; then
  git fetch --deepen=1 --quiet 2>/dev/null || true
fi

if git rev-parse HEAD~1 >/dev/null 2>&1 && \
   git diff HEAD~1 HEAD -- '*.java' ':!*/src/generated/*' \
     | grep '^+[^+]' \
     | grep -v '^+ *import ' \
     | grep -v '^+ *package ' \
     | grep -v '^+ *//' \
     | grep -v '^+ *\*' \
     | grep -qE '[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)+\.[A-Z]'; then
  MAVEN_EXTRA_ARGS="${MAVEN_EXTRA_ARGS} -Prewrite"
  echo "FQCNs detected in diff, enabling OpenRewrite (-Prewrite)"
else
  echo "No FQCNs detected in diff, skipping OpenRewrite"
fi

# Force clean
git clean -fdx
rm -Rf **/src/generated/

# Regenerate everything
if ./mvnw --batch-mode -Pregen -DskipTests ${MAVEN_EXTRA_ARGS} install >> build.log 2>&1; then
  echo "✅ mvn -Pregen succeeded."
else
  echo "❌ mvn -Pregen failed. Last 50 lines of build.log:"
  tail -n 50 build.log
  exit 1
fi

# One additional pass to get the info for the 'others' jars
if ./mvnw --batch-mode ${MAVEN_EXTRA_ARGS} install -f catalog/camel-catalog >> build.log 2>&1; then
  echo "✅ mvn install for camel-catalog succeeded."
else
  echo "❌ mvn install for camel-catalog failed. Last 50 lines of build.log:"
  tail -n 50 build.log
  exit 1
fi
