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

# Force clean
git clean -fdx
rm -Rf **/src/generated/

# Enable OpenRewrite only for modules with changed Java files.
# Maven's file-activated profile (<exists>.rewrite-enabled</exists>) checks
# per-module, so OpenRewrite runs only where needed — no -Prewrite flag required.
# We only need --deepen=1 (depth 1 -> 2) since we compare adjacent commits:
# for PRs, HEAD~1 is the base branch tip (first parent of the merge commit);
# for main builds, HEAD~1 is the previous squash-merged commit.
if ! git rev-parse HEAD~1 >/dev/null 2>&1; then
  git fetch --deepen=1 --quiet 2>/dev/null || true
fi

if git rev-parse HEAD~1 >/dev/null 2>&1; then
  git diff HEAD~1 HEAD --name-only -- '*.java' ':!*/src/generated/*' \
    | sed 's|/src/.*||' | sort -u \
    | while read module; do
        [ -d "$module" ] && touch "$module/.rewrite-enabled"
      done
fi

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
