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
package org.apache.camel.component.bonita.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ProcessDefinitionResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("displayDescription")
    private String displayDescription;

    @JsonProperty("deploymentDate")
    private String deploymentDate;

    @JsonProperty("description")
    private String description;

    @JsonProperty("activationState")
    private String activationState;

    @JsonProperty("name")
    private String name;

    @JsonProperty("deployedBy")
    private String deployedBy;

    @JsonProperty("displayName")
    private String displayName;

    @JsonProperty("actorinitiatorid")
    private String actorInitiatorId;

    @JsonProperty("last_update_date")
    private String lastUpdateDate;

    @JsonProperty("configurationState")
    private String configurationState;

    @JsonProperty("version")
    private String version;

    public ProcessDefinitionResponse() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDisplayDescription() {
        return displayDescription;
    }

    public void setDisplayDescription(String displayDescription) {
        this.displayDescription = displayDescription;
    }

    public String getDeploymentDate() {
        return deploymentDate;
    }

    public void setDeploymentDate(String deploymentDate) {
        this.deploymentDate = deploymentDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getActivationState() {
        return activationState;
    }

    public void setActivationState(String activationState) {
        this.activationState = activationState;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDeployedBy() {
        return deployedBy;
    }

    public void setDeployedBy(String deployedBy) {
        this.deployedBy = deployedBy;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getActorInitiatorId() {
        return actorInitiatorId;
    }

    public void setActorInitiatorId(String actorInitiatorId) {
        this.actorInitiatorId = actorInitiatorId;
    }

    public String getLastUpdateDate() {
        return lastUpdateDate;
    }

    public void setLastUpdateDate(String lastUpdateDate) {
        this.lastUpdateDate = lastUpdateDate;
    }

    public String getConfigurationState() {
        return configurationState;
    }

    public void setConfigurationState(String configurationState) {
        this.configurationState = configurationState;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

}
