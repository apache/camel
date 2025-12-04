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

package org.apache.camel.component.clickup.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class WebhookCreationCommand implements Serializable {

    @Serial
    private static final long serialVersionUID = 0L;

    @JsonProperty("endpoint")
    private String endpoint;

    @JsonProperty("events")
    private Set<String> events;

    @JsonProperty("space_id")
    private Long spaceId;

    @JsonProperty("folder_id")
    private Long folderId;

    @JsonProperty("list_id")
    private Long listId;

    @JsonProperty("task_id")
    private String taskId;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public Set<String> getEvents() {
        return events;
    }

    public void setEvents(Set<String> events) {
        this.events = events;
    }

    public Long getSpaceId() {
        return spaceId;
    }

    public void setSpaceId(Long spaceId) {
        this.spaceId = spaceId;
    }

    public Long getFolderId() {
        return folderId;
    }

    public void setFolderId(Long folderId) {
        this.folderId = folderId;
    }

    public Long getListId() {
        return listId;
    }

    public void setListId(Long listId) {
        this.listId = listId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    @Override
    public String toString() {
        return "WebhookCreationCommand{" + "endpoint='"
                + endpoint + '\'' + ", events="
                + events + ", spaceId="
                + spaceId + ", folderId="
                + folderId + ", listId="
                + listId + ", taskId='"
                + taskId + '\'' + '}';
    }
}
