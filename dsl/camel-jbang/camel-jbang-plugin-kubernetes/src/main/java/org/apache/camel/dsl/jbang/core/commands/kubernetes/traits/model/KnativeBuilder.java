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

public final class KnativeBuilder {
    private Boolean auto;
    private List<String> channelSinks;
    private List<String> channelSources;
    private String configuration;
    private Boolean enabled;
    private List<String> endpointSinks;
    private List<String> endpointSources;
    private List<String> eventSinks;
    private List<String> eventSources;
    private Boolean filterEventType;
    private List<String> filters;
    private Boolean sinkBinding;

    private KnativeBuilder() {
    }

    public static KnativeBuilder knative() {
        return new KnativeBuilder();
    }

    public KnativeBuilder withAuto(Boolean auto) {
        this.auto = auto;
        return this;
    }

    public KnativeBuilder withChannelSinks(List<String> channelSinks) {
        this.channelSinks = channelSinks;
        return this;
    }

    public KnativeBuilder withChannelSources(List<String> channelSources) {
        this.channelSources = channelSources;
        return this;
    }

    public KnativeBuilder withConfiguration(String configuration) {
        this.configuration = configuration;
        return this;
    }

    public KnativeBuilder withEnabled(Boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public KnativeBuilder withEndpointSinks(List<String> endpointSinks) {
        this.endpointSinks = endpointSinks;
        return this;
    }

    public KnativeBuilder withEndpointSources(List<String> endpointSources) {
        this.endpointSources = endpointSources;
        return this;
    }

    public KnativeBuilder withEventSinks(List<String> eventSinks) {
        this.eventSinks = eventSinks;
        return this;
    }

    public KnativeBuilder withEventSources(List<String> eventSources) {
        this.eventSources = eventSources;
        return this;
    }

    public KnativeBuilder withFilterEventType(Boolean filterEventType) {
        this.filterEventType = filterEventType;
        return this;
    }

    public KnativeBuilder withFilters(List<String> filters) {
        this.filters = filters;
        return this;
    }

    public KnativeBuilder withSinkBinding(Boolean sinkBinding) {
        this.sinkBinding = sinkBinding;
        return this;
    }

    public Knative build() {
        Knative knative = new Knative();
        knative.setAuto(auto);
        knative.setChannelSinks(channelSinks);
        knative.setChannelSources(channelSources);
        knative.setConfiguration(configuration);
        knative.setEnabled(enabled);
        knative.setEndpointSinks(endpointSinks);
        knative.setEndpointSources(endpointSources);
        knative.setEventSinks(eventSinks);
        knative.setEventSources(eventSources);
        knative.setFilterEventType(filterEventType);
        knative.setFilters(filters);
        knative.setSinkBinding(sinkBinding);
        return knative;
    }
}
