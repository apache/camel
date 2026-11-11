/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * CAMEL-24290: workaround for https://github.com/jenkinsci/jenkins/pull/27365
 *
 * With throttleJobProperty(maxConcurrentPerNode: 1), Jenkins counts the idle executors of an
 * agent that is already running a Camel build as available capacity, so it never provisions a new
 * ppc64le / s390x cloud agent for a queued build and the build waits for hours.
 *
 * The helpers below:
 *  1. query ${JENKINS_URL}/computer/api/json and parse it via Python 3 in sh (to avoid Jenkins sandbox
 *     approval issues with Groovy JSON parsers),
 *  2. find the online agents of a platform that are running a Camel Core job and still have idle executors,
 *  3. (only while the platform has not reached its maximum number of agents) build one parallel branch
 *     per idle executor that holds the executor with a 10 minutes sleep, so that Jenkins sees no free
 *     capacity for the label and provisions a new agent.
 *
 * Usage: loaded by Jenkinsfile.occupier, which is triggered by the Camel Jenkinsfiles with wait: false.
 * It must NOT be run inside a job using the 'camel' throttle, as the occupier branches allocate the
 * busy agents and would hit the same bug. Fetching the REST API uses curl and python3, hence
 * provisionAltArchNodes must be called from within a node {}, while the returned branches must be run
 * outside of it:
 *
 *   def branches = provisionAltArchNodes(['ppc64le', 's390x'])
 *   parallel branches
 *
 * Nothing in here fails the build: any problem results in an empty set of branches.
 */

import groovy.transform.Field
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

// Number of minutes an idle executor is held
@Field final int ALT_ARCH_OCCUPY_MINUTES = 10
// Limit the amount of data returned by the REST API to what is needed
@Field final String ALT_ARCH_COMPUTER_TREE =
        'computer[displayName,offline,assignedLabels[name],executors[idle,currentExecutable[url]]]'

/**
 * Query the Jenkins REST API and parse node status via Python 3 inside sh to remain sandbox-safe.
 * Needs to be called within a node {} block.
 *
 * @param jenkinsUrl the Jenkins root URL
 * @param platforms list of platforms to evaluate (e.g. ['ppc64le', 's390x'])
 * @return list of [platform: String, node: String, idleExecutors: int], never null
 */
List<Map> fetchAndPlanOccupiers(String jenkinsUrl, Collection<String> platforms) {
    List<Map> plan = []
    try {
        String base = jenkinsUrl.endsWith('/') ? jenkinsUrl : jenkinsUrl + '/'
        String platformsArg = platforms.join(',')
        String script = """
curl --fail --silent --show-error --globoff --max-time 30 '${base}computer/api/json?tree=${ALT_ARCH_COMPUTER_TREE}' | python3 -c '
import json, sys

max_nodes = {"ppc64le": 3, "s390x": 6}
target_platforms = set(p.strip() for p in sys.argv[1].split(",") if p.strip())

try:
    data = json.load(sys.stdin)
except Exception:
    sys.exit(0)

computers = data.get("computer", [])
if not isinstance(computers, list):
    sys.exit(0)

for platform in target_platforms:
    max_count = max_nodes.get(platform)
    if max_count is None:
        continue

    online = [
        c for c in computers
        if not c.get("offline") and any(lbl.get("name") == platform for lbl in (c.get("assignedLabels") or []))
    ]
    if len(online) >= max_count:
        continue

    for c in online:
        executors = c.get("executors") or []
        idle_count = sum(1 for e in executors if e.get("idle"))

        runs_camel_core = False
        for e in executors:
            executable = e.get("currentExecutable") or {}
            url = (executable.get("url") or "").lower()
            if "/job/camel/" in url or "/job/camel%20" in url:
                if not any(excl in url for excl in ["occupier", "kamelet", "quarkus", "spring-boot", "karaf"]):
                    runs_camel_core = True
                    break

        if idle_count > 0 and runs_camel_core:
            node_name = c.get("displayName", "")
            if node_name:
                print(f"{platform}\\t{node_name}\\t{idle_count}")
' "${platformsArg}"
"""
        String output = sh(returnStdout: true, script: script).trim()
        if (output) {
            output.eachLine { line ->
                def parts = line.split('\t')
                if (parts.length == 3) {
                    plan << [platform: parts[0], node: parts[1], idleExecutors: parts[2] as int]
                }
            }
        }
    } catch (FlowInterruptedException e) {
        throw e
    } catch (Exception e) {
        echo "WARN: unable to query or plan alt-arch node provisioning: ${e.message}"
    }
    return plan
}

/**
 * Build the parallel branches holding the given idle executors. A branch never fails the build,
 * and gives up after twice the occupation time if the executor was taken in the meantime.
 */
Map<String, Closure> buildOccupierBranches(List<Map> plan, int minutes) {
    def branches = [:]
    for (def entry : plan) {
        final String nodeName = entry.node
        final int idleCount = entry.idleExecutors as int
        for (int i = 1; i <= idleCount; i++) {
            final String branchName = "occupy ${nodeName} #${i}"
            branches[branchName] = {
                catchError(buildResult: 'SUCCESS', stageResult: 'SUCCESS') {
                    timeout(time: minutes * 2, unit: 'MINUTES') {
                        node(nodeName) {
                            echo "Occupying an idle executor of ${nodeName} for ${minutes} minutes (CAMEL-24290)"
                            sleep(time: minutes, unit: 'MINUTES')
                        }
                    }
                }
            }
        }
    }
    return branches
}

/**
 * Entry point: returns the parallel branches occupying the idle executors of busy Camel Core agents,
 * an empty map if there is nothing to do or if anything went wrong.
 */
Map<String, Closure> provisionAltArchNodes(Collection<String> platforms) {
    try {
        def plan = fetchAndPlanOccupiers(env.JENKINS_URL, platforms)
        echo "Idle executors to occupy: ${plan}"
        return buildOccupierBranches(plan, ALT_ARCH_OCCUPY_MINUTES)
    } catch (FlowInterruptedException e) {
        throw e
    } catch (Exception e) {
        echo "WARN: alt-arch node provisioning workaround failed, ignoring: ${e.message}"
        return [:]
    }
}

return this
