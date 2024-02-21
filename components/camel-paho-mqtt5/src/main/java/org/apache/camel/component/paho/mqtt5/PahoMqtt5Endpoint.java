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
package org.apache.camel.component.paho.mqtt5;

import java.util.UUID;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttClientPersistence;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.client.persist.MqttDefaultFilePersistence;
import org.eclipse.paho.mqttv5.common.MqttMessage;

/**
 * Communicate with MQTT message brokers using Eclipse Paho MQTT v5 Client.
 */
@UriEndpoint(firstVersion = "3.8.0", scheme = "paho-mqtt5", title = "Paho MQTT 5",
             category = { Category.MESSAGING, Category.IOT },
             syntax = "paho-mqtt5:topic", headersClass = PahoMqtt5Constants.class)
public class PahoMqtt5Endpoint extends DefaultEndpoint {

    // Configuration members
    @UriPath(description = "Name of the topic")
    @Metadata(required = true)
    private final String topic;
    @UriParam
    private final PahoMqtt5Configuration configuration;
    @UriParam(label = "advanced")
    private volatile MqttClient client;

    public PahoMqtt5Endpoint(String uri, String topic, PahoMqtt5Component component, PahoMqtt5Configuration configuration) {
        super(uri, component);
        this.topic = topic;
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        PahoMqtt5Producer producer = new PahoMqtt5Producer(this);
        producer.setClient(client);
        return producer;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        PahoMqtt5Consumer consumer = new PahoMqtt5Consumer(this, processor);
        consumer.setClient(client);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public PahoMqtt5Component getComponent() {
        return (PahoMqtt5Component) super.getComponent();
    }

    public String getTopic() {
        return topic;
    }

    protected MqttConnectionOptions createMqttConnectionOptions() {
        PahoMqtt5Configuration config = getConfiguration();
        MqttConnectionOptions options = new MqttConnectionOptions();
        if (ObjectHelper.isNotEmpty(config.getUserName())) {
            options.setUserName(config.getUserName());
            if (ObjectHelper.isNotEmpty(config.getPassword())) {
                options.setPassword(config.getPassword().getBytes());
            }
        }
        options.setAutomaticReconnect(config.isAutomaticReconnect());
        options.setCleanStart(config.isCleanStart());
        options.setConnectionTimeout(config.getConnectionTimeout());
        options.setExecutorServiceTimeout(config.getExecutorServiceTimeout());
        if (ObjectHelper.isNotEmpty(config.getCustomWebSocketHeaders())) {
            options.setCustomWebSocketHeaders(config.getCustomWebSocketHeaders());
        }
        options.setHttpsHostnameVerificationEnabled(config.isHttpsHostnameVerificationEnabled());
        options.setKeepAliveInterval(config.getKeepAliveInterval());
        options.setReceiveMaximum(config.getReceiveMaximum());
        options.setMaxReconnectDelay(config.getMaxReconnectDelay());
        options.setSocketFactory(config.getSocketFactory());
        options.setSSLHostnameVerifier(config.getSslHostnameVerifier());
        options.setSSLProperties(config.getSslClientProps());
        if (config.getWillTopic() != null && config.getWillPayload() != null) {
            MqttMessage message = new MqttMessage(
                    config.getWillPayload().getBytes(),
                    config.getWillQos(),
                    config.isWillRetained(),
                    config.getWillMqttProperties());
            options.setWill(config.getWillTopic(), message);
        }
        if (config.getServerURIs() != null) {
            options.setServerURIs(config.getServerURIs().split(","));
        }
        if (config.getSessionExpiryInterval() >= 0) {
            options.setSessionExpiryInterval(config.getSessionExpiryInterval());
        }
        return options;
    }

    protected static String generateClientId() {
        return "camel-paho-" + UUID.randomUUID().toString();
    }

    protected static MqttClientPersistence createMqttClientPersistence(PahoMqtt5Configuration configuration) {
        if (configuration.getPersistence() == PahoMqtt5Persistence.MEMORY) {
            return new MemoryPersistence();
        } else {
            if (configuration.getFilePersistenceDirectory() != null) {
                return new MqttDefaultFilePersistence(configuration.getFilePersistenceDirectory());
            } else {
                return new MqttDefaultFilePersistence();
            }
        }
    }

    public PahoMqtt5Configuration getConfiguration() {
        return configuration;
    }

    public MqttClient getClient() {
        return client;
    }

    /**
     * To use an existing mqtt client
     */
    public void setClient(MqttClient client) {
        this.client = client;
    }
}
