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
package org.apache.camel.component.paho;

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttClientPersistence;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;

/**
 * Component for communicating with MQTT message brokers using Eclipse Paho MQTT Client.
 */
@UriEndpoint(firstVersion = "2.16.0", scheme = "paho", title = "Paho", label = "messaging,iot", syntax = "paho:topic")
public class PahoEndpoint extends DefaultEndpoint {

    // Configuration members
    @UriPath(description = "Name of the topic")
    @Metadata(required = true)
    private final String topic;
    @UriParam
    private final PahoConfiguration configuration;
    @UriParam(label = "advanced")
    private volatile MqttClient client;

    public PahoEndpoint(String uri, String topic, PahoComponent component, PahoConfiguration configuration) {
        super(uri, component);
        this.topic = topic;
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        PahoProducer producer = new PahoProducer(this);
        producer.setClient(client);
        return producer;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        PahoConsumer consumer = new PahoConsumer(this, processor);
        consumer.setClient(client);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public PahoComponent getComponent() {
        return (PahoComponent) super.getComponent();
    }

    public String getTopic() {
        return topic;
    }

    public Exchange createExchange(MqttMessage mqttMessage, String topic) {
        Exchange exchange = createExchange();

        PahoMessage paho = new PahoMessage(exchange.getContext(), mqttMessage);
        paho.setBody(mqttMessage.getPayload());
        paho.setHeader(PahoConstants.MQTT_TOPIC, topic);
        paho.setHeader(PahoConstants.MQTT_QOS, mqttMessage.getQos());

        exchange.setIn(paho);
        return exchange;
    }

    protected static MqttConnectOptions createMqttConnectOptions(PahoConfiguration config) {
        MqttConnectOptions mq = new MqttConnectOptions();
        if (ObjectHelper.isNotEmpty(config.getUserName()) && ObjectHelper.isNotEmpty(config.getPassword())) {
            mq.setUserName(config.getUserName());
            mq.setPassword(config.getPassword().toCharArray());
        }
        mq.setAutomaticReconnect(config.isAutomaticReconnect());
        mq.setCleanSession(config.isCleanSession());
        mq.setConnectionTimeout(config.getConnectionTimeout());
        mq.setExecutorServiceTimeout(config.getExecutorServiceTimeout());
        mq.setCustomWebSocketHeaders(config.getCustomWebSocketHeaders());
        mq.setHttpsHostnameVerificationEnabled(config.isHttpsHostnameVerificationEnabled());
        mq.setKeepAliveInterval(config.getKeepAliveInterval());
        mq.setMaxInflight(config.getMaxInflight());
        mq.setMaxReconnectDelay(config.getMaxReconnectDelay());
        mq.setMqttVersion(config.getMqttVersion());
        mq.setSocketFactory(config.getSocketFactory());
        mq.setSSLHostnameVerifier(config.getSslHostnameVerifier());
        mq.setSSLProperties(config.getSslClientProps());
        if (config.getWillTopic() != null && config.getWillPayload() != null) {
            mq.setWill(config.getWillTopic(),
                    config.getWillPayload().getBytes(),
                    config.getWillQos(),
                    config.isWillRetained());
        }
        if (config.getServerURIs() != null) {
            mq.setServerURIs(config.getServerURIs().split(","));
        }
        return mq;
    }

    protected static MqttClientPersistence createMqttClientPersistence(PahoConfiguration configuration) {
        if (configuration.getPersistence() == PahoPersistence.MEMORY) {
            return new MemoryPersistence();
        } else {
            if (configuration.getFilePersistenceDirectory() != null) {
                return new MqttDefaultFilePersistence(configuration.getFilePersistenceDirectory());
            } else {
                return new MqttDefaultFilePersistence();
            }
        }
    }

    public PahoConfiguration getConfiguration() {
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
