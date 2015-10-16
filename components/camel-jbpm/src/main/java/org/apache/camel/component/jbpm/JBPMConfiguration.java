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
import javax.naming.InitialContext;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.kie.api.task.model.OrganizationalEntity;
import org.kie.api.task.model.Status;
import org.kie.api.task.model.Task;

@UriParams
public class JBPMConfiguration {

    /**
     * Specifies the operation to perform
     */
    @UriParam(label = "producer", defaultValue = "startProcess")
    private String operation;
    /**
     * Specifies the value to use
     */
    @UriParam
    private Object value;
    private String processId;
    private Map<String, Object> parameters;
    /**
     * Specifies the process instance to interact with
     */
    @UriParam @Metadata(required = "true")
    private Long processInstanceId;
    private String eventType;
    private Object event;
    private Integer maxNumber;
    private String identifier;
    private Long workItemId;
    private Long taskId;
    private String userId;
    private String language;
    private String targetUserId;
    private Long attachmentId;
    private Long contentId;
    private Task task;
    private List<OrganizationalEntity> entities;
    private List<Status> statuses;

    //connection
    private String userName;
    private String password;
    /**
     * Specifies the connection url to the jBPM system
     */
    @UriPath @Metadata(required = "true")
    private URL connectionURL;
    /**
     * Specifies the deploymentId to use
     */
    @UriParam @Metadata(required = "true")
    private String deploymentId;
    private Integer timeout;
    private Class[] extraJaxbClasses;

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public String getProcessId() {
        return processId;
    }

    public void setProcessId(String processId) {
        this.processId = processId;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public Long getProcessInstanceId() {
        return processInstanceId;
    }

    public void setProcessInstanceId(Long processInstanceId) {
        this.processInstanceId = processInstanceId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Object getEvent() {
        return event;
    }

    public void setEvent(Object event) {
        this.event = event;
    }

    public Integer getMaxNumber() {
        return maxNumber;
    }

    public void setMaxNumber(Integer maxNumber) {
        this.maxNumber = maxNumber;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public Long getWorkItemId() {
        return workItemId;
    }

    public void setWorkItemId(Long workItemId) {
        this.workItemId = workItemId;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Task getTask() {
        return task;
    }

    public void setTask(Task task) {
        this.task = task;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(String targetUserId) {
        this.targetUserId = targetUserId;
    }

    public Long getAttachmentId() {
        return attachmentId;
    }

    public void setAttachmentId(Long attachmentId) {
        this.attachmentId = attachmentId;
    }

    public Long getContentId() {
        return contentId;
    }

    public void setContentId(Long contentId) {
        this.contentId = contentId;
    }

    public List<OrganizationalEntity> getEntities() {
        return entities;
    }

    public void setEntities(List<OrganizationalEntity> entities) {
        this.entities = entities;
    }

    public List<Status> getStatuses() {
        return statuses;
    }

    public void setStatuses(List<Status> statuses) {
        this.statuses = statuses;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public URL getConnectionURL() {
        return connectionURL;
    }

    public void setConnectionURL(URL connectionURL) {
        this.connectionURL = connectionURL;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public Class[] getExtraJaxbClasses() {
        return extraJaxbClasses;
    }

    public void setExtraJaxbClasses(Class[] extraJaxbClasses) {
        this.extraJaxbClasses = extraJaxbClasses;
    }
}
