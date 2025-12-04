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

package org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"enabled", "containerPort", "containerPortName", "expose", "servicePort", "servicePortName"})
public class Jolokia {
    @JsonProperty("enabled")
    @JsonPropertyDescription("Can be used to enable or disable a trait.")
    @JsonSetter(nulls = Nulls.SKIP)
    private Boolean enabled;

    @JsonProperty("containerPort")
    @JsonPropertyDescription("To configure a different jolokia port exposed by the container (default `8778`).")
    @JsonSetter(nulls = Nulls.SKIP)
    private Long containerPort;

    @JsonProperty("containerPortName")
    @JsonPropertyDescription(
            "To configure a different jolokia port name for the port exposed by the container. It defaults to `jolokia`.")
    @JsonSetter(nulls = Nulls.SKIP)
    private String containerPortName;

    @JsonProperty("expose")
    @JsonPropertyDescription(
            "Can be used to enable/disable jolokia exposure via kubernetes Service. Requires Service to be enabled to be applicable.")
    @JsonSetter(nulls = Nulls.SKIP)
    private Boolean expose;

    @JsonProperty("servicePort")
    @JsonPropertyDescription("To configure a different jolokia port exposed by the service (default `8778`).")
    @JsonSetter(nulls = Nulls.SKIP)
    private Long servicePort;

    @JsonProperty("servicePortName")
    @JsonPropertyDescription(
            "To configure a different jolokia port name for the port exposed by the service. It defaults to `jolokia`.")
    @JsonSetter(nulls = Nulls.SKIP)
    private String servicePortName;

    public Jolokia() {}

    public Boolean getEnabled() {
        return this.enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Long getContainerPort() {
        return this.containerPort;
    }

    public void setContainerPort(Long port) {
        this.containerPort = port;
    }

    public String getContainerPortName() {
        return this.containerPortName;
    }

    public void setContainerPortName(String portName) {
        this.containerPortName = portName;
    }

    public Boolean getExpose() {
        return this.expose;
    }

    public void setExpose(Boolean expose) {
        this.expose = expose;
    }

    public Long getServicePort() {
        return this.servicePort;
    }

    public void setServicePort(Long port) {
        this.servicePort = port;
    }

    public String getServicePortName() {
        return this.servicePortName;
    }

    public void setServicePortName(String portName) {
        this.servicePortName = portName;
    }
}
