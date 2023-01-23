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

package org.apache.camel.component.zeebe.model;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true, value = { "variables" }, allowGetters = true)
public class JobWorkerMessage implements ZeebeMessage {
    private long key;
    private String type;
    private Map<String, String> customHeaders = Collections.emptyMap();
    private long processInstanceKey;
    private String bpmnProcessId;
    private int processDefinitionVersion;
    private long processDefinitionKey;
    private String elementId;
    private long elementInstanceKey;
    private String worker;
    private int retries;
    private long deadline;
    @JsonProperty("variablesAsMap")
    private Map<String, Object> variables = Collections.emptyMap();

    public long getKey() {
        return key;
    }

    public void setKey(long key) {
        this.key = key;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, String> getCustomHeaders() {
        return customHeaders;
    }

    public void setCustomHeaders(Map<String, String> customHeaders) {
        this.customHeaders = customHeaders;
    }

    public long getProcessInstanceKey() {
        return processInstanceKey;
    }

    public void setProcessInstanceKey(long processInstanceKey) {
        this.processInstanceKey = processInstanceKey;
    }

    public String getBpmnProcessId() {
        return bpmnProcessId;
    }

    public void setBpmnProcessId(String bpmnProcessId) {
        this.bpmnProcessId = bpmnProcessId;
    }

    public int getProcessDefinitionVersion() {
        return processDefinitionVersion;
    }

    public void setProcessDefinitionVersion(int processDefinitionVersion) {
        this.processDefinitionVersion = processDefinitionVersion;
    }

    public long getProcessDefinitionKey() {
        return processDefinitionKey;
    }

    public void setProcessDefinitionKey(long processDefinitionKey) {
        this.processDefinitionKey = processDefinitionKey;
    }

    public String getElementId() {
        return elementId;
    }

    public void setElementId(String elementId) {
        this.elementId = elementId;
    }

    public long getElementInstanceKey() {
        return elementInstanceKey;
    }

    public void setElementInstanceKey(long elementInstanceKey) {
        this.elementInstanceKey = elementInstanceKey;
    }

    public String getWorker() {
        return worker;
    }

    public void setWorker(String worker) {
        this.worker = worker;
    }

    public int getRetries() {
        return retries;
    }

    public void setRetries(int retries) {
        this.retries = retries;
    }

    public long getDeadline() {
        return deadline;
    }

    public void setDeadline(long deadline) {
        this.deadline = deadline;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("JobWorkerMessage{");
        sb.append("key=").append(key);
        sb.append(", type='").append(type).append('\'');
        sb.append(", customHeaders=").append(customHeaders);
        sb.append(", processInstanceKey=").append(processInstanceKey);
        sb.append(", bpmnProcessId='").append(bpmnProcessId).append('\'');
        sb.append(", processDefinitionVersion=").append(processDefinitionVersion);
        sb.append(", processDefinitionKey=").append(processDefinitionKey);
        sb.append(", elementId='").append(elementId).append('\'');
        sb.append(", elementInstanceKey=").append(elementInstanceKey);
        sb.append(", worker='").append(worker).append('\'');
        sb.append(", retries=").append(retries);
        sb.append(", deadline=").append(deadline);
        sb.append(", variables=").append(variables);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JobWorkerMessage that = (JobWorkerMessage) o;
        return key == that.key && processInstanceKey == that.processInstanceKey
                && processDefinitionVersion == that.processDefinitionVersion
                && processDefinitionKey == that.processDefinitionKey && elementInstanceKey == that.elementInstanceKey
                && retries == that.retries && deadline == that.deadline && Objects.equals(type, that.type)
                && Objects.equals(customHeaders, that.customHeaders) && Objects.equals(bpmnProcessId, that.bpmnProcessId)
                && Objects.equals(elementId, that.elementId) && Objects.equals(worker, that.worker)
                && Objects.equals(variables, that.variables);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, type, customHeaders, processInstanceKey, bpmnProcessId, processDefinitionVersion,
                processDefinitionKey, elementId, elementInstanceKey, worker, retries, deadline, variables);
    }
}
