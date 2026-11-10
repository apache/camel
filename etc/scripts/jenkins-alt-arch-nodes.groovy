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
 *  1. query ${JENKINS_URL}/computer/api/json,
 *  2. find the online agents of a platform that are running a Camel job and still have idle executors,
 *  3. (only while the platform has not reached its maximum number of agents) build one parallel branch
 *     per idle executor that holds the executor with a 10 minutes sleep, so that Jenkins sees no free
 *     capacity for the label and provisions a new agent.
 *
 * Usage: loaded by Jenkinsfile.occupier, which is triggered by the Camel Jenkinsfiles with wait: false.
 * It must NOT be run inside a job using the 'camel' throttle, as the occupier branches allocate the
 * busy agents and would hit the same bug. Fetching the REST API uses curl, hence provisionAltArchNodes
 * must be called from within a node {}, while the returned branches must be run outside of it:
 *
 *   def branches = provisionAltArchNodes(['ppc64le', 's390x'])
 *   parallel branches
 *
 * Nothing in here fails the build: any problem results in an empty set of branches.
 */

import groovy.json.JsonSlurperClassic
import groovy.transform.Field
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

// Maximum number of agents Jenkins can provision per platform
@Field final Map<String, Integer> ALT_ARCH_MAX_NODES = ['ppc64le': 3, 's390x': 6]
// Number of minutes an idle executor is held
@Field final int ALT_ARCH_OCCUPY_MINUTES = 10
// A running build whose URL contains this marker is considered a Camel job
@Field final String ALT_ARCH_CAMEL_JOB_URL_MARKER = '/job/Camel/'
// Limit the amount of data returned by the REST API to what is needed
@Field final String ALT_ARCH_COMPUTER_TREE =
        'computer[displayName,offline,assignedLabels[name],executors[idle,currentExecutable[url]]]'

/**
 * Pure function: given the JSON of /computer/api/json, return the executors to occupy.
 * Platforms that already have their maximum number of online agents are skipped, as Jenkins cannot
 * provision a new agent for them anyway.
 *
 * @return list of [platform: String, node: String, idleExecutors: int], never null
 */
@NonCPS
List<Map> planIdleExecutorsToOccupy(String computerJson, Collection<String> platforms, Map<String, Integer> maxNodes,
                                    String camelJobUrlMarker) {
    List<Map> plan = []
    def computers = new JsonSlurperClassic().parseText(computerJson).computer
    if (!(computers instanceof List)) {
        return plan
    }

    for (String platform : platforms) {
        Integer max = maxNodes[platform]
        if (max == null) {
            continue
        }

        def online = computers.findAll { c ->
            !c.offline && c.assignedLabels?.any { it.name == platform }
        }
        if (online.size() >= max) {
            continue
        }

        for (def c : online) {
            def executors = c.executors ?: []
            // an idle executor has no currentExecutable
            int idle = executors.count { it.idle }
            boolean runsCamelJob = executors.any { it.currentExecutable?.url?.toLowerCase()?.contains(camelJobUrlMarker.toLowerCase()) }
            if (idle > 0 && runsCamelJob) {
                plan << [platform: platform, node: c.displayName as String, idleExecutors: idle]
            }
        }
    }
    return plan
}

/**
 * Query the Jenkins REST API. Needs to be called within a node {} block.
 *
 * @return the JSON text or null if the query failed
 */
String fetchComputerJson(String jenkinsUrl) {
    try {
        String base = jenkinsUrl.endsWith('/') ? jenkinsUrl : jenkinsUrl + '/'
        // --globoff: the tree parameter contains square brackets
        return sh(returnStdout: true, script: "curl --fail --silent --show-error --globoff --max-time 30 " +
                "'${base}computer/api/json?tree=${ALT_ARCH_COMPUTER_TREE}'")
    } catch (FlowInterruptedException e) {
        throw e
    } catch (Exception e) {
        echo "WARN: unable to query the Jenkins REST API, skipping alt-arch node provisioning workaround: ${e.message}"
        return null
    }
}

/**
 * Build the parallel branches holding the given idle executors. A branch never fails the build,
 * and gives up after twice the occupation time if the executor was taken in the meantime.
 */
Map<String, Closure> buildOccupierBranches(List<Map> plan, int minutes) {
    def branches = [:]
    for (def entry : plan) {
        for (int i = 1; i <= (entry.idleExecutors as int); i++) {
            String nodeName = entry.node
            String branchName = "occupy ${nodeName} #${i}"
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
 * Entry point: returns the parallel branches occupying the idle executors of busy Camel agents,
 * an empty map if there is nothing to do or if anything went wrong.
 */
Map<String, Closure> provisionAltArchNodes(Collection<String> platforms) {
    try {
        String json = fetchComputerJson(env.JENKINS_URL)
        if (!json) {
            return [:]
        }
        def plan = planIdleExecutorsToOccupy(json, platforms, ALT_ARCH_MAX_NODES, ALT_ARCH_CAMEL_JOB_URL_MARKER)
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
