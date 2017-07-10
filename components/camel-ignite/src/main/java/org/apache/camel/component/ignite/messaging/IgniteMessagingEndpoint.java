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
package org.apache.camel.component.ignite.messaging;

import java.net.URI;
import java.util.Map;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.ignite.AbstractIgniteEndpoint;
import org.apache.camel.component.ignite.ClusterGroupExpression;
import org.apache.camel.component.ignite.IgniteComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteMessaging;

/**
 * The Ignite Messaging endpoint is one of camel-ignite endpoints which allows you to send and
 * consume messages from an <a href="https://apacheignite.readme.io/docs/messaging">Ignite topic</a>.
 * This endpoint supports producers (to send messages) and consumers (to receive messages).
 */
@UriEndpoint(firstVersion = "2.17.0", scheme = "ignite-messaging", title = "Ignite Messaging", syntax = "ignite-messaging:topic", label = "nosql,cache,messaging",
    consumerClass = IgniteMessagingConsumer.class)
public class IgniteMessagingEndpoint extends AbstractIgniteEndpoint {

    @UriPath
    @Metadata(required = "true")
    private String topic;

    @UriParam(label = "consumer,producer")
    private ClusterGroupExpression clusterGroupExpression;

    @UriParam(label = "producer", defaultValue = "UNORDERED")
    private IgniteMessagingSendMode sendMode = IgniteMessagingSendMode.UNORDERED;

    @UriParam(label = "producer")
    private Long timeout;

    @Deprecated
    public IgniteMessagingEndpoint(String endpointUri, URI remainingUri, Map<String, Object> parameters, IgniteComponent igniteComponent) {
        super(endpointUri, igniteComponent);
        topic = remainingUri.getHost();
    }

    public IgniteMessagingEndpoint(String endpointUri, String remaining, Map<String, Object> parameters, IgniteMessagingComponent igniteComponent) {
        super(endpointUri, igniteComponent);
        topic = remaining;
    }

    @Override
    public Producer createProducer() throws Exception {
        // Validate options.
        if (topic == null) {
            throw new IllegalStateException("Cannot initialize an Ignite Messaging Producer with a null topic.");
        }

        if (sendMode == IgniteMessagingSendMode.ORDERED && timeout == null) {
            throw new IllegalStateException("Cannot initialize an Ignite Messaging Producer in ORDERED send mode without a timeout.");
        }

        // Initialize the Producer.
        IgniteMessaging messaging = createIgniteMessaging();
        return new IgniteMessagingProducer(this, igniteComponent().getIgnite(), messaging);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        // Validate options.
        if (topic == null) {
            throw new IllegalStateException("Cannot initialize an Ignite Messaging Consumer with a null topic.");
        }

        // Initialize the Consumer.
        IgniteMessaging messaging = createIgniteMessaging();
        IgniteMessagingConsumer consumer = new IgniteMessagingConsumer(this, processor, messaging);
        configureConsumer(consumer);
        return consumer;
    }

    private IgniteMessaging createIgniteMessaging() {
        Ignite ignite = ignite();
        IgniteMessaging messaging = clusterGroupExpression == null ? ignite.message() : ignite.message(clusterGroupExpression.getClusterGroup(ignite));
        return messaging;
    }

    /**
     * Gets the topic name.
     * 
     * @return topic name
     */
    public String getTopic() {
        return topic;
    }

    /**
     * The topic name.
     * 
     * @param topic topic name
     */
    public void setTopic(String topic) {
        this.topic = topic;
    }

    /**
     * Gets the cluster group expression.
     * 
     * @return cluster group expression
     */
    public ClusterGroupExpression getClusterGroupExpression() {
        return clusterGroupExpression;
    }

    /**
     * The cluster group expression.
     * 
     * @param clusterGroupExpression cluster group expression
     */
    public void setClusterGroupExpression(ClusterGroupExpression clusterGroupExpression) {
        this.clusterGroupExpression = clusterGroupExpression;
    }

    /**
     * Gets the timeout.
     * 
     * @return timeout
     */
    public Long getTimeout() {
        return timeout;
    }

    /**
     * The timeout for the send operation when using ordered messages.
     * 
     * @param timeout timeout
     */
    public void setTimeout(Long timeout) {
        this.timeout = timeout;
    }

    /**
     * Gets the send mode.
     * 
     * @return send mode
     */
    public IgniteMessagingSendMode getSendMode() {
        return sendMode;
    }

    /**
     * The send mode to use.
     * Possible values: UNORDERED, ORDERED.
     * 
     * @param sendMode send mode
     */
    public void setSendMode(IgniteMessagingSendMode sendMode) {
        this.sendMode = sendMode;
    }

}
