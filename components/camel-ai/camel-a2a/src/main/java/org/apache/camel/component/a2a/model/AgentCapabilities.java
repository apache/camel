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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A2A agent capabilities.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentCapabilities {
    private Boolean streaming;
    private Boolean pushNotifications;
    private Boolean extendedAgentCard;
    private List<AgentExtension> extensions;

    public AgentCapabilities() {
    }

    @JsonProperty("streaming")
    public Boolean getStreaming() {
        return streaming;
    }

    @JsonIgnore
    public boolean isStreaming() {
        return Boolean.TRUE.equals(streaming);
    }

    @JsonProperty("streaming")
    public void setStreaming(Boolean streaming) {
        this.streaming = streaming;
    }

    @JsonProperty("pushNotifications")
    public Boolean getPushNotifications() {
        return pushNotifications;
    }

    @JsonIgnore
    public boolean isPushNotifications() {
        return Boolean.TRUE.equals(pushNotifications);
    }

    @JsonProperty("pushNotifications")
    public void setPushNotifications(Boolean pushNotifications) {
        this.pushNotifications = pushNotifications;
    }

    @JsonProperty("extendedAgentCard")
    public Boolean getExtendedAgentCard() {
        return extendedAgentCard;
    }

    @JsonIgnore
    public boolean isExtendedAgentCard() {
        return Boolean.TRUE.equals(extendedAgentCard);
    }

    @JsonProperty("extendedAgentCard")
    public void setExtendedAgentCard(Boolean extendedAgentCard) {
        this.extendedAgentCard = extendedAgentCard;
    }

    public List<AgentExtension> getExtensions() {
        return extensions;
    }

    public void setExtensions(List<AgentExtension> extensions) {
        this.extensions = extensions;
    }
}
