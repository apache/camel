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

package org.apache.camel.component.jbpm;

public enum JBPMOperation {

    // PROCESS OPERATIONS
    startProcess,
    abortProcessInstance,
    signalEvent,
    getProcessInstance,
    getProcessInstances,

    // RULE OPERATIONS
    fireAllRules,
    getGlobal,
    setGlobal,

    // WORK ITEM OPERATIONS
    abortWorkItem,
    completeWorkItem,

    // TASK OPERATIONS
    activateTask,
    claimTask,
    completeTask,
    delegateTask,
    exitTask,
    failTask,
    getAttachment,
    getTasksAssignedAsBusinessAdministrator,
    getTasksAssignedAsPotentialOwnerByStatus,
    getTaskByWorkItem,
    getTaskBy,
    getTaskContent,
    getTasksByProcessInstance,
    getTasksByStatusByProcessInstance,
    getTasksOwned,
    nominateTask,
    releaseTask,
    resumeTask,
    skipTask,
    startTask,
    stopTask,
    suspendTask;

    @Override
    public String toString() {
        return JBPMConstants.OPERATION + super.toString();
    }
}
