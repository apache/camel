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
MVN_DEFAULT_OPTS="-Dmaven.compiler.fork=true -Dsurefire.rerunFailingTestsCount=2 -Dfailsafe.rerunFailingTestsCount=2 -Dci.env.name=github.com"
MVN_OPTS=${MVN_OPTS:-$MVN_DEFAULT_OPTS}

# Script variables
failures=0
successes=0
maxNumberOfTestableComponents=20
basedir=$(pwd)
testDate=$(date '+%Y-%m-%d-%H%M%S')
logDir=${basedir}/automated-build-log
testHost=$(hostname)

function notifySuccess() {
  local component=$1
  local total=$2
  local current=$3

  echo "${component} test completed successfully: ${current} verified / ${failures} failed"
  echo "| ${component} | :white_check_mark: pass" >> $GITHUB_STEP_SUMMARY
}

function notifyError() {
  local component=$1
  local total=$2
  local current=$3

  echo "Failed ${component} test: ${current} verified / ${failures} failed"
  echo "| ${component} | :x: fail" >> $GITHUB_STEP_SUMMARY
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
  mvn -l "${logDir}/${component/\//-}.log" -Psourcecheck ${MVN_OPTS} verify
  if [[ $? -ne 0 ]]; then
    ((failures++))
    notifyError "${component} test" "${total}" "${current}" "${failures}"
  else
    ((successes++))
    notifySuccess "${component}" "${total}" "${current}" "${failures}"
  fi

  local shortName=$(basename "${component}")
  local testLog="target/${shortName}-test.log"
  if [[ -f "$testLog" ]] ; then
    echo "Copying test log file at ${testLog} to the log directory"
    mv "${testLog}" "${logDir}"/
  else
    echo "There is no log file to copy at ${testLog}"
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

function coreTest() {
  echo "| Core | Result |" >> $GITHUB_STEP_SUMMARY
  echo "| --- | --- |" >> $GITHUB_STEP_SUMMARY

  cd "core"
  runTest "core" "1" "1"
  find . -iname '*test*.log' -exec mv {} "${logDir}"/ \;
}

function main() {
  local current=0
  local startCommit=${1:-""}
  local endCommit=${2:-""}

  mkdir -p "${logDir}"

  echo "Searching for camel core changes"
  local core=$(git diff "${startCommit}..${endCommit}" --name-only --pretty=format:"" | grep -e '^core' | grep -v -e '^$' | cut -d / -f 1 | uniq | sort)
  if [[ ! -z "${core}" ]] ; then
    coreTest
  fi

  echo "Searching for modified components"
  local components=$(git diff "${startCommit}..${endCommit}" --name-only --pretty=format:"" | grep -e '^components' | grep -v "camel-aws" | grep -v -e '^$' | cut -d / -f 1-2 | uniq | sort)
  local componentsAws=$(git diff "${startCommit}..${endCommit}" --name-only --pretty=format:"" | grep -e '^components' | grep "camel-aws" | grep -v -e '^$' | cut -d / -f 1-3 | uniq | sort)

  components+="\n$componentsAws"

  local total=$(echo "${components}" | grep -v -e '^$' | wc -l)

  echo "${total}" > "${logDir}/total"
  if [[ ${total} -eq 0 ]]; then
    echo "0" > "${logDir}/tested"
    echo "0" > "${logDir}/failures"
    echo "0" > "${logDir}/successes"
    exit 0
  else
    if [[ ${total} -gt ${maxNumberOfTestableComponents} ]]; then
      echo "0" > "${logDir}/tested"
      echo "0" > "${logDir}/failures"
      echo "0" > "${logDir}/successes"
      exit 0
    fi
  fi

  echo "It will test the following ${total} components:"
  echo "${components}"

  echo "" >> $GITHUB_STEP_SUMMARY
  echo "| Component | Result |" >> $GITHUB_STEP_SUMMARY
  echo "| --- | --- |" >> $GITHUB_STEP_SUMMARY
  current=0
  for component in $(echo $components); do
    ((current++))
    componentTest "${component}" "${total}" "${current}"
  done

  echo "${total}" > "${logDir}/tested"
  echo "${failures}" > "${logDir}/failures"
  echo "${successes}" > "${logDir}/successes"
  exit "${failures}"
}

main "$@"
