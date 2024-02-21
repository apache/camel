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
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Contains information about a Zeebe process
 */

@JsonInclude(Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProcessRequest implements ZeebeMessage {

    @JsonProperty(value = "process_id")
    private String processId;

    @JsonProperty("process_version")
    private int processVersion = -1;

    private Map<String, Object> variables = Collections.emptyMap();

    @JsonProperty("process_key")
    private long processKey = -1;

    @JsonProperty("process_instance_key")
    private long processInstanceKey = -1;

    public String getProcessId() {
        return processId;
    }

    public void setProcessId(String processId) {
        this.processId = processId;
    }

    public int getProcessVersion() {
        return processVersion;
    }

    public void setProcessVersion(int processVersion) {
        this.processVersion = processVersion;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }

    public long getProcessKey() {
        return processKey;
    }

    public void setProcessKey(long processKey) {
        this.processKey = processKey;
    }

    public long getProcessInstanceKey() {
        return processInstanceKey;
    }

    public void setProcessInstanceKey(long processInstanceKey) {
        this.processInstanceKey = processInstanceKey;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ProcessMessage{");
        sb.append("processId='").append(processId).append('\'');
        sb.append(", processVersion=").append(processVersion);
        sb.append(", variables=").append(variables);
        sb.append(", processKey=").append(processKey);
        sb.append(", processInstanceKey=").append(processInstanceKey);
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
        ProcessRequest that = (ProcessRequest) o;
        return processVersion == that.processVersion && processKey == that.processKey
                && processInstanceKey == that.processInstanceKey && processId.equals(that.processId)
                && variables.equals(that.variables);
    }

    @Override
    public int hashCode() {
        return Objects.hash(processId, processVersion, variables, processKey, processInstanceKey);
    }
}
