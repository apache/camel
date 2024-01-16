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

echo "Using MVND_OPTS=$MVND_OPTS"

function main() {
  local mavenBinary=$MAVEN_BINARY
  local commentBody=$COMMENT_BODY
  local fastBuild=$FAST_BUILD
  local log=$LOG_FILE

  if [[ ${commentBody} = /component-test* ]] ; then
    local componentList="${commentBody:16}"
    echo "The list of components to test is ${componentList}"
  else
    echo "No components have been detected, the expected format is '/component-test (camel-)component-name1 (camel-)component-name2...'"
    exit 1
  fi
  local pl=""
  for component in ${componentList}
  do
    if [[ ${component} = camel-* ]] ; then
      componentPath="components/${component}"
    else
      componentPath="components/camel-${component}"
    fi
    if [[ -d "${componentPath}" ]] ; then
      pl="$pl$(find "${componentPath}" -name pom.xml -not -path "*/src/it/*" -not -path "*/target/*" -exec dirname {} \; | sort | tr -s "\n" ",")"
    fi
  done
  len=${#pl}
  if [[ "$len" -gt "0" ]] ; then
    pl="${pl::len-1}"
  else
    echo "The components to test don't exist"
    exit 1
  fi

  if [[ ${fastBuild} = "true" ]] ; then
    echo "Launching a fast build against the projects ${pl} and their dependencies"
    $mavenBinary -l $log $MVND_OPTS -Dquickly install -pl "$pl" -am
  else
    echo "Launching tests of the projects ${pl}"
    $mavenBinary -l $log $MVND_OPTS install -pl "$pl"
    ret=$?

    if [[ ${ret} -ne 0 ]] ; then
      echo "Processing surefire and failsafe reports to create the summary"
      echo -e "| Failed Test | Duration | Failure Type |\n| --- | --- | --- |"  > "$GITHUB_STEP_SUMMARY"
      find . -path '*target/*-reports*' -iname '*.txt' -exec .github/actions/incremental-build/parse_errors.sh {} \;
    fi

    exit $ret
  fi
}

main "$@"
