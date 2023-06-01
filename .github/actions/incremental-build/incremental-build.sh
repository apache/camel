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
MVN_DEFAULT_OPTS="-Dmvnd.threads=2 -V -Dhttp.keepAlive=false -Dmaven.wagon.http.pool=false -Dmaven.wagon.httpconnectionManager.ttlSeconds=120 --no-transfer-progress -e -Dmaven.artifact.threads=25 -Daether.dependencyCollector.impl=bf"
MVN_OPTS=${MVN_OPTS:-$MVN_DEFAULT_OPTS}

maxNumberOfBuildableProjects=100
maxNumberOfTestableProjects=50

function findProjectRoot () {
  local path=${1}
  while [[ "$path" != "." && ! -e "$path/pom.xml" ]]; do
    path=$(dirname $path)
  done
  echo "$path"
}

function main() {
  local mavenBinary=${1}
  local checkstyle=${2}
  local log=${3}
  local prId=${4}

  echo "Searching for affected projects"
  local projects=$(curl -s "https://patch-diff.githubusercontent.com/raw/apache/camel/pull/${prId}.diff" | sed -n -e '/^diff --git a/p' | awk '{print $3}' | cut -b 3- | sed 's|\(.*\)/.*|\1|' | uniq | sort)
  local pl=""
  local lastProjectRoot=""
  local buildAll=false
  local totalAffected=0
  for project in ${projects}
  do
    if [[ ${project} != .* ]] ; then
      projectRoot=$(findProjectRoot ${project})
      if [[ ${projectRoot} = "." ]] ; then
        echo "There root project is affected, so a complete build is triggered"
        buildAll=true
      elif [[ ${projectRoot} != "${lastProjectRoot}" ]] ; then
        (( totalAffected ++ ))
        pl="$pl,${projectRoot}"
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

  if [[ ${checkstyle} = "true" ]] ; then
    if [[ ${buildAll} = "true" ]] ; then
      echo "Launching checkstyle command against all projects"
      $mavenBinary -l $log $MVN_OPTS -Dcheckstyle.failOnViolation=true checkstyle:checkstyle
    else
      echo "Launching checkstyle command against the projects ${pl}"
      $mavenBinary -l $log $MVN_OPTS -Dcheckstyle.failOnViolation=true checkstyle:checkstyle -pl "$pl"
    fi
  else
    if [[ ${buildAll} = "true" ]] ; then
      echo "Launching fast build command against all projects"
      $mavenBinary -l $log $MVN_OPTS -Pfastinstall install
      echo "Cannot launch the tests of all projects, so no test will be launched"
    else
      local totalTestableProjects=$(mvn -q -amd exec:exec -Dexec.executable="pwd" -pl "$pl" | wc -l)
      if [[ ${totalTestableProjects} -gt ${maxNumberOfTestableProjects} ]] ; then
        echo "Launching fast build command against the projects ${pl}, their dependencies and the projects that depend on them"
        $mavenBinary $MVN_OPTS -Pfastinstall install -pl "$pl" -amd -am >> $log
        echo "There are too many projects to test so only the affected projects are tested"
        $mavenBinary $MVN_OPTS install -pl "$pl" >> $log
      else
        echo "Launching fast build command against the projects ${pl} and their dependencies"
        $mavenBinary $MVN_OPTS -Pfastinstall install -pl "$pl" -am >> $log
        echo "Testing the affected projects and the projects that depend on them"
        $mavenBinary $MVN_OPTS install -pl "$pl" -amd >> $log
      fi
    fi
  fi
}

main "$@"
