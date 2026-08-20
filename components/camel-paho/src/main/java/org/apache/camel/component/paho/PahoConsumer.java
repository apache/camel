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

import org.apache.camel.AsyncCallback;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.DefaultConsumer;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Deprecated(since = "4.21")
public class PahoConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(PahoConsumer.class);

    private volatile MqttClient client;
    private volatile String clientId;
    private volatile boolean stopClient;
    private volatile MqttConnectOptions connectOptions;

    public PahoConsumer(Endpoint endpoint, Processor processor) {
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

        stopClient = client == null;
        try {
            connectOptions = PahoEndpoint.createMqttConnectOptions(getEndpoint().getConfiguration());

            if (stopClient) {
                clientId = getEndpoint().getConfiguration().getClientId();
                if (clientId == null) {
                    clientId = "camel-" + MqttClient.generateClientId();
                }
                client = createClient();
                LOG.debug("Connecting client: {} to broker: {}", clientId, getEndpoint().getConfiguration().getBrokerUrl());
                if (getEndpoint().getConfiguration().isManualAcksEnabled()) {
                    client.setManualAcks(true);
                }
                client.connect(connectOptions);
            }

            client.setCallback(new MqttCallbackExtended() {

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
                public void connectionLost(Throwable cause) {
                    LOG.debug("MQTT broker connection lost due {}", cause.getMessage(), cause);
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
                public void deliveryComplete(IMqttDeliveryToken token) {
                    LOG.debug("Delivery complete. Token: {}", token);
                }
            });

            LOG.debug("Subscribing client: {} to topic: {}", clientId, getEndpoint().getTopic());
            client.subscribe(getEndpoint().getTopic(), getEndpoint().getConfiguration().getQos());
        } catch (Exception startException) {
            MqttClient ownedClient = stopClient ? client : null;
            if (ownedClient != null) {
                client = null;
                stopClient = false;
                if (ownedClient.isConnected()) {
                    try {
                        ownedClient.disconnect();
                    } catch (Exception disconnectException) {
                        startException.addSuppressed(disconnectException);
                    }
                }
                closeOwnedClient(ownedClient, startException);
            }
            throw startException;
        }
    }

    @Override
    protected void doStop() throws Exception {
        MqttClient ownedClient = stopClient ? client : null;
        Exception stopException = null;
        try {
            super.doStop();

            if (ownedClient != null && ownedClient.isConnected()) {
                String topic = getEndpoint().getTopic();
                // only unsubscribe if we are not durable
                if (getEndpoint().getConfiguration().isCleanSession()) {
                    LOG.debug("Unsubscribing client: {} from topic: {}", clientId, topic);
                    ownedClient.unsubscribe(topic);
                } else {
                    LOG.debug("Client: {} is durable so will not unsubscribe from topic: {}", clientId, topic);
                }
                LOG.debug("Disconnecting client: {} from broker: {}", clientId,
                        getEndpoint().getConfiguration().getBrokerUrl());
                ownedClient.disconnect();
            }
        } catch (Exception e) {
            stopException = e;
        } finally {
            client = null;
            stopClient = false;
            if (ownedClient != null) {
                stopException = closeOwnedClient(ownedClient, stopException);
            }
        }
        if (stopException != null) {
            throw stopException;
        }
    }

    MqttClient createClient() throws MqttException {
        return new MqttClient(
                getEndpoint().getConfiguration().getBrokerUrl(),
                clientId,
                PahoEndpoint.createMqttClientPersistence(getEndpoint().getConfiguration()));
    }

    private Exception closeOwnedClient(MqttClient ownedClient, Exception primaryException) {
        try {
            ownedClient.close(true);
        } catch (Exception closeException) {
            if (primaryException == null) {
                return closeException;
            }
            primaryException.addSuppressed(closeException);
        }
        return primaryException;
    }

    @Override
    public PahoEndpoint getEndpoint() {
        return (PahoEndpoint) super.getEndpoint();
    }

    public Exchange createExchange(MqttMessage mqttMessage, String topic) {
        Exchange exchange = createExchange(true);

        PahoMessage paho = new PahoMessage(exchange.getContext(), mqttMessage);
        paho.setBody(mqttMessage.getPayload());
        paho.setHeader(PahoConstants.MQTT_TOPIC, topic);
        paho.setHeader(PahoConstants.MQTT_QOS, mqttMessage.getQos());

        exchange.setIn(paho);
        if (getEndpoint().getConfiguration().isManualAcksEnabled()) {
            exchange.getExchangeExtension().addOnCompletion(new Synchronization() {
                @Override
                public void onComplete(Exchange exchange) {
                    try {
                        PahoConsumer.this.client.messageArrivedComplete(mqttMessage.getId(), mqttMessage.getQos());
                    } catch (MqttException e) {
                        getExceptionHandler().handleException(
                                "Error acknowledging MQTT message with ID: " + mqttMessage.getId(),
                                exchange, e);
                    }
                }

                @Override
                public void onFailure(Exchange exchange) {
                    LOG.error("Rollback due to error processing Exchange ID: {}", exchange.getExchangeId(),
                            exchange.getException());
                }
            });
        }
        return exchange;
    }

}
