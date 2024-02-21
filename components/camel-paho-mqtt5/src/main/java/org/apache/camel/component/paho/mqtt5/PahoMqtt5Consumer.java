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

import org.apache.camel.AsyncCallback;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PahoMqtt5Consumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(PahoMqtt5Consumer.class);

    private volatile MqttClient client;
    private volatile String clientId;
    private volatile boolean stopClient;
    private volatile MqttConnectionOptions connectionOptions;

    public PahoMqtt5Consumer(Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    public MqttClient getClient() {
        return client;
    }

    public void setClient(MqttClient client) {
        this.client = client;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        connectionOptions = getEndpoint().createMqttConnectionOptions();

        if (client == null) {
            clientId = getEndpoint().getConfiguration().getClientId();
            if (clientId == null) {
                clientId = PahoMqtt5Endpoint.generateClientId();
            }
            stopClient = true;
            client = new MqttClient(
                    getEndpoint().getConfiguration().getBrokerUrl(),
                    clientId,
                    PahoMqtt5Endpoint.createMqttClientPersistence(getEndpoint().getConfiguration()));
            LOG.debug("Connecting client: {} to broker: {}", clientId, getEndpoint().getConfiguration().getBrokerUrl());
            client.connect(connectionOptions);
        }

        client.setCallback(new MqttCallback() {

            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                if (reconnect) {
                    try {
                        client.subscribe(getEndpoint().getTopic(), getEndpoint().getConfiguration().getQos());
                    } catch (MqttException e) {
                        LOG.error("MQTT resubscribe failed {}", e.getMessage(), e);
                    }
                }
            }

            @Override
            public void authPacketArrived(int reasonCode, MqttProperties properties) {
                LOG.debug("Auth packet arrived {} {}", reasonCode, properties);
            }

            @Override
            public void disconnected(MqttDisconnectResponse response) {
                LOG.debug("MQTT broker disconnected due {}", response.getReasonString(), response.getException());
            }

            @Override
            public void mqttErrorOccurred(MqttException exception) {
                LOG.debug("Error occurred {}", exception.getMessage(), exception);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                LOG.debug("Message arrived on topic: {} -> {}", topic, message);
                Exchange exchange = createExchange(message, topic);

                // use default consumer callback
                AsyncCallback cb = defaultConsumerCallback(exchange, true);
                getAsyncProcessor().process(exchange, cb);
            }

            @Override
            public void deliveryComplete(IMqttToken token) {
                LOG.debug("Delivery complete. Token: {}", token);
            }
        });

        LOG.debug("Subscribing client: {} to topic: {}", clientId, getEndpoint().getTopic());
        client.subscribe(getEndpoint().getTopic(), getEndpoint().getConfiguration().getQos());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (stopClient && client != null && client.isConnected()) {
            String topic = getEndpoint().getTopic();
            // only unsubscribe if we are not durable
            if (getEndpoint().getConfiguration().isCleanStart()) {
                LOG.debug("Unsubscribing client: {} from topic: {}", clientId, topic);
                client.unsubscribe(topic);
            } else {
                LOG.debug("Client: {} is durable so will not unsubscribe from topic: {}", clientId, topic);
            }
            LOG.debug("Disconnecting client: {} from broker: {}", clientId, getEndpoint().getConfiguration().getBrokerUrl());
            client.disconnect();
        }
        client = null;
    }

    @Override
    public PahoMqtt5Endpoint getEndpoint() {
        return (PahoMqtt5Endpoint) super.getEndpoint();
    }

    public Exchange createExchange(MqttMessage mqttMessage, String topic) {
        Exchange exchange = createExchange(true);

        PahoMqtt5Message paho = new PahoMqtt5Message(exchange.getContext(), mqttMessage);
        paho.setBody(mqttMessage.getPayload());
        paho.setHeader(PahoMqtt5Constants.MQTT_TOPIC, topic);
        paho.setHeader(PahoMqtt5Constants.MQTT_QOS, mqttMessage.getQos());

        exchange.setIn(paho);
        return exchange;
    }

}
