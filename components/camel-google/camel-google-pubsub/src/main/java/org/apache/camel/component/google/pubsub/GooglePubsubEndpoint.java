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
package org.apache.camel.component.google.pubsub;

import java.util.concurrent.ExecutorService;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.google.pubsub.serializer.DefaultGooglePubsubSerializer;
import org.apache.camel.component.google.pubsub.serializer.GooglePubsubSerializer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Send and receive messages to/from Google Cloud Platform PubSub Service.
 * <p/>
 * Built on top of the Google Cloud Pub/Sub libraries.
 */
@UriEndpoint(firstVersion = "2.19.0", scheme = "google-pubsub", title = "Google Pubsub",
             syntax = "google-pubsub:projectId:destinationName", category = { Category.CLOUD, Category.MESSAGING },
             headersClass = GooglePubsubConstants.class)
public class GooglePubsubEndpoint extends DefaultEndpoint {

    private Logger log;

    @UriPath(label = "common", description = "The Google Cloud PubSub Project Id")
    @Metadata(required = true)
    private String projectId;

    @UriPath(label = "common",
             description = "The Destination Name. For the consumer this will be the subscription name, while for the producer this will be the topic name.")
    @Metadata(required = true)
    private String destinationName;

    @UriParam(label = "common", name = "authenticate",
              description = "Use Credentials when interacting with PubSub service (no authentication is required when using emulator).",
              defaultValue = "true")
    private boolean authenticate = true;

    @UriParam(label = "common",
              description = "The Service account key that can be used as credentials for the PubSub publisher/subscriber. It can be loaded by default from "
                            + " classpath, but you can prefix with classpath:, file:, or http: to load the resource from different systems.")
    private String serviceAccountKey;

    @UriParam(name = "loggerId", description = "Logger ID to use when a match to the parent route required")
    private String loggerId;

    @UriParam(label = "consumer", name = "concurrentConsumers",
              description = "The number of parallel streams consuming from the subscription",
              defaultValue = "1")
    private Integer concurrentConsumers = 1;

    @UriParam(label = "consumer", name = "maxMessagesPerPoll",
              description = "The max number of messages to receive from the server in a single API call", defaultValue = "1")
    private Integer maxMessagesPerPoll = 1;

    @UriParam(label = "consumer", name = "synchronousPull", description = "Synchronously pull batches of messages",
              defaultValue = "false")
    private boolean synchronousPull;

    @UriParam(label = "consumer", defaultValue = "AUTO", enums = "AUTO,NONE",
              description = "AUTO = exchange gets ack'ed/nack'ed on completion. NONE = downstream process has to ack/nack explicitly")
    private GooglePubsubConstants.AckMode ackMode = GooglePubsubConstants.AckMode.AUTO;

    @UriParam(label = "consumer", name = "maxAckExtensionPeriod",
              description = "Set the maximum period a message ack deadline will be extended. Value in seconds",
              defaultValue = "3600")
    private int maxAckExtensionPeriod = 3600;

    @UriParam(defaultValue = "false",
              description = "Should message ordering be enabled",
              label = "producer,advanced")
    private boolean messageOrderingEnabled;

    @UriParam(description = "Pub/Sub endpoint to use. Required when using message ordering, and ensures that messages are received in order even when multiple publishers are used",
              label = "producer,advanced")
    private String pubsubEndpoint;

    @UriParam(name = "serializer",
              description = "A custom GooglePubsubSerializer to use for serializing message payloads in the producer",
              label = "producer,advanced")
    @Metadata(autowired = true)
    private GooglePubsubSerializer serializer;

    public GooglePubsubEndpoint(String uri, Component component) {
        super(uri, component);

        if (!(component instanceof GooglePubsubComponent)) {
            throw new IllegalArgumentException(
                    "The component provided is not GooglePubsubComponent : " + component.getClass().getName());
        }
    }

    @Override
    public GooglePubsubComponent getComponent() {
        return (GooglePubsubComponent) super.getComponent();
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
        return new GooglePubsubProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        afterPropertiesSet();
        setExchangePattern(ExchangePattern.InOnly);
        GooglePubsubConsumer consumer = new GooglePubsubConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    public ExecutorService createExecutor() {
        return getCamelContext().getExecutorServiceManager().newFixedThreadPool(this,
                "GooglePubsubConsumer[" + getDestinationName() + "]", concurrentConsumers);
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getLoggerId() {
        return loggerId;
    }

    public void setLoggerId(String loggerId) {
        this.loggerId = loggerId;
    }

    public boolean isAuthenticate() {
        return authenticate;
    }

    public void setAuthenticate(boolean authenticate) {
        this.authenticate = authenticate;
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

    public boolean isSynchronousPull() {
        return synchronousPull;
    }

    public void setSynchronousPull(Boolean synchronousPull) {
        this.synchronousPull = synchronousPull;
    }

    public GooglePubsubConstants.AckMode getAckMode() {
        return ackMode;
    }

    public void setAckMode(GooglePubsubConstants.AckMode ackMode) {
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

    public boolean isMessageOrderingEnabled() {
        return this.messageOrderingEnabled;
    }

    public void setMessageOrderingEnabled(boolean messageOrderingEnabled) {
        this.messageOrderingEnabled = messageOrderingEnabled;
    }

    public String getPubsubEndpoint() {
        return this.pubsubEndpoint;
    }

    public void setPubsubEndpoint(String pubsubEndpoint) {
        this.pubsubEndpoint = pubsubEndpoint;
    }
}
