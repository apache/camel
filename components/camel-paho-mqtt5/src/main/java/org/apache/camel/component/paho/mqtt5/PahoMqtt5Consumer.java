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
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttClientException;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttReturnCode;
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
    // guards the replacement of the client by a reconnect, the client = null of a stop, and the acknowledgement of an
    // exchange: an exchange is never acknowledged on a client that the consumer closed
    private final Object clientLock = new Object();
    // the client that a reconnect closes, until the reconnect replaced it: its exchanges are no longer acknowledged
    private MqttClient replacedClient;
    // failed exchanges in a row that made the consumer reconnect, for the backoff; reset by a successful exchange
    private final AtomicInteger failedMessageReconnects = new AtomicInteger();
    private volatile ScheduledExecutorService reconnectExecutor;

    public PahoMqtt5Consumer(Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    /**
     * The client of the consumer. With manualAcksEnabled on a session that the broker keeps, a failed exchange makes
     * the consumer replace its own client with a new one (see {@link #reconnect(MqttClient)}), so this can return
     * another instance afterwards.
     */
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

            failedMessageReconnects.set(0);
            if (stopClient && getEndpoint().getConfiguration().isManualAcksEnabled() && isSessionKeptAfterDisconnect()) {
                // before the connect: the broker sends the unacknowledged messages of the session right after CONNACK,
                // and one of them can fail and need a reconnect
                reconnectExecutor = getEndpoint().getCamelContext().getExecutorServiceManager()
                        .newSingleThreadScheduledExecutor(this, "PahoMqtt5-Reconnect");
            }

            if (stopClient) {
                clientId = getEndpoint().getConfiguration().getClientId();
                if (clientId == null) {
                    clientId = PahoMqtt5Endpoint.generateClientId();
                }
                client = createOwnClient();
            }

            // the client can be shared by the consumers of several endpoints, and it has a single callback
            PahoMqtt5ClientCallback.register(client, this, stopClient);

            if (stopClient) {
                // connect once the callback is set: on a session that the broker kept, it sends the messages of the
                // session right after CONNACK, and Paho drops a message that arrives while the client has no callback
                // (and completes the QoS 2 flow of it all the same)
                LOG.debug("Connecting client: {} to broker: {}", clientId, getEndpoint().getConfiguration().getBrokerUrl());
                client.connect(connectionOptions);
            }

            LOG.debug("Subscribing client: {} to topic: {}", clientId, getEndpoint().getTopic());
            subscribe(client);
        } catch (Exception startException) {
            shutdownReconnectExecutor();
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
            PahoMqtt5ClientCallback.unregister(this);
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
        MqttClient ownedClient;
        synchronized (clientLock) {
            // isRunAllowed() is false by now, so a reconnect no longer replaces the client
            ownedClient = stopClient ? client : null;
        }
        Exception stopException = null;
        shutdownReconnectExecutor();
        reconnecting.set(false);
        try {
            super.doStop();

            MqttClient sharedClient = ownedClient == null ? client : null;
            if (sharedClient != null) {
                // the messages of a shared client no longer go to this consumer (the own client of the consumer is
                // unregistered after its disconnect, so that its messages are routed until then, as before)
                boolean topicStillUsed = PahoMqtt5ClientCallback.unregister(this);
                if (sharedClient.isConnected() && getEndpoint().getConfiguration().isCleanStart() && !topicStillUsed) {
                    // the client stays connected for the other users, so remove the subscription of this consumer (unless
                    // another consumer of the client uses the same topic filter: the broker has one subscription)
                    LOG.debug("Unsubscribing shared client from topic: {}", getEndpoint().getTopic());
                    sharedClient.unsubscribe(getEndpoint().getTopic());
                }
            }
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
            PahoMqtt5ClientCallback.unregister(this);
            synchronized (clientLock) {
                if (ownedClient != null) {
                    // an externally provided client is kept, so that the consumer uses it again when it starts again
                    client = null;
                }
                replacedClient = null;
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

    private void shutdownReconnectExecutor() {
        ScheduledExecutorService executor = reconnectExecutor;
        if (executor != null) {
            reconnectExecutor = null;
            getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(executor);
        }
    }

    /**
     * Whether the broker keeps the session after the connection closes: cleanStart=false and a session expiry interval
     * greater than 0. An absent session expiry interval (the default, -1) means 0 (MQTT 5, 3.1.2.11.2), so the session
     * ends with the connection.
     */
    private boolean isSessionKeptAfterDisconnect() {
        PahoMqtt5Configuration configuration = getEndpoint().getConfiguration();
        return !configuration.isCleanStart() && configuration.getSessionExpiryInterval() > 0;
    }

    /**
     * The client connected: subscribes again after an automatic reconnect of Paho. The first connect of a client is not
     * a reconnect, also the connect of the new client of {@link #reconnect(MqttClient)}, which subscribes itself, only
     * when the broker did not keep the session.
     */
    void connectComplete(boolean reconnect) {
        if (reconnect) {
            resubscribe();
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
            subscribe(current);
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

    /**
     * Subscribes the client to the topic of the endpoint. Paho returns from the subscribe once the SUBACK arrived, also
     * when the broker refused the subscription: the reason code in the SUBACK is then 0x80 or higher (MQTT 5, 3.9.3),
     * and the broker sends no message of the topic. That is a failed subscribe, as a subscribe that throws.
     */
    private void subscribe(MqttClient mqttClient) throws MqttException {
        IMqttToken token = mqttClient.subscribe(getEndpoint().getTopic(), getEndpoint().getConfiguration().getQos());
        int[] reasonCodes = token != null ? token.getReasonCodes() : null;
        if (reasonCodes != null) {
            for (int reasonCode : reasonCodes) {
                if (reasonCode >= MqttReturnCode.RETURN_CODE_UNSPECIFIED_ERROR) {
                    LOG.debug("The broker refused the subscription of client: {} to topic: {} with reason code: {}",
                            mqttClient.getClientId(), getEndpoint().getTopic(), reasonCode);
                    throw new MqttException(reasonCode);
                }
            }
        }
    }

    void onMessageArrived(String topic, MqttMessage message) {
        Exchange exchange = createExchange(message, topic);

        // use default consumer callback
        AsyncCallback cb = defaultConsumerCallback(exchange, true);
        getAsyncProcessor().process(exchange, cb);
    }

    /**
     * Creates the own client of the consumer, for a start and for a reconnect after a failed exchange.
     */
    private MqttClient createOwnClient() throws MqttException {
        MqttClient ownClient = createClient();
        if (getEndpoint().getConfiguration().isManualAcksEnabled()) {
            ownClient.setManualAcks(true);
        }
        return ownClient;
    }

    MqttClient createClient() throws MqttException {
        return new MqttClient(
                getEndpoint().getConfiguration().getBrokerUrl(),
                clientId,
                PahoMqtt5Endpoint.createMqttClientPersistence(getEndpoint().getConfiguration()));
    }

    private Exception closeOwnedClient(MqttClient ownedClient, Exception primaryException) {
        try {
            closeClient(ownedClient);
        } catch (Exception closeException) {
            if (primaryException == null) {
                return closeException;
            }
            primaryException.addSuppressed(closeException);
        }
        return primaryException;
    }

    /**
     * Closes the own client of the consumer. Paho does not close a client while it connects, also with force: the close
     * throws 32110 (connect already in progress), and the client goes on connecting, with the automatic reconnect of
     * Paho after a lost connection, or with the reconnect of the consumer that its stop interrupted. Such a client
     * would connect later next to the client of the next start, and with the same client id the two take the session
     * from each other. A forced disconnect sets the client to disconnected in any state and shuts down what the connect
     * has opened so far, so the client can then be closed, and a connect still in progress does not complete on a
     * closed client. A connect that completed just after the stop saw the client disconnected makes the close throw
     * 32100 (client is connected): that client is disconnected first, as a connected client is.
     */
    private static void closeClient(MqttClient ownedClient) throws MqttException {
        try {
            ownedClient.close(true);
        } catch (MqttException e) {
            if (e.getReasonCode() == MqttClientException.REASON_CODE_CONNECT_IN_PROGRESS) {
                LOG.debug("Client: {} is connecting: aborting the connect before closing the client",
                        ownedClient.getClientId());
                // no quiesce and no DISCONNECT packet: the client has no connection yet
                ownedClient.disconnectForcibly(0, 0, false);
            } else if (e.getReasonCode() == MqttClientException.REASON_CODE_CLIENT_CONNECTED) {
                LOG.debug("Client: {} connected while the consumer stopped: disconnecting before closing the client",
                        ownedClient.getClientId());
                try {
                    ownedClient.disconnect();
                } catch (MqttException disconnectException) {
                    // disconnected or disconnecting in the meantime: leave it disconnected in any state
                    LOG.debug("Client: {} could not be disconnected: {}", ownedClient.getClientId(),
                            disconnectException.getMessage());
                    ownedClient.disconnectForcibly(0, 0, false);
                }
            } else {
                throw e;
            }
            // an automatic reconnect that starts between the disconnect and this close would make it throw 32110
            // again; Paho reschedules a failed attempt of the reconnect cycle at least 2 s later (the delay
            // doubles), and the forced disconnect starts no new cycle, so this is not retried
            ownedClient.close(true);
        }
    }

    /**
     * MQTT has no negative acknowledgement: the broker sends an unacknowledged message again only when the client
     * connects again to a session that the broker kept, and it sends no more than receiveMaximum unacknowledged
     * messages to the client. Paho holds back only the PUBACK of a QoS 1 message until it is acknowledged manually; it
     * completes the QoS 2 flow (PUBREC, PUBCOMP) on its own, so the broker never sends a QoS 2 message again. A failed
     * QoS 1 message that is just left unacknowledged is therefore never retried, and enough of them stop the consumer
     * for good.
     * <p>
     * For a QoS 1 message on a session that the broker keeps after the disconnect (cleanStart=false and
     * sessionExpiryInterval greater than 0) the client reconnects, so that the broker sends the message again; the
     * reconnects of failed exchanges in a row back off (see {@link #reconnectDelay(int)}), so that a message that
     * always fails does not reconnect in a tight loop. In every other case the broker never sends the message again, so
     * it is acknowledged, which frees its place in the receive window.
     */
    private void rollback(Exchange exchange, MqttMessage mqttMessage, MqttClient receivingClient) {
        MqttException ackException = null;
        synchronized (clientLock) {
            if (!isCurrentClient(receivingClient)) {
                logNotAcknowledgedAfterStop(exchange, mqttMessage);
                return;
            }
            if (mqttMessage.getQos() != 1 || !isSessionKeptAfterDisconnect()) {
                LOG.debug("Acknowledging failed MQTT message with ID: {} (QoS {}) of Exchange ID: {} as the broker does"
                          + " not deliver it again",
                        mqttMessage.getId(), mqttMessage.getQos(), exchange.getExchangeId());
                try {
                    receivingClient.messageArrivedComplete(mqttMessage.getId(), mqttMessage.getQos());
                } catch (MqttException e) {
                    ackException = e;
                }
            } else if (stopClient) {
                // under the lock, so that the reconnect is scheduled for the client that is still the current one
                reconnectAfterFailure(receivingClient, exchange, mqttMessage);
            } else {
                LOG.warn("Rollback due to error processing Exchange ID: {}: MQTT message with ID: {} stays unacknowledged"
                         + " until the externally provided client connects again",
                        exchange.getExchangeId(), mqttMessage.getId());
            }
        }
        if (ackException != null) {
            getExceptionHandler().handleException(
                    "Error acknowledging failed MQTT message with ID: " + mqttMessage.getId(), exchange, ackException);
        }
    }

    /**
     * Whether an exchange of the given client is acknowledged: the client is the current one, and a reconnect is not
     * closing it. Called with the clientLock held.
     */
    private boolean isCurrentClient(MqttClient receivingClient) {
        return receivingClient != null && receivingClient == client && receivingClient != replacedClient;
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
     * the client (1 second), doubled for each further failed exchange in a row, up to maxReconnectDelay, but never less
     * than the minimum (a maxReconnectDelay below it would reconnect in a tight loop).
     */
    private long reconnectDelay(int attempt) {
        long max = Math.max(0, getEndpoint().getConfiguration().getMaxReconnectDelay());
        MqttConnectionOptions options = connectionOptions;
        long min = options != null ? TimeUnit.SECONDS.toMillis(options.getAutomaticReconnectMinDelay()) : 1000;
        long delay = min << Math.min(attempt - 1, 20);
        return Math.max(min, Math.min(delay, max));
    }

    /**
     * Reconnects after a failed exchange, so that the broker sends the unacknowledged messages of the session again.
     * <p>
     * The consumer does not connect the same client again: it disconnects and closes it, and connects a new client with
     * the same client id, which resumes the session that the broker kept. Paho 1.2.5 starts the threads of a new
     * connection of a client (CommsCallback, CommsSender) only once the threads of its previous connection have ended,
     * and otherwise waits for them forever, with no timeout. With a synchronous route the exchange runs on the callback
     * thread of Paho, so that thread is still busy when another message is in the route, and a connect of the same
     * client hung there (CAMEL-27043). The exchanges still in the route on the closed client are not acknowledged: the
     * broker sends them again to the new client.
     */
    private void reconnect(MqttClient current) {
        if (!isRunAllowed() || current != client) {
            // the consumer stopped, or started again with another client
            return;
        }
        try {
            // the message callback may still be running (disconnect is not allowed from it), so this runs here
            if (current.isConnected()) {
                current.disconnect();
            }
        } catch (Exception e) {
            getExceptionHandler().handleException("Failed to disconnect after a failed exchange", e);
        }
        synchronized (clientLock) {
            // from now on its exchanges are not acknowledged: the client is closed next (an acknowledgement on a closed
            // client fails in Paho), and the broker sends them again to the new client
            replacedClient = current;
        }
        Exception closeException = closeOwnedClient(current, null);
        if (closeException != null) {
            getExceptionHandler().handleException("Failed to close the client after a failed exchange", closeException);
        }
        MqttClient next = null;
        boolean replaced = false;
        try {
            // after the close: with file persistence both clients use the directory of the client id, and Paho 1.2.5
            // goes on when another client holds its lock, so the old client is done with it first
            next = createOwnClient();
            synchronized (clientLock) {
                // the stop reads the client under the same lock once isRunAllowed() is false, so either it closes the
                // new client, or the new client is not used
                if (isRunAllowed() && current == client) {
                    client = next;
                    replacedClient = null;
                    PahoMqtt5ClientCallback.unregister(this);
                    // the callback is set before the connect: the broker sends the messages of the session right after
                    // CONNACK
                    PahoMqtt5ClientCallback.register(next, this, true);
                    // a message that the broker sends again on the new connection can fail before this method returns,
                    // and must be able to schedule the next reconnect
                    reconnecting.set(false);
                    replaced = true;
                } else if (replacedClient == current) {
                    replacedClient = null;
                }
            }
            if (!replaced) {
                LOG.debug("The consumer of client: {} stopped while it reconnected: the new client is closed", clientId);
                Exception closeFailure = closeOwnedClient(next, null);
                if (closeFailure != null) {
                    LOG.debug("Client: {} could not be closed: {}", clientId, closeFailure.getMessage(), closeFailure);
                }
                return;
            }
            LOG.debug("Connecting a new client: {} to broker: {} after a failed exchange", clientId,
                    getEndpoint().getConfiguration().getBrokerUrl());
            IMqttToken token = next.connectWithResult(connectionOptions);
            // the subscription is part of the session; subscribing again would make the broker send the retained
            // message of the topic again (Retain Handling 0)
            if (!token.getSessionPresent()) {
                LOG.warn("The broker did not keep the session of client: {}: the failed MQTT message and the other"
                         + " unacknowledged ones are not delivered again; subscribing again to topic: {}",
                        clientId, getEndpoint().getTopic());
                subscribeAfterReconnect(next);
            }
        } catch (Exception e) {
            getExceptionHandler().handleException("Failed to reconnect after a failed exchange", e);
            // the client does not reconnect on its own after a disconnect it was asked for, so try again later
            synchronized (clientLock) {
                MqttClient retry = replaced ? next : current;
                if (!replaced) {
                    // no new client: this reconnect is still the one pending
                    reconnecting.set(false);
                }
                if (retry == client) {
                    scheduleReconnect(retry, "Reconnect failed");
                }
            }
        }
    }

    /**
     * Subscribes again after the reconnect of {@link #reconnect(MqttClient)} found no session. The connect created a
     * session that the broker keeps, so a later reconnect finds it and does not subscribe: a failed subscribe would
     * leave the consumer connected without its subscription. The route is restarted instead, as for a failed
     * resubscribe after an automatic reconnect.
     */
    private void subscribeAfterReconnect(MqttClient current) {
        try {
            subscribe(current);
        } catch (MqttException e) {
            LOG.warn("MQTT subscribe failed after a reconnect, restarting route for recovery: {}", e.getMessage(), e);
            restartRouteAsync();
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
            // the message is acknowledged on the client it arrived on: once the consumer stopped or reconnected after a
            // failed exchange, that client is closed (and replaced by a new client), and its packet id means nothing to
            // another client
            MqttClient receivingClient = client;
            exchange.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
                @Override
                public void onComplete(Exchange exchange) {
                    MqttException ackException = null;
                    synchronized (clientLock) {
                        if (!isCurrentClient(receivingClient)) {
                            // not a message of the current client: its backoff is left as is
                            logNotAcknowledgedAfterStop(exchange, mqttMessage);
                            return;
                        }
                        failedMessageReconnects.set(0);
                        try {
                            receivingClient.messageArrivedComplete(mqttMessage.getId(), mqttMessage.getQos());
                        } catch (MqttException e) {
                            ackException = e;
                        }
                    }
                    if (ackException != null) {
                        getExceptionHandler().handleException(
                                "Error acknowledging MQTT message with ID: " + mqttMessage.getId(), exchange, ackException);
                    }
                }

                @Override
                public void onFailure(Exchange exchange) {
                    rollback(exchange, mqttMessage, receivingClient);
                }
            });
        }
        return exchange;
    }

    private void logNotAcknowledgedAfterStop(Exchange exchange, MqttMessage mqttMessage) {
        LOG.debug("MQTT message with ID: {} of Exchange ID: {} is not acknowledged: the consumer stopped or reconnected, and"
                  + " closed the client it arrived on (on a session that the broker keeps, the broker sends it again to a"
                  + " client with the same client id)",
                mqttMessage.getId(), exchange.getExchangeId());
    }

}
