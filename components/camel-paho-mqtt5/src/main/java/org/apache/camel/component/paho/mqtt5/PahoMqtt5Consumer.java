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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.SynchronizationAdapter;
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
    private final AtomicBoolean restarting = new AtomicBoolean(false);
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);
    // failed exchanges in a row that made the consumer reconnect, for the backoff; reset by a successful exchange
    private final AtomicInteger failedMessageReconnects = new AtomicInteger();
    private volatile ScheduledExecutorService reconnectExecutor;

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

        stopClient = client == null;
        try {
            connectionOptions = getEndpoint().createMqttConnectionOptions();

            if (stopClient) {
                clientId = getEndpoint().getConfiguration().getClientId();
                if (clientId == null) {
                    clientId = PahoMqtt5Endpoint.generateClientId();
                }
                client = createClient();
                LOG.debug("Connecting client: {} to broker: {}", clientId, getEndpoint().getConfiguration().getBrokerUrl());
                if (getEndpoint().getConfiguration().isManualAcksEnabled()) {
                    client.setManualAcks(true);
                }
                client.connect(connectionOptions);
            }

            client.setCallback(new MqttCallback() {

                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    if (reconnect) {
                        try {
                            client.subscribe(getEndpoint().getTopic(), getEndpoint().getConfiguration().getQos());
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

            failedMessageReconnects.set(0);
            if (stopClient && getEndpoint().getConfiguration().isManualAcksEnabled()
                    && !getEndpoint().getConfiguration().isCleanStart()) {
                reconnectExecutor = getEndpoint().getCamelContext().getExecutorServiceManager()
                        .newSingleThreadScheduledExecutor(this, "PahoMqtt5-Reconnect");
            }
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

    private void restartRouteAsync() {
        if (!restarting.compareAndSet(false, true)) {
            LOG.debug("Route restart already in progress, skipping duplicate restart");
            return;
        }
        String threadName = "PahoMqtt5-RestartRoute-" + getRouteId();
        ExecutorService executor
                = getEndpoint().getCamelContext().getExecutorServiceManager().newSingleThreadExecutor(this, threadName);
        executor.submit(() -> {
            try {
                String routeId = getRouteId();
                LOG.info("Stopping route {} for restart after resubscribe failure", routeId);
                getEndpoint().getCamelContext().getRouteController().stopRoute(routeId);
                LOG.info("Restarting route {}", routeId);
                getEndpoint().getCamelContext().getRouteController().startRoute(routeId);
            } catch (Exception e) {
                getExceptionHandler().handleException(
                        "Failed to restart route after resubscribe failure", e);
            } finally {
                restarting.set(false);
                getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(executor);
            }
        });
    }

    @Override
    protected void doStop() throws Exception {
        MqttClient ownedClient = stopClient ? client : null;
        Exception stopException = null;
        ScheduledExecutorService executor = reconnectExecutor;
        if (executor != null) {
            reconnectExecutor = null;
            getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(executor);
        }
        reconnecting.set(false);
        try {
            super.doStop();

            if (ownedClient != null && ownedClient.isConnected()) {
                String topic = getEndpoint().getTopic();
                // only unsubscribe if we are not durable
                if (getEndpoint().getConfiguration().isCleanStart()) {
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
                PahoMqtt5Endpoint.createMqttClientPersistence(getEndpoint().getConfiguration()));
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

    /**
     * MQTT has no negative acknowledgement: the broker sends an unacknowledged message again only when the client
     * connects again to the same session, and it sends no more than receiveMaximum unacknowledged messages to the
     * client. A failed message that is just left unacknowledged is therefore never retried, and enough of them stop the
     * consumer for good.
     * <p>
     * With cleanStart=false the client reconnects, so that the broker sends the message again; the reconnects of failed
     * exchanges in a row back off (see {@link #reconnectDelay(int)}), so that a message that always fails does not
     * reconnect in a tight loop. With a clean session the broker never sends it again, so it is acknowledged, which
     * frees its place in the receive window.
     */
    private void rollback(Exchange exchange, MqttMessage mqttMessage) {
        MqttClient current = client;
        if (current == null) {
            return;
        }
        if (getEndpoint().getConfiguration().isCleanStart()) {
            LOG.debug("Acknowledging failed MQTT message with ID: {} of Exchange ID: {} as a clean session never"
                      + " delivers it again",
                    mqttMessage.getId(), exchange.getExchangeId());
            try {
                current.messageArrivedComplete(mqttMessage.getId(), mqttMessage.getQos());
            } catch (MqttException e) {
                getExceptionHandler().handleException(
                        "Error acknowledging failed MQTT message with ID: " + mqttMessage.getId(), exchange, e);
            }
        } else if (stopClient) {
            reconnectAfterFailure(current, exchange, mqttMessage);
        } else {
            LOG.warn("Rollback due to error processing Exchange ID: {}: MQTT message with ID: {} stays unacknowledged"
                     + " until the externally provided client connects again",
                    exchange.getExchangeId(), mqttMessage.getId());
        }
    }

    private void reconnectAfterFailure(MqttClient current, Exchange exchange, MqttMessage mqttMessage) {
        String reason = "Rollback due to error processing Exchange ID: " + exchange.getExchangeId()
                        + ", so that the broker delivers MQTT message with ID: " + mqttMessage.getId() + " again";
        if (!scheduleReconnect(current, reason)) {
            LOG.debug("{}: a reconnect is already scheduled", reason);
        }
    }

    /**
     * The delay before the reconnect of the given failed exchange in a row: the minimum automatic reconnect delay of
     * the client (1 second), doubled for each further failed exchange in a row, up to maxReconnectDelay.
     */
    private long reconnectDelay(int attempt) {
        long max = Math.max(0, getEndpoint().getConfiguration().getMaxReconnectDelay());
        MqttConnectionOptions options = connectionOptions;
        long min = options != null ? TimeUnit.SECONDS.toMillis(options.getAutomaticReconnectMinDelay()) : 1000;
        long delay = min << Math.min(attempt - 1, 20);
        return Math.min(delay, max);
    }

    private void reconnect(MqttClient current) {
        try {
            // the message callback may still be running (disconnect is not allowed from it), so this runs here
            if (isRunAllowed() && current == client && current.isConnected()) {
                current.disconnect();
            }
        } catch (Exception e) {
            getExceptionHandler().handleException("Failed to disconnect after a failed exchange", e);
        }
        // the disconnect covers every message that failed until now; a message the broker sends again on the new
        // connection can fail before this method returns, and must be able to schedule the next reconnect
        reconnecting.set(false);
        try {
            if (isRunAllowed() && current == client && !current.isConnected()) {
                current.connect(connectionOptions);
                current.subscribe(getEndpoint().getTopic(), getEndpoint().getConfiguration().getQos());
            }
        } catch (Exception e) {
            getExceptionHandler().handleException("Failed to reconnect after a failed exchange", e);
            // the client does not reconnect on its own after a disconnect it was asked for, so try again later
            scheduleReconnect(current, "Reconnect failed");
        }
    }

    /**
     * Schedules a reconnect after the backoff delay, unless one is already scheduled or the consumer is stopping.
     */
    private boolean scheduleReconnect(MqttClient current, String reason) {
        ScheduledExecutorService executor = reconnectExecutor;
        if (executor == null || !isRunAllowed() || !reconnecting.compareAndSet(false, true)) {
            return false;
        }
        int attempt = failedMessageReconnects.incrementAndGet();
        long delay = reconnectDelay(attempt);
        LOG.warn("{}: reconnecting in {} ms (reconnect {} in a row)", reason, delay, attempt);
        try {
            executor.schedule(() -> reconnect(current), delay, TimeUnit.MILLISECONDS);
            return true;
        } catch (RuntimeException e) {
            // the consumer is stopping
            reconnecting.set(false);
            return false;
        }
    }

    /**
     * The number of failed exchanges in a row that made the consumer reconnect. Package-private, for tests.
     */
    int getFailedMessageReconnects() {
        return failedMessageReconnects.get();
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
        paho.setHeader(PahoMqtt5Constants.CAMEL_PAHO_MSG_PROPERTIES, mqttMessage.getProperties());

        exchange.setIn(paho);
        if (getEndpoint().getConfiguration().isManualAcksEnabled()) {
            exchange.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
                @Override
                public void onComplete(Exchange exchange) {
                    failedMessageReconnects.set(0);
                    try {
                        PahoMqtt5Consumer.this.client.messageArrivedComplete(mqttMessage.getId(), mqttMessage.getQos());
                    } catch (MqttException e) {
                        getExceptionHandler().handleException(
                                "Error acknowledging MQTT message with ID: " + mqttMessage.getId(),
                                exchange, e);
                    }
                }

                @Override
                public void onFailure(Exchange exchange) {
                    rollback(exchange, mqttMessage);
                }
            });
        }
        return exchange;
    }

}
