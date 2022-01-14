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

MVN_DEFAULT_OPTS="-Dmaven.compiler.fork=true -Dsurefire.rerunFailingTestsCount=2"
MVN_OPTS=${MVN_OPTS:-$MVN_DEFAULT_OPTS}
failures=0
basedir=$(pwd)
testDate=$(date '+%Y-%m-%d-%H%M%S')
logDir=${basedir}/automated-build-log/${testDate}
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

  mvn -Psourcecheck ${MVN_OPTS} verify 2>&1 >>"${logDir}/${component/\//-}.log"
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

  cd ${basedir}/${component}
  runTest "${component}" "${total}" "${current}"
}

function main() {
  local current=0
  local startCommit=${1:-""}
  local endCommit=${2:-""}

  echo "Searching for modified components"
  local components=$(git diff "${startCommit}^..${endCommit}" --name-only --pretty=format:"" | grep -e '^components' | grep -v -e '^$' | cut -d / -f 1-2 | uniq | sort)
  local total=$(echo "${components}" | grep -v -e '^$' | wc -l)

  if [[ ${total} -eq 0 ]]; then
    echo "::set-output name=result:: :camel: There are (likely) no components to be tested in this PR"
    echo "::set-output name=component-count::0"
    echo "::set-output name=failures-count::0"
    exit 0
  fi

  echo "It will test the following ${total} components:"
  echo "${components}"
  echo "::set-output name=component-count::${total}"

  current=0
  mkdir -p "${logDir}"
  for component in $(echo $components); do
    ((current++))
    componentTest "${component}" "${total}" "${current}"
  done

  if [[ ${failures} -eq 0 ]]; then
    echo "::set-output name=result:: :heavy_check_mark: Finished verification: ${total} verified / ${failures} failed"
  else
    echo "::set-output name=result:: :x: Finished verification: ${total} verified / ${failures} failed"
  fi


  echo "::set-output name=failures-count::${failures}"
  exit "${failures}"
}

main "$@"
