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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.DefaultConsumer;
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
    private final AtomicBoolean restarting = new AtomicBoolean(false);

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

            // the client can be shared by the consumers of several endpoints, and it has a single callback
            PahoClientCallback.register(client, this, stopClient);

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
            PahoClientCallback.unregister(this);
            throw startException;
        }
    }

    private void restartRouteAsync() {
        if (!restarting.compareAndSet(false, true)) {
            LOG.debug("Route restart already in progress, skipping duplicate restart");
            return;
        }
        String threadName = "Paho-RestartRoute-" + getRouteId();
        ExecutorService executor = null;
        try {
            executor
                    = getEndpoint().getCamelContext().getExecutorServiceManager().newSingleThreadExecutor(this, threadName);
            ExecutorService restartExecutor = executor;
            restartExecutor.submit(() -> {
                try {
                    if (!isRunAllowed() || !getEndpoint().getCamelContext().isRunAllowed()) {
                        LOG.debug("Consumer or Camel context is stopping, skipping route restart");
                        return;
                    }
                    String routeId = getRouteId();
                    LOG.info("Stopping route {} for restart after resubscribe failure", routeId);
                    getEndpoint().getCamelContext().getRouteController().stopRoute(routeId);
                    if (!getEndpoint().getCamelContext().isRunAllowed()) {
                        LOG.debug("Camel context is stopping, not restarting route {}", routeId);
                        return;
                    }
                    LOG.info("Restarting route {}", routeId);
                    getEndpoint().getCamelContext().getRouteController().startRoute(routeId);
                } catch (Exception e) {
                    getExceptionHandler().handleException(
                            "Failed to restart route after resubscribe failure", e);
                } finally {
                    restarting.set(false);
                    getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(restartExecutor);
                }
            });
        } catch (RuntimeException e) {
            restarting.set(false);
            if (executor != null) {
                getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(executor);
            }
            getExceptionHandler().handleException(
                    "Failed to schedule route restart after resubscribe failure", e);
        }
    }

    @Override
    protected void doStop() throws Exception {
        MqttClient ownedClient = stopClient ? client : null;
        Exception stopException = null;
        try {
            super.doStop();

            MqttClient sharedClient = ownedClient == null ? client : null;
            if (sharedClient != null) {
                // the messages of a shared client no longer go to this consumer (the own client of the consumer is
                // unregistered after its disconnect, so that its messages are routed until then, as before)
                boolean topicStillUsed = PahoClientCallback.unregister(this);
                if (sharedClient.isConnected() && getEndpoint().getConfiguration().isCleanSession() && !topicStillUsed) {
                    // the client stays connected for the other users, so remove the subscription of this consumer (unless
                    // another consumer of the client uses the same topic filter: the broker has one subscription)
                    LOG.debug("Unsubscribing shared client from topic: {}", getEndpoint().getTopic());
                    sharedClient.unsubscribe(getEndpoint().getTopic());
                }
            }
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
            PahoClientCallback.unregister(this);
            if (ownedClient != null) {
                // an externally provided client is kept, so that the consumer uses it again when it starts again
                client = null;
            }
            stopClient = false;
            if (ownedClient != null) {
                stopException = closeOwnedClient(ownedClient, stopException);
            }
        }
        if (stopException != null) {
            throw stopException;
        }
    }

    /**
     * Subscribes again after the client reconnected.
     */
    void resubscribe() {
        MqttClient current = client;
        if (current == null || !isRunAllowed()) {
            // stopping: do not subscribe the topic again, the stop unsubscribes it
            return;
        }
        try {
            current.subscribe(getEndpoint().getTopic(), getEndpoint().getConfiguration().getQos());
        } catch (MqttException e) {
            if (stopClient) {
                LOG.warn("MQTT resubscribe failed on reconnect, restarting route for recovery: {}",
                        e.getMessage(), e);
                restartRouteAsync();
            } else {
                LOG.error(
                        "MQTT resubscribe failed on reconnect with externally provided client,"
                          + " route will not be auto-restarted: {}",
                        e.getMessage(), e);
            }
        }
    }

    void onMessageArrived(String topic, MqttMessage message) {
        Exchange exchange = createExchange(message, topic);

        // use default consumer callback
        AsyncCallback cb = defaultConsumerCallback(exchange, true);
        getAsyncProcessor().process(exchange, cb);
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
