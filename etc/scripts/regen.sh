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
git clean -fd
rm -Rf **/src/generated/

# Regenerate everything (-DskipTests skips Surefire; -DskipITs skips Failsafe ITs)
if ./mvnw -T1C --batch-mode -Pregen -DskipTests -DskipITs ${MAVEN_EXTRA_ARGS} install > build-regen.log 2>&1; then
  echo "✅ mvn -Pregen succeeded."
else
  echo "❌ mvn -Pregen failed. Last 50 lines of build-regen.log:"
  tail -n 50 build-regen.log
  exit 1
fi

# One additional pass to get the info for the 'others' jars
if ./mvnw --batch-mode ${MAVEN_EXTRA_ARGS} install -f catalog/camel-catalog > build-regen-catalog.log 2>&1; then
  echo "✅ mvn install for camel-catalog succeeded."
else
  echo "❌ mvn install for camel-catalog failed. Last 50 lines of build-regen-catalog.log:"
  tail -n 50 build-regen-catalog.log
  exit 1
fi
