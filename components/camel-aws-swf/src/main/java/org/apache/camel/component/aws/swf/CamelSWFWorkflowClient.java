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
package org.apache.camel.component.aws.swf;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.simpleworkflow.flow.DynamicWorkflowClientExternal;
import com.amazonaws.services.simpleworkflow.flow.DynamicWorkflowClientExternalImpl;
import com.amazonaws.services.simpleworkflow.flow.StartWorkflowOptions;
import com.amazonaws.services.simpleworkflow.flow.common.FlowHelpers;
import com.amazonaws.services.simpleworkflow.flow.common.WorkflowExecutionUtils;
import com.amazonaws.services.simpleworkflow.flow.worker.GenericWorkflowClientExternalImpl;
import com.amazonaws.services.simpleworkflow.model.ChildPolicy;
import com.amazonaws.services.simpleworkflow.model.DescribeWorkflowExecutionRequest;
import com.amazonaws.services.simpleworkflow.model.HistoryEvent;
import com.amazonaws.services.simpleworkflow.model.WorkflowExecution;
import com.amazonaws.services.simpleworkflow.model.WorkflowExecutionDetail;
import com.amazonaws.services.simpleworkflow.model.WorkflowExecutionInfo;
import com.amazonaws.services.simpleworkflow.model.WorkflowType;

public class CamelSWFWorkflowClient {
    private final SWFEndpoint endpoint;
    private final SWFConfiguration configuration;

    public CamelSWFWorkflowClient(SWFEndpoint endpoint, SWFConfiguration configuration) {
        this.endpoint = endpoint;
        this.configuration = configuration;
    }

    public void signalWorkflowExecution(String workflowId, String runId, String signalName, Object arguments) {
        DynamicWorkflowClientExternal dynamicWorkflowClientExternal = getDynamicWorkflowClient(workflowId, runId);
        dynamicWorkflowClientExternal.signalWorkflowExecution(signalName, toArray(arguments));
    }

    public Object getWorkflowExecutionState(String workflowId, String runId, Class<?> aClass) throws Throwable {
        DynamicWorkflowClientExternal dynamicWorkflowClientExternal = getDynamicWorkflowClient(workflowId, runId);
        return dynamicWorkflowClientExternal.getWorkflowExecutionState(aClass);
    }

    public void requestCancelWorkflowExecution(String workflowId, String runId) {
        DynamicWorkflowClientExternal dynamicWorkflowClientExternal = getDynamicWorkflowClient(workflowId, runId);
        dynamicWorkflowClientExternal.requestCancelWorkflowExecution();
    }

    public void terminateWorkflowExecution(String workflowId, String runId, String reason, String details, String childPolicy) {
        DynamicWorkflowClientExternal dynamicWorkflowClientExternal = getDynamicWorkflowClient(workflowId, runId);
        ChildPolicy policy = childPolicy != null ? ChildPolicy.valueOf(childPolicy) : null;
        dynamicWorkflowClientExternal.terminateWorkflowExecution(reason, details, policy);
    }

    public String[] startWorkflowExecution(String workflowId, String runId, String eventName, String version, Object arguments, List<String> tags) {
        DynamicWorkflowClientExternalImpl dynamicWorkflowClientExternal = (DynamicWorkflowClientExternalImpl) getDynamicWorkflowClient(workflowId, runId);

        WorkflowType workflowType = new WorkflowType();
        workflowType.setName(eventName);
        workflowType.setVersion(version);
        
        dynamicWorkflowClientExternal.setWorkflowType(workflowType);
        
        StartWorkflowOptions startWorkflowOptions = new StartWorkflowOptions();
        startWorkflowOptions.setTaskStartToCloseTimeoutSeconds(FlowHelpers.durationToSeconds(configuration.getTaskStartToCloseTimeout()));
        startWorkflowOptions.setExecutionStartToCloseTimeoutSeconds(FlowHelpers.durationToSeconds(configuration.getExecutionStartToCloseTimeout()));
        startWorkflowOptions.setTagList(tags);
        dynamicWorkflowClientExternal.setSchedulingOptions(startWorkflowOptions);
        
        dynamicWorkflowClientExternal.startWorkflowExecution(toArray(arguments));

        String newWorkflowId = dynamicWorkflowClientExternal.getWorkflowExecution().getWorkflowId();
        String newRunId = dynamicWorkflowClientExternal.getWorkflowExecution().getRunId();

        return new String[] {newWorkflowId, newRunId};
    }

    public Map<String, Object> describeWorkflowInstance(String workflowId, String runId) {
        DescribeWorkflowExecutionRequest describeRequest = new DescribeWorkflowExecutionRequest();
        describeRequest.setDomain(configuration.getDomainName());
        describeRequest.setExecution(new WorkflowExecution().withWorkflowId(workflowId).withRunId(runId));
        WorkflowExecutionDetail executionDetail = endpoint.getSWClient().describeWorkflowExecution(describeRequest);
        WorkflowExecutionInfo instanceMetadata = executionDetail.getExecutionInfo();

        Map<String, Object> info = new HashMap<>();
        info.put("closeStatus", instanceMetadata.getCloseStatus());
        info.put("closeTimestamp", instanceMetadata.getCloseTimestamp());
        info.put("executionStatus", instanceMetadata.getExecutionStatus());
        info.put("tagList", instanceMetadata.getTagList());
        info.put("executionDetail", executionDetail);
        return info;
    }

    public List<HistoryEvent> getWorkflowExecutionHistory(String workflowId, String runId) {
        return WorkflowExecutionUtils.getHistory(endpoint.getSWClient(),
                configuration.getDomainName(), new WorkflowExecution().withWorkflowId(workflowId).withRunId(runId));
    }

    DynamicWorkflowClientExternal getDynamicWorkflowClient(String workflowId, String runId) {
        GenericWorkflowClientExternalImpl genericClient = new GenericWorkflowClientExternalImpl(endpoint.getSWClient(), configuration.getDomainName());
        WorkflowExecution workflowExecution = new WorkflowExecution();
        workflowExecution.setWorkflowId(workflowId != null ? workflowId : genericClient.generateUniqueId());
        workflowExecution.setRunId(runId);
        return new DynamicWorkflowClientExternalImpl(workflowExecution, null, endpoint.getStartWorkflowOptions(), configuration.getDataConverter(), genericClient);
    }

    private Object[] toArray(Object input) {
        Object[] inputArray;
        if (input instanceof Object[]) {
            inputArray = (Object[])input;
        } else {
            inputArray = new Object[1];
            inputArray[0] = input;
        }
        return inputArray;
    }
}
