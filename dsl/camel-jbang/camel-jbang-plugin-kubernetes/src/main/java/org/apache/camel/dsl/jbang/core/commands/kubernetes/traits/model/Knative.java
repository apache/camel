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
        "auto", "channelSinks", "channelSources", "configuration", "enabled", "endpointSinks", "endpointSources",
        "eventSinks", "eventSources", "filterEventType", "filters", "sinkBinding" })
public class Knative {
    @JsonProperty("auto")
    @JsonPropertyDescription("Enable automatic discovery of all trait properties.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Boolean auto;
    @JsonProperty("channelSinks")
    @JsonPropertyDescription("List of channels used as destination of camel routes. Can contain simple channel names or full Camel URIs.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private List<String> channelSinks;
    @JsonProperty("channelSources")
    @JsonPropertyDescription("List of channels used as source of camel routes. Can contain simple channel names or full Camel URIs.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private List<String> channelSources;
    @JsonProperty("config")
    @JsonPropertyDescription("Can be used to inject a Knative complete configuration in JSON format.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private String config;
    @JsonProperty("enabled")
    @JsonPropertyDescription("Can be used to enable or disable a trait. All traits share this common property.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Boolean enabled;
    @JsonProperty("endpointSinks")
    @JsonPropertyDescription("List of endpoints used as destination of camel routes. Can contain simple endpoint names or full Camel URIs.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private List<String> endpointSinks;
    @JsonProperty("endpointSources")
    @JsonPropertyDescription("List of channels used as source of camel routes.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private List<String> endpointSources;
    @JsonProperty("eventSinks")
    @JsonPropertyDescription("List of event types that the camel route will produce. Can contain simple event types or full Camel URIs (to use a specific broker).")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private List<String> eventSinks;
    @JsonProperty("eventSources")
    @JsonPropertyDescription("List of event types that the camel route will be subscribed to. Can contain simple event types or full Camel URIs (to use a specific broker different from \"default\").")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private List<String> eventSources;
    @JsonProperty("filterEventType")
    @JsonPropertyDescription("Enables the default filtering for the Knative trigger using the event type If this is true, the created Knative trigger uses the event type as a filter on the event stream when no other filter criteria is given. (default: true)")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Boolean filterEventType;
    @JsonProperty("filters")
    @JsonPropertyDescription("Sets filter attributes on the event stream (such as event type, source, subject and so on). A list of key-value pairs that represent filter attributes and its values. The syntax is KEY=VALUE, e.g., `source=\"my.source\"`. Filter attributes get set on the Knative trigger that is being created as part of this integration.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private List<String> filters;
    @JsonProperty("sinkBinding")
    @JsonPropertyDescription("Allows binding the camel route to a sink via a Knative SinkBinding resource. This can be used when the camel route targets a single sink. It's enabled by default when the integration targets a single sink (except when the integration is owned by a Knative source).")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Boolean sinkBinding;

    public Knative() {
    }

    public Boolean getAuto() {
        return this.auto;
    }

    public void setAuto(Boolean auto) {
        this.auto = auto;
    }

    public List<String> getChannelSinks() {
        return this.channelSinks;
    }

    public void setChannelSinks(List<String> channelSinks) {
        this.channelSinks = channelSinks;
    }

    public List<String> getChannelSources() {
        return this.channelSources;
    }

    public void setChannelSources(List<String> channelSources) {
        this.channelSources = channelSources;
    }

    public String getConfig() {
        return this.config;
    }

    public void setConfig(String config) {
        this.config = config;
    }

    public Boolean getEnabled() {
        return this.enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getEndpointSinks() {
        return this.endpointSinks;
    }

    public void setEndpointSinks(List<String> endpointSinks) {
        this.endpointSinks = endpointSinks;
    }

    public List<String> getEndpointSources() {
        return this.endpointSources;
    }

    public void setEndpointSources(List<String> endpointSources) {
        this.endpointSources = endpointSources;
    }

    public List<String> getEventSinks() {
        return this.eventSinks;
    }

    public void setEventSinks(List<String> eventSinks) {
        this.eventSinks = eventSinks;
    }

    public List<String> getEventSources() {
        return this.eventSources;
    }

    public void setEventSources(List<String> eventSources) {
        this.eventSources = eventSources;
    }

    public Boolean getFilterEventType() {
        return this.filterEventType;
    }

    public void setFilterEventType(Boolean filterEventType) {
        this.filterEventType = filterEventType;
    }

    public List<String> getFilters() {
        return this.filters;
    }

    public void setFilters(List<String> filters) {
        this.filters = filters;
    }

    public Boolean getSinkBinding() {
        return this.sinkBinding;
    }

    public void setSinkBinding(Boolean sinkBinding) {
        this.sinkBinding = sinkBinding;
    }
}
