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
package org.apache.camel.component.jbpm;

import java.net.URL;
import java.util.List;
import java.util.Map;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.kie.api.task.model.OrganizationalEntity;
import org.kie.api.task.model.Status;
import org.kie.api.task.model.Task;

@UriParams
public class JBPMConfiguration {

    @UriPath @Metadata(required = "true")
    private URL connectionURL;
    @UriParam(label = "producer", defaultValue = "startProcess")
    private String operation;
    @UriParam @Metadata(required = "true")
    private String deploymentId;
    @UriParam
    private Long processInstanceId;
    @UriParam
    private Object value;
    @UriParam
    private String processId;
    @UriParam
    private String eventType;
    @UriParam
    private Object event;
    @UriParam
    private Integer maxNumber;
    @UriParam
    private String identifier;
    @UriParam
    private Long workItemId;
    @UriParam
    private Long taskId;
    @UriParam
    private String userId;
    @UriParam
    private String language;
    @UriParam
    private String targetUserId;
    @UriParam
    private Long attachmentId;
    @UriParam
    private Long contentId;
    @UriParam
    private Task task;
    @UriParam(label = "advanced")
    private List<OrganizationalEntity> entities;
    @UriParam(label = "filter")
    private List<Status> statuses;
    @UriParam(label = "security", secret = true)
    private String userName;
    @UriParam(label = "security", secret = true)
    private String password;
    @UriParam
    private Integer timeout;
    @UriParam(label = "advanced")
    private Map<String, Object> parameters;
    @UriParam(label = "advanced")
    private Class[] extraJaxbClasses;

    public String getOperation() {
        return operation;
    }

    /**
     * The operation to perform
     */
    public void setOperation(String operation) {
        this.operation = operation;
    }

    public Object getValue() {
        return value;
    }

    /**
     * the value to assign to the global identifier
     */
    public void setValue(Object value) {
        this.value = value;
    }

    public String getProcessId() {
        return processId;
    }

    /**
     * the id of the process that should be acted upon
     */
    public void setProcessId(String processId) {
        this.processId = processId;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    /**
     * the variables that should be set for various operations
     */
    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public Long getProcessInstanceId() {
        return processInstanceId;
    }

    /**
     * the id of the process instance
     */
    public void setProcessInstanceId(Long processInstanceId) {
        this.processInstanceId = processInstanceId;
    }

    public String getEventType() {
        return eventType;
    }

    /**
     * the type of event to use when signalEvent operation is performed
     */
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Object getEvent() {
        return event;
    }

    /**
     * the data associated with this event when signalEvent operation is performed
     */
    public void setEvent(Object event) {
        this.event = event;
    }

    public Integer getMaxNumber() {
        return maxNumber;
    }

    /**
     * the maximum number of rules that should be fired
     */
    public void setMaxNumber(Integer maxNumber) {
        this.maxNumber = maxNumber;
    }

    public String getIdentifier() {
        return identifier;
    }

    /**
     * identifier the global identifier
     */
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public Long getWorkItemId() {
        return workItemId;
    }

    /**
     * the id of the work item
     */
    public void setWorkItemId(Long workItemId) {
        this.workItemId = workItemId;
    }

    public Long getTaskId() {
        return taskId;
    }

    /**
     *the id of the task
     */
    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getUserId() {
        return userId;
    }

    /**
     * userId to use with task operations
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Task getTask() {
        return task;
    }

    /**
     * The task instance to use with task operations
     */
    public void setTask(Task task) {
        this.task = task;
    }

    public String getLanguage() {
        return language;
    }

    /**
     * The language to use when filtering user tasks
     */
    public void setLanguage(String language) {
        this.language = language;
    }

    public String getTargetUserId() {
        return targetUserId;
    }

    /**
     * The targetUserId used when delegating a task
     */
    public void setTargetUserId(String targetUserId) {
        this.targetUserId = targetUserId;
    }

    public Long getAttachmentId() {
        return attachmentId;
    }

    /**
     * attachId to use when retrieving attachments
     */
    public void setAttachmentId(Long attachmentId) {
        this.attachmentId = attachmentId;
    }

    public Long getContentId() {
        return contentId;
    }

    /**
     * contentId to use when retrieving attachments
     */
    public void setContentId(Long contentId) {
        this.contentId = contentId;
    }

    public List<OrganizationalEntity> getEntities() {
        return entities;
    }

    /**
     * The potentialOwners when nominateTask operation is performed
     */
    public void setEntities(List<OrganizationalEntity> entities) {
        this.entities = entities;
    }

    public List<Status> getStatuses() {
        return statuses;
    }

    /**
     * The list of status to use when filtering tasks
     */
    public void setStatuses(List<Status> statuses) {
        this.statuses = statuses;
    }

    public String getUserName() {
        return userName;
    }

    /**
     * Username for authentication
     */
    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Password for authentication
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public URL getConnectionURL() {
        return connectionURL;
    }

    /**
     * The URL to the jBPM server.
     */
    public void setConnectionURL(URL connectionURL) {
        this.connectionURL = connectionURL;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    /**
     * The id of the deployment
     */
    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    public Integer getTimeout() {
        return timeout;
    }

    /**
     * A timeout value
     */
    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public Class[] getExtraJaxbClasses() {
        return extraJaxbClasses;
    }

    /**
     * To load additional classes when working with XML
     */
    public void setExtraJaxbClasses(Class[] extraJaxbClasses) {
        this.extraJaxbClasses = extraJaxbClasses;
    }
}
