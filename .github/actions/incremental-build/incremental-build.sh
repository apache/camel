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

maxNumberOfBuildableProjects=100
maxNumberOfTestableProjects=50

function findProjectRoot () {
  local path=${1}
  while [[ "$path" != "." && ! -e "$path/pom.xml" ]]; do
    path=$(dirname $path)
  done
  echo "$path"
}

function hasLabel() {
    local issueNumber=${1}
    local label="incremental-${2}"
    curl -s \
      -H "Accept: application/vnd.github+json" \
      -H "Authorization: Bearer ${GITHUB_TOKEN}"\
      -H "X-GitHub-Api-Version: 2022-11-28" \
      "https://api.github.com/repos/apache/camel/issues/${issueNumber}/labels" | jq -r '.[].name' | grep -c "$label"
}

function main() {
  local mavenBinary=${1}
  local mode=${2}
  local log="incremental-${mode}.log"
  local prId=${3}
  local ret=0

  echo "Searching for affected projects"
  local projects
  projects=$(curl -s "https://patch-diff.githubusercontent.com/raw/apache/camel/pull/${prId}.diff" | sed -n -e '/^diff --git a/p' | awk '{print $3}' | cut -b 3- | sed 's|\(.*\)/.*|\1|' | uniq | sort)
  local pl=""
  local lastProjectRoot=""
  local buildAll=false
  local totalAffected=0
  for project in ${projects}
  do
    if [[ ${project} != .* ]] ; then
      local projectRoot
      projectRoot=$(findProjectRoot ${project})
      if [[ ${projectRoot} = "." ]] ; then
        echo "There root project is affected, so a complete build is triggered"
        buildAll=true
      elif [[ ${projectRoot} != "${lastProjectRoot}" ]] ; then
        (( totalAffected ++ ))
        pl="$pl,${projectRoot}"
        lastProjectRoot=${projectRoot}
      fi
    fi
  done
  if [[ ${totalAffected} = 0 ]] ; then
    echo "There is nothing to build"
    exit 0
  elif [[ ${totalAffected} -gt ${maxNumberOfBuildableProjects} ]] ; then
    echo "There are too many affected projects, so a complete build is triggered"
    buildAll=true
  fi
  pl="${pl:1}"

  if [[ ${mode} = "build" ]] ; then
    local mustBuildAll
    mustBuildAll=$(hasLabel ${prId} "build-all")
    if [[ ${mustBuildAll} = "1" ]] ; then
      echo "The build-all label has been detected thus all projects must be built"
      buildAll=true
    fi
    if [[ ${buildAll} = "true" ]] ; then
      echo "Building all projects"
      $mavenBinary -l $log $MVND_OPTS -DskipTests install
      ret=$?
    else
      local buildDependents
      buildDependents=$(hasLabel ${prId} "build-dependents")
      local totalTestableProjects
      if [[ ${buildDependents} = "1" ]] ; then
        echo "The build-dependents label has been detected thus the projects that depend on the affected projects will be built"
        totalTestableProjects=0
      else
        totalTestableProjects=$(./mvnw -q -amd exec:exec -Dexec.executable="pwd" -pl "$pl" | wc -l)
      fi
      if [[ ${totalTestableProjects} -gt ${maxNumberOfTestableProjects} ]] ; then
        echo "Launching fast build command against the projects ${pl}, their dependencies and the projects that depend on them"
        $mavenBinary -l $log $MVND_OPTS -DskipTests install -pl "$pl" -amd -am
        ret=$?
      else
        echo "Launching fast build command against the projects ${pl} and their dependencies"
        $mavenBinary -l $log $MVND_OPTS -DskipTests install -pl "$pl" -am
        ret=$?
      fi
    fi
    [[ -z $(git status --porcelain | grep -v antora.yml) ]] || { echo 'There are uncommitted changes'; git status; echo; echo; git diff; exit 1; }
  else
    local mustSkipTests
    mustSkipTests=$(hasLabel ${prId} "skip-tests")
    if [[ ${mustSkipTests} = "1" ]] ; then
      echo "The skip-tests label has been detected thus no test will be launched"
      buildAll=true
    elif [[ ${buildAll} = "true" ]] ; then
      echo "Cannot launch the tests of all projects, so no test will be launched"
    else
      local testDependents
      testDependents=$(hasLabel ${prId} "test-dependents")
      local totalTestableProjects
      if [[ ${testDependents} = "1" ]] ; then
        echo "The test-dependents label has been detected thus the projects that depend on affected projects will be tested"
        totalTestableProjects=0
      else
        totalTestableProjects=$(./mvnw -q -amd exec:exec -Dexec.executable="pwd" -pl "$pl" | wc -l)
      fi
      if [[ ${totalTestableProjects} -gt ${maxNumberOfTestableProjects} ]] ; then
        echo "There are too many projects to test so only the affected projects are tested"
        $mavenBinary -l $log $MVND_OPTS install -pl "$pl"
        ret=$?
      else
        echo "Testing the affected projects and the projects that depend on them"
        $mavenBinary -l $log $MVND_OPTS install -pl "$pl" -amd
        ret=$?
      fi
    fi
  fi

  echo "Processing surefire and failsafe reports to create the summary"
  echo -e "| Failed Test | Duration | Failure Type |\n| --- | --- | --- |"  > "$GITHUB_STEP_SUMMARY"
  find . -path '*target/*-reports*' -iname '*.txt' -exec .github/actions/incremental-build/parse_errors.sh {} \;

  exit $ret
}

main "$@"
