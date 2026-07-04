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
package org.apache.camel.component.a2a.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A2A SSE stream response wrapper. Carries exactly one of: task, message, statusUpdate, artifactUpdate.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StreamResponse {
    private Task task;
    private Message message;
    private TaskStatusUpdateEvent statusUpdate;
    private TaskArtifactUpdateEvent artifactUpdate;

    public StreamResponse() {
    }

    public static StreamResponse ofTask(Task task) {
        StreamResponse response = new StreamResponse();
        response.setTask(task);
        return response;
    }

    public static StreamResponse ofMessage(Message message) {
        StreamResponse response = new StreamResponse();
        response.setMessage(message);
        return response;
    }

    public static StreamResponse ofStatusUpdate(TaskStatusUpdateEvent statusUpdate) {
        StreamResponse response = new StreamResponse();
        response.setStatusUpdate(statusUpdate);
        return response;
    }

    public static StreamResponse ofArtifactUpdate(TaskArtifactUpdateEvent artifactUpdate) {
        StreamResponse response = new StreamResponse();
        response.setArtifactUpdate(artifactUpdate);
        return response;
    }

    public Task getTask() {
        return task;
    }

    public void setTask(Task task) {
        assertCanSet(task);
        this.task = task;
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        assertCanSet(message);
        this.message = message;
    }

    public TaskStatusUpdateEvent getStatusUpdate() {
        return statusUpdate;
    }

    public void setStatusUpdate(TaskStatusUpdateEvent statusUpdate) {
        assertCanSet(statusUpdate);
        this.statusUpdate = statusUpdate;
    }

    public TaskArtifactUpdateEvent getArtifactUpdate() {
        return artifactUpdate;
    }

    public void setArtifactUpdate(TaskArtifactUpdateEvent artifactUpdate) {
        assertCanSet(artifactUpdate);
        this.artifactUpdate = artifactUpdate;
    }

    @JsonIgnore
    public void validate() {
        if (oneOfCount() != 1) {
            throw new IllegalArgumentException(
                    "StreamResponse must contain exactly one of task, message, statusUpdate, or artifactUpdate");
        }
    }

    private void assertCanSet(Object value) {
        if (value != null && oneOfCount() > 0) {
            throw new IllegalArgumentException(
                    "StreamResponse must contain exactly one of task, message, statusUpdate, or artifactUpdate");
        }
    }

    private int oneOfCount() {
        int count = 0;
        count += task != null ? 1 : 0;
        count += message != null ? 1 : 0;
        count += statusUpdate != null ? 1 : 0;
        count += artifactUpdate != null ? 1 : 0;
        return count;
    }
}
