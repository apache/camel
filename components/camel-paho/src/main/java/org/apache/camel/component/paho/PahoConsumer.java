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
import org.apache.camel.support.DefaultConsumer;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class PahoConsumer extends DefaultConsumer {

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

        connectOptions = PahoEndpoint.createMqttConnectOptions(getEndpoint().getConfiguration());

        if (client == null) {
            clientId = getEndpoint().getConfiguration().getClientId();
            if (clientId == null) {
                clientId = "camel-" + MqttClient.generateClientId();
            }
            stopClient = true;
            client = new MqttClient(getEndpoint().getConfiguration().getBrokerUrl(),
                    clientId,
                    PahoEndpoint.createMqttClientPersistence(getEndpoint().getConfiguration()));
            log.debug("Connecting client: {} to broker: {}", clientId, getEndpoint().getConfiguration().getBrokerUrl());
            client.connect(connectOptions);
        }

        client.setCallback(new MqttCallbackExtended() {

            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                if (reconnect) {
                    try {
                        client.subscribe(getEndpoint().getTopic(), getEndpoint().getConfiguration().getQos());
                    } catch (MqttException e) {
                        log.error("MQTT resubscribe failed {}", e.getMessage(), e);
                    }
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                log.debug("MQTT broker connection lost due {}", cause.getMessage(), cause);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                log.debug("Message arrived on topic: {} -> {}", topic, message);
                Exchange exchange = getEndpoint().createExchange(message, topic);

                getAsyncProcessor().process(exchange, new AsyncCallback() {
                    @Override
                    public void done(boolean doneSync) {
                        // noop
                    }
                });
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                log.debug("Delivery complete. Token: {}", token);
            }
        });

        log.debug("Subscribing client: {} to topic: {}", clientId, getEndpoint().getTopic());
        client.subscribe(getEndpoint().getTopic(), getEndpoint().getConfiguration().getQos());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (stopClient && client != null && client.isConnected()) {
            String topic = getEndpoint().getTopic();
            // only unsubscribe if we are not durable
            if (getEndpoint().getConfiguration().isCleanSession()) {
                log.debug("Unsubscribing client: {} from topic: {}", clientId, topic);
                client.unsubscribe(topic);
            } else {
                log.debug("Client: {} is durable so will not unsubscribe from topic: {}", clientId, topic);
            }
            log.debug("Disconnecting client: {} from broker: {}", clientId, getEndpoint().getConfiguration().getBrokerUrl());
            client.disconnect();
            client = null;
        }
    }

    @Override
    public PahoEndpoint getEndpoint() {
        return (PahoEndpoint) super.getEndpoint();
    }

}