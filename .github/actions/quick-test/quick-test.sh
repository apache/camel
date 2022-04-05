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

# This script runs a quick test for modified components
# It receives a starting and end commit IDs and verifies if any component was modified between them.
# It iterates through the list of components, running a unit and integration tests with -Psourcecheck
# The result and other important data is saved on log files (i.e.: test log, the PR number and the result message).
# The log file is archived by the action (see: action.yaml file)

# Modify maven options here if needed
MVN_DEFAULT_OPTS="-Dmaven.compiler.fork=true -Dsurefire.rerunFailingTestsCount=2"
MVN_OPTS=${MVN_OPTS:-$MVN_DEFAULT_OPTS}

# Script variables
failures=0
basedir=$(pwd)
testDate=$(date '+%Y-%m-%d-%H%M%S')
logDir=${basedir}/automated-build-log
testHost=$(hostname)

function notifySuccess() {
  local component=$1
  local total=$2
  local current=$3

  echo "${component} test completed successfully: ${current} verified / ${failures} failed"
}

function notifyError() {
  local component=$1
  local total=$2
  local current=$3

  echo "Failed ${component} test: ${current} verified / ${failures} failed"
}

function runTest() {
  local component=$1
  local total=$2
  local current=$3

  echo "############################################################"
  echo "Testing component ${current} of ${total}: ${component}"
  echo "############################################################"
  echo ""

  echo "Logging test to ${logDir}/${component/\//-}.log"
  mvn -Psourcecheck ${MVN_OPTS} verify 2>&1 >> "${logDir}/${component/\//-}.log"
  if [[ $? -ne 0 ]]; then
    ((failures++))
    notifyError "${component} test" "${total}" "${current}" "${failures}"
  else
    notifySuccess "${component}" "${total}" "${current}" "${failures}"
  fi
}

function componentTest() {
  local component=$1
  local total=$2
  local current=$3

  if [[ -d "${basedir}/${component}" ]] ; then
    cd "${basedir}/${component}"
    runTest "${component}" "${total}" "${current}"
  else
    echo "Skipping modified entity ${basedir}/${component} because it's not a directory"
  fi
}

function main() {
  local current=0
  local startCommit=${1:-""}
  local endCommit=${2:-""}

  mkdir -p "${logDir}"

  echo "Searching for modified components"
  local components=$(git diff "${startCommit}^..${endCommit}" --name-only --pretty=format:"" | grep -e '^components' | grep -v -e '^$' | cut -d / -f 1-2 | uniq | sort)
  local total=$(echo "${components}" | grep -v -e '^$' | wc -l)

  if [[ ${total} -eq 0 ]]; then
    echo "result=:no_entry_sign: There are (likely) no components to be tested in this PR" > "${logDir}/results.txt"
    exit 0
  else
    if [[ ${total} -gt 10 ]]; then
      echo "result=:no_entry_sign: There are too many components to be tested in this PR, components were removed or the code needs a rebase: (${total} likely to be tested)"  > "${logDir}/results.txt"
      exit 0
    fi
  fi

  echo "It will test the following ${total} components:"
  echo "${components}"

  current=0
  for component in $(echo $components); do
    ((current++))
    componentTest "${component}" "${total}" "${current}"
  done

  # This is the comment that is displayed on the PR
  if [[ ${failures} -eq 0 ]]; then
    echo "result=:heavy_check_mark: Finished component verification: ${failures} component(s) test failed out of **${total} component(s) tested**" > "${logDir}/results.txt"
  else
    echo "result=:x: Finished component verification: **${failures} component(s) test failed** out of ${total} component(s) tested" > "${logDir}/results.txt"
  fi

  exit "${failures}"
}

main "$@"
