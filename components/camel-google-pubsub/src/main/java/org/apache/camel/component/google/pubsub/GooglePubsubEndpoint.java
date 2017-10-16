/**
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

import com.google.api.client.repackaged.com.google.common.base.Strings;
import com.google.api.services.pubsub.Pubsub;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Messaging client for Google Cloud Platform PubSub Service
 * <p/>
 * Built on top of the Service API libraries (v1).
 */
@UriEndpoint(firstVersion = "2.19.0", scheme = "google-pubsub", title = "Google Pubsub",
        syntax = "google-pubsub:projectId:destinationName", label = "messaging")
public class GooglePubsubEndpoint extends DefaultEndpoint {

    private Logger log;

    @UriPath(description = "Project Id")
    @Metadata(required = "true")
    private String projectId;

    @UriPath(description = "Destination Name")
    @Metadata(required = "true")
    private String destinationName;

    @UriParam(name = "loggerId", description = "Logger ID to use when a match to the parent route required")
    private String loggerId;

    @UriParam(name = "concurrentConsumers", description = "The number of parallel streams consuming from the subscription", defaultValue = "1")
    private Integer concurrentConsumers = 1;

    @UriParam(name = "maxMessagesPerPoll", description = "The max number of messages to receive from the server in a single API call", defaultValue = "1")
    private Integer maxMessagesPerPoll = 1;

    @UriParam(name = "connectionFactory", description = "ConnectionFactory to obtain connection to PubSub Service. If non provided the default one will be used")
    private GooglePubsubConnectionFactory connectionFactory;

    @UriParam(defaultValue = "AUTO", enums = "AUTO,NONE",
            description = "AUTO = exchange gets ack'ed/nack'ed on completion. NONE = downstream process has to ack/nack explicitly")
    private GooglePubsubConstants.AckMode ackMode = GooglePubsubConstants.AckMode.AUTO;

    private Pubsub pubsub;

    public GooglePubsubEndpoint(String uri, Component component, String remaining) {
        super(uri, component);

        if (!(component instanceof GooglePubsubComponent)) {
            throw new IllegalArgumentException("The component provided is not GooglePubsubComponent : " + component.getClass().getName());
        }
    }

    @Override
    public GooglePubsubComponent getComponent() {
        return (GooglePubsubComponent) super.getComponent();
    }

    public void afterPropertiesSet() throws Exception {
        if (Strings.isNullOrEmpty(loggerId)) {
            log = LoggerFactory.getLogger(this.getClass().getName());
        } else {
            log = LoggerFactory.getLogger(loggerId);
        }

        // Default pubsub connection.
        // With the publisher endpoints - the main publisher
        // with the consumer endpoints  - the ack client
        pubsub = getConnectionFactory().getDefaultClient();

        log.trace("Credential file location : {}", getConnectionFactory().getCredentialsFileLocation());
        log.trace("Project ID: {}", this.projectId);
        log.trace("Destination Name: {}", this.destinationName);
    }

    public Producer createProducer() throws Exception {
        afterPropertiesSet();
        return new GooglePubsubProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        afterPropertiesSet();
        setExchangePattern(ExchangePattern.InOnly);
        return new GooglePubsubConsumer(this, processor);
    }

    public ExecutorService createExecutor() {
        return getCamelContext()
                .getExecutorServiceManager()
                .newFixedThreadPool(this,
                                    "GooglePubsubConsumer[" + getDestinationName() + "]",
                                    concurrentConsumers);
    }

    public boolean isSingleton() {
        return false;
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

    public GooglePubsubConstants.AckMode getAckMode() {
        return ackMode;
    }

    public void setAckMode(GooglePubsubConstants.AckMode ackMode) {
        this.ackMode = ackMode;
    }

    public Pubsub getPubsub() {
        return pubsub;
    }

    /**
     * ConnectionFactory to obtain connection to PubSub Service. If non provided the default will be used.
     */
    public GooglePubsubConnectionFactory getConnectionFactory() {
        return (null == connectionFactory)
                ? getComponent().getConnectionFactory()
                : connectionFactory;
    }

    public void setConnectionFactory(GooglePubsubConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }
}
