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

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProcessResponse extends AbstractZeebeResponse {
    @JsonProperty(value = "process_id")
    private String processId;

    @JsonProperty("process_instance_key")
    private long processInstanceKey = -1;

    @JsonProperty("process_version")
    private int processVersion = -1;

    @JsonProperty("process_key")
    private long processKey = -1;

    public String getProcessId() {
        return processId;
    }

    public void setProcessId(String processId) {
        this.processId = processId;
    }

    public long getProcessInstanceKey() {
        return processInstanceKey;
    }

    public void setProcessInstanceKey(long processInstanceKey) {
        this.processInstanceKey = processInstanceKey;
    }

    public int getProcessVersion() {
        return processVersion;
    }

    public void setProcessVersion(int processVersion) {
        this.processVersion = processVersion;
    }

    public long getProcessKey() {
        return processKey;
    }

    public void setProcessKey(long processKey) {
        this.processKey = processKey;
    }

    @Override
    public String toString() {
        return "ProcessResponse{" + "processId='" + processId + '\'' +
               ", processInstanceKey=" + processInstanceKey +
               ", processVersion=" + processVersion +
               ", processKey=" + processKey +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        ProcessResponse that = (ProcessResponse) o;
        return processInstanceKey == that.processInstanceKey && processVersion == that.processVersion
                && processKey == that.processKey && processId.equals(that.processId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), processId, processInstanceKey, processVersion, processKey);
    }
}
