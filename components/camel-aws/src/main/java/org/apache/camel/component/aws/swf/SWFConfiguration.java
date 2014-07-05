/**
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

import java.util.Map;

import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflowClient;
import com.amazonaws.services.simpleworkflow.flow.ActivitySchedulingOptions;
import com.amazonaws.services.simpleworkflow.flow.DataConverter;
import com.amazonaws.services.simpleworkflow.flow.WorkflowTypeRegistrationOptions;
import com.amazonaws.services.simpleworkflow.flow.worker.ActivityTypeExecutionOptions;
import com.amazonaws.services.simpleworkflow.flow.worker.ActivityTypeRegistrationOptions;


public class SWFConfiguration {
    private String accessKey;
    private String secretKey;
    private AmazonSimpleWorkflowClient amazonSWClient;
    private Map<String, Object> clientConfigurationParameters;
    private Map<String, Object> sWClientParameters;
    private Map<String, Object> startWorkflowOptionsParameters;
    private String operation;
    private String domainName;
    private String activityList;
    private String workflowList;
    private String eventName;
    private String version;
    private String type;
    private String signalName;
    private String childPolicy;
    private String terminationReason;
    private String stateResultType;
    private String terminationDetails;
    private DataConverter dataConverter;
    private ActivitySchedulingOptions activitySchedulingOptions;
    private ActivityTypeExecutionOptions activityTypeExecutionOptions;
    private ActivityTypeRegistrationOptions activityTypeRegistrationOptions;
    private WorkflowTypeRegistrationOptions workflowTypeRegistrationOptions;

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getDomainName() {
        return domainName;
    }

    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }

    public String getActivityList() {
        return activityList;
    }

    public void setActivityList(String activityList) {
        this.activityList = activityList;
    }

    public String getWorkflowList() {
        return workflowList;
    }

    public void setWorkflowList(String workflowList) {
        this.workflowList = workflowList;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, Object> getClientConfigurationParameters() {
        return clientConfigurationParameters;
    }

    public void setClientConfigurationParameters(Map<String, Object> clientConfigurationParameters) {
        this.clientConfigurationParameters = clientConfigurationParameters;
    }

    public Map<String, Object> getsWClientParameters() {
        return sWClientParameters;
    }

    public void setsWClientParameters(Map<String, Object> sWClientParameters) {
        this.sWClientParameters = sWClientParameters;
    }

    public AmazonSimpleWorkflowClient getAmazonSWClient() {
        return amazonSWClient;
    }

    public void setAmazonSWClient(AmazonSimpleWorkflowClient amazonSWClient) {
        this.amazonSWClient = amazonSWClient;
    }
    public Map<String, Object> getStartWorkflowOptionsParameters() {
        return startWorkflowOptionsParameters;
    }

    public void setStartWorkflowOptionsParameters(Map<String, Object> startWorkflowOptionsParameters) {
        this.startWorkflowOptionsParameters = startWorkflowOptionsParameters;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getSignalName() {
        return signalName;
    }

    public void setSignalName(String signalName) {
        this.signalName = signalName;
    }

    public String getChildPolicy() {
        return childPolicy;
    }

    public void setChildPolicy(String childPolicy) {
        this.childPolicy = childPolicy;
    }

    public String getTerminationReason() {
        return terminationReason;
    }

    public void setTerminationReason(String terminationReason) {
        this.terminationReason = terminationReason;
    }

    public String getStateResultType() {
        return stateResultType;
    }

    public void setStateResultType(String stateResultType) {
        this.stateResultType = stateResultType;
    }

    public String getTerminationDetails() {
        return terminationDetails;
    }

    public void setTerminationDetails(String terminationDetails) {
        this.terminationDetails = terminationDetails;
    }

    public ActivityTypeExecutionOptions getActivityTypeExecutionOptions() {
        return activityTypeExecutionOptions;
    }

    public void setActivityTypeExecutionOptions(ActivityTypeExecutionOptions activityTypeExecutionOptions) {
        this.activityTypeExecutionOptions = activityTypeExecutionOptions;
    }

    public ActivityTypeRegistrationOptions getActivityTypeRegistrationOptions() {
        return activityTypeRegistrationOptions;
    }

    public void setActivityTypeRegistrationOptions(ActivityTypeRegistrationOptions activityTypeRegistrationOptions) {
        this.activityTypeRegistrationOptions = activityTypeRegistrationOptions;
    }

    public DataConverter getDataConverter() {
        return dataConverter;
    }

    public void setDataConverter(DataConverter dataConverter) {
        this.dataConverter = dataConverter;
    }

    public WorkflowTypeRegistrationOptions getWorkflowTypeRegistrationOptions() {
        return workflowTypeRegistrationOptions;
    }

    public void setWorkflowTypeRegistrationOptions(WorkflowTypeRegistrationOptions workflowTypeRegistrationOptions) {
        this.workflowTypeRegistrationOptions = workflowTypeRegistrationOptions;
    }

    public ActivitySchedulingOptions getActivitySchedulingOptions() {
        return activitySchedulingOptions;
    }

    public void setActivitySchedulingOptions(ActivitySchedulingOptions activitySchedulingOptions) {
        this.activitySchedulingOptions = activitySchedulingOptions;
    }
}
