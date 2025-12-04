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
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Sets;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Webhook implements Serializable {

    @Serial
    private static final long serialVersionUID = 0L;

    @JsonProperty("id")
    private String id = null;

    @JsonProperty("userid")
    private Integer userid = null;

    @JsonProperty("team_id")
    private Integer teamId = null;

    @JsonProperty("endpoint")
    private String endpoint = null;

    @JsonProperty("client_id")
    private String clientId = null;

    @JsonProperty("events")
    private Set<String> events = new HashSet<>();

    @JsonProperty("task_id")
    private String taskId = null;

    @JsonProperty("list_id")
    private String listId = null;

    @JsonProperty("folder_id")
    private String folderId = null;

    @JsonProperty("space_id")
    private String spaceId = null;

    @JsonProperty("health")
    private WebhookHealth health = null;

    @JsonProperty("secret")
    private String secret = null;

    public Integer getUserid() {
        return userid;
    }

    public void setUserid(Integer userid) {
        this.userid = userid;
    }

    public Integer getTeamId() {
        return teamId;
    }

    public void setTeamId(Integer teamId) {
        this.teamId = teamId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public Set<String> getEvents() {
        return events;
    }

    public void setEvents(Set<String> events) {
        this.events = events;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getListId() {
        return listId;
    }

    public void setListId(String listId) {
        this.listId = listId;
    }

    public String getFolderId() {
        return folderId;
    }

    public void setFolderId(String folderId) {
        this.folderId = folderId;
    }

    public String getSpaceId() {
        return spaceId;
    }

    public void setSpaceId(String spaceId) {
        this.spaceId = spaceId;
    }

    public WebhookHealth getHealth() {
        return health;
    }

    public void setHealth(WebhookHealth health) {
        this.health = health;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean matchesConfiguration(String endpointUrl, Set<String> events) {
        boolean sameEndpointUrl = this.getEndpoint().equals(endpointUrl);
        boolean sameEvents = Sets.symmetricDifference(this.getEvents(), events).isEmpty();

        return sameEndpointUrl && sameEvents;
    }

    @Override
    public String toString() {
        return "Webhook{" + "id='"
                + id + '\'' + ", userid="
                + userid + ", teamId="
                + teamId + ", endpoint='"
                + endpoint + '\'' + ", clientId='"
                + clientId + '\'' + ", events="
                + events + ", taskId='"
                + taskId + '\'' + ", listId='"
                + listId + '\'' + ", folderId='"
                + folderId + '\'' + ", spaceId='"
                + spaceId + '\'' + ", health="
                + health + ", secret='"
                + secret + '\'' + '}';
    }
}
