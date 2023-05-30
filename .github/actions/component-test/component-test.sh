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

# Modify maven options here if needed
MVN_DEFAULT_OPTS="-Dmaven.compiler.fork=true -Dsurefire.rerunFailingTestsCount=2 -Dfailsafe.rerunFailingTestsCount=2 -Dci.env.name=github.com"
MVN_OPTS=${MVN_OPTS:-$MVN_DEFAULT_OPTS}

function main() {
  local mavenBinary=${1}
  local fastBuild=${2}
  local commentBody=${3}
  local log=${4}

  if [[ ${commentBody} = /component-test* ]] ; then
    local componentList="${commentBody:16}"
    echo "The list of components to test is ${componentList}"
  else
    echo "No components has been detected, the expected format is '/component-test (camel-)component-name1 (camel-)component-name2...'"
    exit 1
  fi
  local pl=""
  for component in ${componentList}
  do
    if [[ ${component} = camel-* ]] ; then
      pl="$pl,components/${component}"
    else
      pl="$pl,components/camel-${component}"
    fi
  done
  pl="${pl:1}"

  if [[ ${fastBuild} = "true" ]] ; then
    echo "Launching a fast build against the projects ${pl} and their dependencies"
    $mavenBinary -l $log -Dmvnd.threads=2 -V -Dhttp.keepAlive=false -Dmaven.wagon.http.pool=false -Dmaven.wagon.httpconnectionManager.ttlSeconds=120 --no-transfer-progress -e -Pfastinstall install -pl "$pl" -am
  else
    echo "Launching tests of the projects ${pl}"
    $mavenBinary -l $log -Dmvnd.threads=2 -V -Dhttp.keepAlive=false -Dmaven.wagon.http.pool=false -Dmaven.wagon.httpconnectionManager.ttlSeconds=120 --no-transfer-progress -e install -pl "$pl"
  fi
}

main "$@"
