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

echo "Using MVND_OPTS=$MVND_OPTS"

# Script variables
failures=0
successes=0
maxNumberOfTestableComponents=20
basedir=$(pwd)
testDate=$(date '+%Y-%m-%d-%H%M%S')
logDir=${basedir}/automated-build-log
testHost=$(hostname)

# How to test this code:
#
# Set the GITHUB_STEP_SUMMARY variable and select a commit range containing changes core, components or both:
# GITHUB_STEP_SUMMARY=$HOME/tmp/step-summary.txt  ./.github/actions/quick-test/quick-test.sh beginning end.
#
# For instance:
# GITHUB_STEP_SUMMARY=$HOME/tmp/step-summary.txt  ./.github/actions/quick-test/quick-test.sh 74e90da8ec55afe5065d5de495df7fe7a 9e05505d7eaad98c55d67a09cae8aa9505253c72

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

function resetCounters() {
  current=0
  failures=0
  successes=0
}

function setResultValue() {
  local value=$1
  local file=$2

  echo "$1" > "$file"
}

function setComponentTestResults() {
  local tested=$1
  local failures=$2
  local successes=$3

  setResultValue "$tested" "${logDir}/components-tested"
  setResultValue "$failures" "${logDir}/components-failures"
  setResultValue "$successes" "${logDir}/components-successes"
}

function setCoreTestResults() {
  local tested=$1
  local failures=$2
  local successes=$3

  setResultValue "$tested" "${logDir}/core-tested"
  setResultValue "$failures" "${logDir}/core-failures"
  setResultValue "$successes" "${logDir}/core-successes"
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
  ${basedir}/mvnw -l "${logDir}/${component/\//-}.log" -Psourcecheck ${MVND_OPTS} verify
  if [[ $? -ne 0 ]]; then
    ((failures++))
    notifyError "${component}" "${total}" "${current}" "${failures}"
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

function testComponents() {
    local current=0
    local startCommit=${1:-""}
    local endCommit=${2:-""}

    mkdir -p "${logDir}"

    echo "Searching for modified components"
    local components=$(git diff "${startCommit}..${endCommit}" --name-only --pretty=format:"" | grep -e '^components' | grep -v "camel-aws" | grep -v -e '^$' | cut -d / -f 1-2 | uniq | sort)
    local componentsAws=$(git diff "${startCommit}..${endCommit}" --name-only --pretty=format:"" | grep -e '^components' | grep "camel-aws" | grep -v -e '^$' | cut -d / -f 1-3 | uniq | sort)

    if [[ -z "${components}" ]] ; then
      components="${componentsAws}"
    else
      components=$(printf '%s\n%s' "${components}" "${componentsAws}")
    fi

    local total=$(echo "${components}" | grep -v -e '^$' | wc -l)

    echo "${total}" > "${logDir}/components-total"
    if [[ ${total} -eq 0 ]]; then
      setComponentTestResults 0 0 0
      exit 0
    else
      if [[ ${total} -gt ${maxNumberOfTestableComponents} ]]; then
        setComponentTestResults 0 0 0
        exit 0
      fi
    fi

    echo "It will test the following ${total} components:"
    echo "${components}"

    echo "" >> $GITHUB_STEP_SUMMARY
    echo "| Component | Result |" >> $GITHUB_STEP_SUMMARY
    echo "| --- | --- |" >> $GITHUB_STEP_SUMMARY

    resetCounters
    for component in $(echo $components); do
      ((current++))
      componentTest "${component}" "${total}" "${current}"
    done

    setComponentTestResults "$total" "$failures" "$successes"

    exit "${failures}"
}

function testCore() {
    local current=0
    local startCommit=${1:-""}
    local endCommit=${2:-""}

    mkdir -p "${logDir}"
    resetCounters

    setCoreTestResults 0 0 0
    echo "Searching for camel core changes"
    local core=$(git diff "${startCommit}..${endCommit}" --name-only --pretty=format:"" | grep -e '^core' | grep -v -e '^$' | cut -d / -f 1 | uniq | sort)
    if [[ ! -z "${core}" ]] ; then
      coreTest
      setCoreTestResults "1" "$failures" "$successes"
    fi
}

function main() {
  testCore "$@"
  testComponents "$@"
}

main "$@"
