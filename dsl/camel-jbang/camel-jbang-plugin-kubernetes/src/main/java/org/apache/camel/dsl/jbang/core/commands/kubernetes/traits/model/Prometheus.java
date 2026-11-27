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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "enabled", "podMonitor", "podMonitorLabels", "containerPort", "containerPortName", "path" })
public class Prometheus {

    @JsonProperty("enabled")
    @JsonPropertyDescription("Can be used to enable or disable a trait.")
    @JsonSetter(nulls = Nulls.SKIP)
    private Boolean enabled;

    @JsonProperty("podMonitor")
    @JsonPropertyDescription("Whether a PodMonitor resource is created (default `true`). The PodMonitor needs the Prometheus Operator in the cluster.")
    @JsonSetter(nulls = Nulls.SKIP)
    private Boolean podMonitor;

    @JsonProperty("podMonitorLabels")
    @JsonPropertyDescription("The labels of the PodMonitor, such as `release=prometheus`, so the Prometheus Operator selects it.")
    @JsonSetter(nulls = Nulls.SKIP)
    private List<String> podMonitorLabels;

    @JsonProperty("containerPort")
    @JsonPropertyDescription("The port of the container that serves the metrics (default the management port, `9876`).")
    @JsonSetter(nulls = Nulls.SKIP)
    private Long containerPort;

    @JsonProperty("containerPortName")
    @JsonPropertyDescription("The name of the container port that serves the metrics (default `management`). A port the container already has for the same number keeps its name.")
    @JsonSetter(nulls = Nulls.SKIP)
    private String containerPortName;

    @JsonProperty("path")
    @JsonPropertyDescription("The path of the metrics (default `/observe/metrics`).")
    @JsonSetter(nulls = Nulls.SKIP)
    private String path;

    public Prometheus() {
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getPodMonitor() {
        return podMonitor;
    }

    public void setPodMonitor(Boolean podMonitor) {
        this.podMonitor = podMonitor;
    }

    public List<String> getPodMonitorLabels() {
        return podMonitorLabels;
    }

    public void setPodMonitorLabels(List<String> podMonitorLabels) {
        this.podMonitorLabels = podMonitorLabels;
    }

    public Long getContainerPort() {
        return containerPort;
    }

    public void setContainerPort(Long containerPort) {
        this.containerPort = containerPort;
    }

    public String getContainerPortName() {
        return containerPortName;
    }

    public void setContainerPortName(String containerPortName) {
        this.containerPortName = containerPortName;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
