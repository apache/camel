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

package org.apache.camel.component.google.pubsublite;

import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.apache.camel.*;
import org.apache.camel.component.google.pubsublite.serializer.DefaultGooglePubsubSerializer;
import org.apache.camel.component.google.pubsublite.serializer.GooglePubsubSerializer;
import org.apache.camel.spi.*;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Send and receive messages to/from Google Cloud Platform PubSub Lite Service.
 * <p/>
 * Built on top of the Google Cloud Pub/Sub Lite libraries.
 */
@UriEndpoint(
        firstVersion = "4.6.0",
        scheme = "google-pubsub-lite",
        title = "Google PubSub Lite",
        syntax = "google-pubsub-lite:projectId:location:destinationName",
        category = {Category.CLOUD, Category.MESSAGING},
        headersClass = GooglePubsubLiteConstants.class)
public class GooglePubsubLiteEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    private Logger log;

    @UriPath(label = "common", description = "The Google Cloud PubSub Lite Project Id")
    @Metadata(required = true)
    private Long projectId;

    @UriPath(label = "common", description = "The Google Cloud PubSub Lite location")
    @Metadata(required = true)
    private String location;

    @UriPath(
            label = "common",
            description =
                    "The Destination Name. For the consumer this will be the subscription name, while for the producer this will be the topic name.")
    @Metadata(required = true)
    private String destinationName;

    @UriParam(
            label = "security",
            description =
                    "The Service account key that can be used as credentials for the PubSub publisher/subscriber. It can be loaded by default from "
                            + " classpath, but you can prefix with classpath:, file:, or http: to load the resource from different systems.")
    private String serviceAccountKey;

    @UriParam(name = "loggerId", description = "Logger ID to use when a match to the parent route required")
    private String loggerId;

    @UriParam(
            label = "consumer",
            description = "The number of parallel streams consuming from the subscription",
            defaultValue = "1")
    private Integer concurrentConsumers = 1;

    @UriParam(
            label = "consumer",
            description = "The max number of messages to receive from the server in a single API call",
            defaultValue = "1")
    private Integer maxMessagesPerPoll = 1;

    @UriParam(
            label = "consumer",
            defaultValue = "AUTO",
            enums = "AUTO,NONE",
            description =
                    "AUTO = exchange gets ack'ed/nack'ed on completion. NONE = downstream process has to ack/nack explicitly")
    private GooglePubsubLiteConstants.AckMode ackMode = GooglePubsubLiteConstants.AckMode.AUTO;

    @UriParam(
            label = "consumer",
            name = "maxAckExtensionPeriod",
            description = "Set the maximum period a message ack deadline will be extended. Value in seconds",
            defaultValue = "3600")
    private int maxAckExtensionPeriod = 3600;

    @UriParam(
            description =
                    "Pub/Sub endpoint to use. Required when using message ordering, and ensures that messages are received in order even when multiple publishers are used",
            label = "producer,advanced")
    private String pubsubEndpoint;

    @UriParam(
            name = "serializer",
            description = "A custom GooglePubsubLiteSerializer to use for serializing message payloads in the producer",
            label = "producer,advanced")
    @Metadata(autowired = true)
    private GooglePubsubSerializer serializer;

    public GooglePubsubLiteEndpoint(String uri, Component component) {
        super(uri, component);
    }

    @Override
    public GooglePubsubLiteComponent getComponent() {
        return (GooglePubsubLiteComponent) super.getComponent();
    }

    public void afterPropertiesSet() {
        if (ObjectHelper.isEmpty(loggerId)) {
            log = LoggerFactory.getLogger(this.getClass().getName());
        } else {
            log = LoggerFactory.getLogger(loggerId);
        }

        // Default pubsub connection.
        // With the publisher endpoints - the main publisher
        // with the consumer endpoints - the ack client

        log.trace("Project ID: {}", this.projectId);
        log.trace("Destination Name: {}", this.destinationName);
    }

    @Override
    public Producer createProducer() throws Exception {
        afterPropertiesSet();
        if (ObjectHelper.isEmpty(serializer)) {
            serializer = new DefaultGooglePubsubSerializer();
        }
        return new GooglePubsubLiteProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        afterPropertiesSet();
        setExchangePattern(ExchangePattern.InOnly);
        GooglePubsubLiteConsumer consumer = new GooglePubsubLiteConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    public ExecutorService createExecutor(Object source) {
        return getCamelContext()
                .getExecutorServiceManager()
                .newFixedThreadPool(
                        source, "GooglePubsubLiteConsumer[" + getDestinationName() + "]", concurrentConsumers);
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public void setProjectId(String projectId) {
        setProjectId(Long.parseLong(projectId));
    }

    public String getLoggerId() {
        return loggerId;
    }

    public void setLoggerId(String loggerId) {
        this.loggerId = loggerId;
    }

    public String getServiceAccountKey() {
        return serviceAccountKey;
    }

    public void setServiceAccountKey(String serviceAccountKey) {
        this.serviceAccountKey = serviceAccountKey;
    }

    public String getDestinationName() {
        return destinationName;
    }

    public void setDestinationName(String destinationName) {
        this.destinationName = destinationName;
    }

    public Integer getConcurrentConsumers() {
        return concurrentConsumers;
    }

    public void setConcurrentConsumers(Integer concurrentConsumers) {
        this.concurrentConsumers = concurrentConsumers;
    }

    public Integer getMaxMessagesPerPoll() {
        return maxMessagesPerPoll;
    }

    public void setMaxMessagesPerPoll(Integer maxMessagesPerPoll) {
        this.maxMessagesPerPoll = maxMessagesPerPoll;
    }

    public GooglePubsubLiteConstants.AckMode getAckMode() {
        return ackMode;
    }

    public void setAckMode(GooglePubsubLiteConstants.AckMode ackMode) {
        this.ackMode = ackMode;
    }

    public int getMaxAckExtensionPeriod() {
        return maxAckExtensionPeriod;
    }

    public void setMaxAckExtensionPeriod(int maxAckExtensionPeriod) {
        this.maxAckExtensionPeriod = maxAckExtensionPeriod;
    }

    public GooglePubsubSerializer getSerializer() {
        return serializer;
    }

    public void setSerializer(GooglePubsubSerializer serializer) {
        this.serializer = serializer;
    }

    public String getPubsubEndpoint() {
        return this.pubsubEndpoint;
    }

    public void setPubsubEndpoint(String pubsubEndpoint) {
        this.pubsubEndpoint = pubsubEndpoint;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    @Override
    public String getServiceUrl() {
        if (ObjectHelper.isNotEmpty(getPubsubEndpoint())) {
            return getServiceProtocol() + ":" + getPubsubEndpoint();
        }
        return null;
    }

    @Override
    public String getServiceProtocol() {
        return "pubsub-lite";
    }

    @Override
    public Map<String, String> getServiceMetadata() {
        if (getDestinationName() != null) {
            return Map.of("destinationName", getDestinationName());
        }
        return null;
    }
}
