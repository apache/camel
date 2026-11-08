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
package org.apache.camel.component.vertx.mqtt;

import io.vertx.core.buffer.Buffer;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.MqttClientWillOptions;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.vertx.common.VertxHelper;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Send and receive messages to and from an MQTT broker, MQTT 3.1.1 and 5, with the Vert.x MQTT client.
 */
@UriEndpoint(firstVersion = "4.24.0", scheme = VertxMqttConstants.SCHEME, title = "Vert.x MQTT",
             syntax = "vertx-mqtt:topic", category = { Category.MESSAGING, Category.IOT },
             headersClass = VertxMqttConstants.class)
public class VertxMqttEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    /**
     * The topic to publish on, or the topic filter to subscribe to. A filter may hold the wildcards + and #, a topic to
     * publish on may not.
     */
    @UriPath
    @Metadata(required = true)
    private String topic;

    @UriParam
    private VertxMqttConfiguration configuration;

    private BrokerAddress broker;
    private VertxMqttConnection connection;
    private int connectionUsers;

    public VertxMqttEndpoint(String uri, VertxMqttComponent component, VertxMqttConfiguration configuration,
                             String topic) {
        super(uri, component);
        this.configuration = configuration;
        this.topic = topic;
    }

    @Override
    public VertxMqttComponent getComponent() {
        return (VertxMqttComponent) super.getComponent();
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        if (topic == null || topic.isEmpty()) {
            throw new IllegalArgumentException("The topic is required");
        }
        broker = BrokerAddress.parse(configuration.getBrokerUrl());
        checkQos("qos", configuration.getQos());
        checkQos("willQos", configuration.getWillQos());
    }

    private static void checkQos(String option, int qos) {
        if (qos < 0 || qos > 2) {
            throw new IllegalArgumentException("The option %s must be 0, 1 or 2, not %s".formatted(option, qos));
        }
    }

    @Override
    public Producer createProducer() throws Exception {
        return new VertxMqttProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        final VertxMqttConsumer consumer = new VertxMqttConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    /**
     * The connection of this endpoint, shared by its consumer and producer: started by the first of them, stopped with
     * the last, so that an endpoint nobody uses does not connect.
     *
     * @return the started connection
     */
    synchronized VertxMqttConnection acquireConnection() throws Exception {
        if (connection == null) {
            final VertxMqttConnection created = new VertxMqttConnection(
                    getComponent().getVertx(), broker,
                    createClientOptions(), configuration);
            created.start();
            connection = created;
        }
        connectionUsers++;
        return connection;
    }

    /**
     * Gives back the connection taken with {@link #acquireConnection()}, stopping it with its last user.
     */
    synchronized void releaseConnection() {
        if (connectionUsers > 0 && --connectionUsers == 0 && connection != null) {
            connection.stop();
            connection = null;
        }
    }

    @Override
    protected synchronized void doStop() throws Exception {
        if (connection != null) {
            connection.stop();
            connection = null;
            connectionUsers = 0;
        }
        super.doStop();
    }

    /**
     * @return the options of the Vert.x client for this endpoint
     */
    MqttClientOptions createClientOptions() throws Exception {
        final MqttClientOptions options = new MqttClientOptions();
        final String clientId = configuration.getClientId();
        options.setAutoGeneratedClientId(clientId == null || clientId.isEmpty());
        if (clientId != null && !clientId.isEmpty()) {
            options.setClientId(clientId);
        }
        options.setVersion(configuration.getMqttVersion().protocolLevel());
        options.setCleanSession(configuration.isCleanStart());
        options.setKeepAliveInterval(configuration.getKeepAliveInterval());
        options.setConnectTimeout((int) Math.min(Integer.MAX_VALUE, configuration.getConnectTimeout()));
        options.setMaxMessageSize(configuration.getMaxMessageSize());
        options.setMaxInflightQueue(configuration.getMaxInflightQueue());
        options.setAckTimeout(configuration.getAckTimeout() > 0 ? configuration.getAckTimeout() : -1);
        options.setAutoAck(!configuration.isManualAcksEnabled());
        if (configuration.getMqttVersion() == VertxMqttVersion.MQTT_5_0) {
            if (configuration.getSessionExpiryInterval() > 0) {
                options.setSessionExpireInterval(configuration.getSessionExpiryInterval());
            }
            if (configuration.getReceiveMaximum() > 0) {
                options.setReceiveMaximum(configuration.getReceiveMaximum());
            }
        }
        options.setUsername(configuration.getUsername());
        options.setPassword(configuration.getPassword());
        if (configuration.getWillTopic() != null && !configuration.getWillTopic().isEmpty()) {
            final MqttClientWillOptions will = new MqttClientWillOptions().setTopic(configuration.getWillTopic())
                    .setQos(configuration.getWillQos()).setRetain(configuration.isWillRetained());
            if (configuration.getWillPayload() != null) {
                will.setMessageBytes(Buffer.buffer(configuration.getWillPayload()));
            }
            options.setWillOptions(will);
        }
        if (broker.ssl()) {
            options.setSsl(true);
            if (configuration.getSslContextParameters() != null) {
                VertxHelper.setupSSLOptions(getCamelContext(), configuration.getSslContextParameters(), options);
            }
            // Never switched off: without it any certificate of a trusted issuer passes for any host
            options.setHostnameVerificationAlgorithm("HTTPS");
            options.setTrustAll(false);
        }
        return options;
    }

    BrokerAddress getBroker() {
        return broker;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public VertxMqttConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(VertxMqttConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public String getServiceUrl() {
        return configuration.getBrokerUrl();
    }

    @Override
    public String getServiceProtocol() {
        return "mqtt";
    }
}
