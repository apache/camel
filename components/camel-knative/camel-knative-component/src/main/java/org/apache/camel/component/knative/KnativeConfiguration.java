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
package org.apache.camel.component.knative;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.cloudevents.CloudEvents;
import org.apache.camel.component.knative.spi.KnativeEnvironment;
import org.apache.camel.component.knative.spi.KnativeSinkBinding;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class KnativeConfiguration implements Cloneable {
    @UriParam
    private KnativeEnvironment environment;
    @UriParam
    private KnativeSinkBinding sinkBinding;
    @UriParam
    private String typeId;
    @UriParam(defaultValue = "1.0", enums = "1.0,1.0.1,1.0.2")
    private String cloudEventsSpecVersion = CloudEvents.v1_0.version();
    @UriParam(defaultValue = "org.apache.camel.event")
    private String cloudEventsType = "org.apache.camel.event";
    @UriParam(prefix = "transport.")
    private Map<String, Object> transportOptions;
    @UriParam(prefix = "filter.")
    private Map<String, String> filters;
    @UriParam(prefix = "ce.override.")
    private Map<String, String> ceOverride;
    @UriParam(label = "advanced")
    private String apiVersion;
    @UriParam(label = "advanced")
    private String kind;
    @UriParam(label = "advanced")
    private String name;
    @UriParam(label = "consumer", defaultValue = "false")
    private boolean replyWithCloudEvent;
    @UriParam(label = "consumer,advanced", defaultValue = "true")
    private Boolean reply;

    // ************************
    //
    // Properties
    //
    // ************************

    public KnativeEnvironment getEnvironment() {
        return environment;
    }

    /**
     * The environment
     */
    public void setEnvironment(KnativeEnvironment environment) {
        this.environment = environment;
    }

    public KnativeSinkBinding getSinkBinding() {
        return sinkBinding;
    }

    /**
     * The SinkBinding configuration.
     */
    public void setSinkBinding(KnativeSinkBinding sinkBinding) {
        this.sinkBinding = sinkBinding;
    }

    public String getTypeId() {
        return typeId;
    }

    /**
     * The name of the service to lookup from the {@link KnativeEnvironment}.
     */
    public void setTypeId(String typeId) {
        this.typeId = typeId;
    }

    public boolean isReplyWithCloudEvent() {
        return replyWithCloudEvent;
    }

    /**
     * Transforms the reply into a cloud event that will be processed by the caller.
     *
     * When listening to events from a Knative Broker, if this flag is enabled, replies will be published to the same
     * Broker where the request comes from (beware that if you don't change the "type" of the received message, you may
     * create a loop and receive your same reply).
     *
     * When this flag is disabled, CloudEvent headers are removed from the reply.
     */
    public void setReplyWithCloudEvent(boolean replyWithCloudEvent) {
        this.replyWithCloudEvent = replyWithCloudEvent;
    }

    public String getCloudEventsSpecVersion() {
        return cloudEventsSpecVersion;
    }

    /**
     * Set the version of the cloudevents spec.
     */
    public void setCloudEventsSpecVersion(String cloudEventsSpecVersion) {
        this.cloudEventsSpecVersion = cloudEventsSpecVersion;
    }

    public String getCloudEventsType() {
        return cloudEventsType;
    }

    /**
     * Set the event-type information of the produced events.
     */
    public void setCloudEventsType(String cloudEventsType) {
        this.cloudEventsType = cloudEventsType;
    }

    public Map<String, Object> getTransportOptions() {
        return transportOptions;
    }

    /**
     * Set the transport options.
     */
    public void setTransportOptions(Map<String, Object> transportOptions) {
        this.transportOptions = new HashMap<>(transportOptions);
    }

    /**
     * Add a transport option.
     */
    public void addTransportOptions(String key, Object value) {
        if (this.transportOptions == null) {
            this.transportOptions = new HashMap<>();
        }

        this.transportOptions.put(key, value);
    }

    public Map<String, String> getFilters() {
        return filters;
    }

    /**
     * Set the filters.
     */
    public void setFilters(Map<String, String> filters) {
        this.filters = new HashMap<>(filters);
    }

    public Map<String, String> getCeOverride() {
        return ceOverride;
    }

    /**
     * CloudEvent headers to override
     */
    public void setCeOverride(Map<String, String> ceOverride) {
        this.ceOverride = new HashMap<>(ceOverride);
    }

    public String getApiVersion() {
        return apiVersion;
    }

    /**
     * The version of the k8s resource referenced by the endpoint.
     */
    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getKind() {
        return kind;
    }

    /**
     * The type of the k8s resource referenced by the endpoint.
     */
    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getName() {
        return name;
    }

    /**
     * The name of the k8s resource referenced by the endpoint.
     */
    public void setName(String name) {
        this.name = name;
    }

    public Boolean getReply() {
        return reply;
    }

    /**
     * If the consumer should construct a full reply to knative request.
     */
    public void setReply(Boolean reply) {
        this.reply = reply;
    }

    // ************************
    //
    // Cloneable
    //
    // ************************

    public KnativeConfiguration copy() {
        try {
            return (KnativeConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
