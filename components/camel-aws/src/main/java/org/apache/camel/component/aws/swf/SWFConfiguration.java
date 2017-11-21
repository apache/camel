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

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class SWFConfiguration {

    @UriPath(enums = "activity,workflow")
    @Metadata(required = "true")
    private String type;
    @UriParam
    private AmazonSimpleWorkflowClient amazonSWClient;
    @UriParam
    private String accessKey;
    @UriParam
    private String secretKey;
    @UriParam(label = "producer,workflow", defaultValue = "START", enums = "SIGNAL,CANCEL,TERMINATE,GET_STATE,START,DESCRIBE,GET_HISTORY")
    private String operation = "START";
    @UriParam(label = "common")
    private String region;
    @UriParam
    private String domainName;
    @UriParam(label = "consumer,activity")
    private String activityList;
    @UriParam(label = "consumer,workflow")
    private String workflowList;
    @UriParam
    private String eventName;
    @UriParam
    private String version;
    @UriParam(label = "producer,workflow")
    private String signalName;
    @UriParam(label = "producer,workflow")
    private String childPolicy;
    @UriParam(label = "producer,workflow")
    private String terminationReason;
    @UriParam(label = "producer,workflow")
    private String stateResultType;
    @UriParam(label = "producer,workflow")
    private String terminationDetails;
    @UriParam(label = "producer,workflow", defaultValue = "3600")
    private String executionStartToCloseTimeout = "3600";
    @UriParam(label = "producer,workflow", defaultValue = "600")
    private String taskStartToCloseTimeout = "600";
    @UriParam
    private DataConverter dataConverter;
    @UriParam(label = "producer,activity")
    private ActivitySchedulingOptions activitySchedulingOptions;
    @UriParam(label = "consumer,activity")
    private ActivityTypeExecutionOptions activityTypeExecutionOptions;
    @UriParam(label = "consumer,activity")
    private ActivityTypeRegistrationOptions activityTypeRegistrationOptions;
    @UriParam(label = "consumer,workflow")
    private WorkflowTypeRegistrationOptions workflowTypeRegistrationOptions;
    @UriParam(label = "consumer,activity", defaultValue = "100")
    private int activityThreadPoolSize = 100; // aws-sdk default

    @UriParam(label = "advanced", prefix = "clientConfiguration.", multiValue = true)
    private Map<String, Object> clientConfigurationParameters;
    @UriParam(label = "advanced", prefix = "sWClient.", multiValue = true)
    private Map<String, Object> sWClientParameters;
    @UriParam(label = "advanced", prefix = "startWorkflowOptions.", multiValue = true)
    private Map<String, Object> startWorkflowOptionsParameters;

    public String getAccessKey() {
        return accessKey;
    }

    /**
     * Amazon AWS Access Key.
     */
    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    /**
     * Amazon AWS Secret Key.
     */
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getRegion() {
        return region;
    }

    /**
     * Amazon AWS Region.
     */
    public void setRegion(String region) {
        this.region = region;
    }

    public String getDomainName() {
        return domainName;
    }

    /**
     * The workflow domain to use.
     */
    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }

    public String getActivityList() {
        return activityList;
    }

    /**
     * The list name to consume activities from.
     */
    public void setActivityList(String activityList) {
        this.activityList = activityList;
    }

    public String getWorkflowList() {
        return workflowList;
    }

    /**
     * The list name to consume workflows from.
     */
    public void setWorkflowList(String workflowList) {
        this.workflowList = workflowList;
    }

    public String getEventName() {
        return eventName;
    }

    /**
     * The workflow or activity event name to use.
     */
    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public String getVersion() {
        return version;
    }

    /**
     * The workflow or activity event version to use.
     */
    public void setVersion(String version) {
        this.version = version;
    }

    public String getType() {
        return type;
    }

    /**
     * Activity or workflow
     */
    public void setType(String type) {
        this.type = type;
    }

    public Map<String, Object> getClientConfigurationParameters() {
        return clientConfigurationParameters;
    }

    /**
     * To configure the ClientConfiguration using the key/values from the Map.
     */
    public void setClientConfigurationParameters(Map<String, Object> clientConfigurationParameters) {
        this.clientConfigurationParameters = clientConfigurationParameters;
    }

    public Map<String, Object> getSWClientParameters() {
        return sWClientParameters;
    }

    /**
     * To configure the AmazonSimpleWorkflowClient using the key/values from the
     * Map.
     */
    public void setSWClientParameters(Map<String, Object> sWClientParameters) {
        this.sWClientParameters = sWClientParameters;
    }

    public AmazonSimpleWorkflowClient getAmazonSWClient() {
        return amazonSWClient;
    }

    /**
     * To use the given AmazonSimpleWorkflowClient as client
     */
    public void setAmazonSWClient(AmazonSimpleWorkflowClient amazonSWClient) {
        this.amazonSWClient = amazonSWClient;
    }

    public Map<String, Object> getStartWorkflowOptionsParameters() {
        return startWorkflowOptionsParameters;
    }

    /**
     * To configure the StartWorkflowOptions using the key/values from the Map.
     * 
     * @param startWorkflowOptionsParameters
     */
    public void setStartWorkflowOptionsParameters(Map<String, Object> startWorkflowOptionsParameters) {
        this.startWorkflowOptionsParameters = startWorkflowOptionsParameters;
    }

    public String getOperation() {
        return operation;
    }

    /**
     * Workflow operation
     */
    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getSignalName() {
        return signalName;
    }

    /**
     * The name of the signal to send to the workflow.
     */
    public void setSignalName(String signalName) {
        this.signalName = signalName;
    }

    public String getChildPolicy() {
        return childPolicy;
    }

    /**
     * The policy to use on child workflows when terminating a workflow.
     */
    public void setChildPolicy(String childPolicy) {
        this.childPolicy = childPolicy;
    }

    public String getTerminationReason() {
        return terminationReason;
    }

    /**
     * The reason for terminating a workflow.
     */
    public void setTerminationReason(String terminationReason) {
        this.terminationReason = terminationReason;
    }

    public String getStateResultType() {
        return stateResultType;
    }

    /**
     * The type of the result when a workflow state is queried.
     */
    public void setStateResultType(String stateResultType) {
        this.stateResultType = stateResultType;
    }

    public String getTerminationDetails() {
        return terminationDetails;
    }

    /**
     * Details for terminating a workflow.
     */
    public void setTerminationDetails(String terminationDetails) {
        this.terminationDetails = terminationDetails;
    }

    public ActivityTypeExecutionOptions getActivityTypeExecutionOptions() {
        return activityTypeExecutionOptions;
    }

    /**
     * Activity execution options
     */
    public void setActivityTypeExecutionOptions(ActivityTypeExecutionOptions activityTypeExecutionOptions) {
        this.activityTypeExecutionOptions = activityTypeExecutionOptions;
    }

    public ActivityTypeRegistrationOptions getActivityTypeRegistrationOptions() {
        return activityTypeRegistrationOptions;
    }

    /**
     * Activity registration options
     */
    public void setActivityTypeRegistrationOptions(ActivityTypeRegistrationOptions activityTypeRegistrationOptions) {
        this.activityTypeRegistrationOptions = activityTypeRegistrationOptions;
    }

    public DataConverter getDataConverter() {
        return dataConverter;
    }

    /**
     * An instance of com.amazonaws.services.simpleworkflow.flow.DataConverter
     * to use for serializing/deserializing the data.
     */
    public void setDataConverter(DataConverter dataConverter) {
        this.dataConverter = dataConverter;
    }

    public WorkflowTypeRegistrationOptions getWorkflowTypeRegistrationOptions() {
        return workflowTypeRegistrationOptions;
    }

    /**
     * Workflow registration options
     */
    public void setWorkflowTypeRegistrationOptions(WorkflowTypeRegistrationOptions workflowTypeRegistrationOptions) {
        this.workflowTypeRegistrationOptions = workflowTypeRegistrationOptions;
    }

    public ActivitySchedulingOptions getActivitySchedulingOptions() {
        return activitySchedulingOptions;
    }

    /**
     * Activity scheduling options
     */
    public void setActivitySchedulingOptions(ActivitySchedulingOptions activitySchedulingOptions) {
        this.activitySchedulingOptions = activitySchedulingOptions;
    }

    public int getActivityThreadPoolSize() {
        return activityThreadPoolSize;
    }

    /**
     * Maximum number of threads in work pool for activity.
     */
    public void setActivityThreadPoolSize(int activityThreadPoolSize) {
        this.activityThreadPoolSize = activityThreadPoolSize;
    }

    /**
     * Set the execution start to close timeout.
     */
    public String getExecutionStartToCloseTimeout() {
        return executionStartToCloseTimeout;
    }

    public void setExecutionStartToCloseTimeout(String executionStartToCloseTimeout) {
        this.executionStartToCloseTimeout = executionStartToCloseTimeout;
    }

    /**
     * Set the task start to close timeout.
     */
    public String getTaskStartToCloseTimeout() {
        return taskStartToCloseTimeout;
    }

    public void setTaskStartToCloseTimeout(String taskStartToCloseTimeout) {
        this.taskStartToCloseTimeout = taskStartToCloseTimeout;
    }
}
