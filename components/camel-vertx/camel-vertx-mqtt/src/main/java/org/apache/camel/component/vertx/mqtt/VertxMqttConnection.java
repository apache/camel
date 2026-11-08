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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.messages.MqttPublishMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The connection of an endpoint to its broker, shared by its consumer and producer: one Vert.x MQTT client, connected
 * again by a {@link VertxMqttReconnector} when the connection is lost and {@code automaticReconnect} is on.
 * <p>
 * Every call of the client runs on one Vert.x context, the one the client was connected on, so its state needs no locks
 * and an acknowledgement can never overtake the bookkeeping of the message it acknowledges. The client is held in a
 * field until the connection stops: Vert.x 5 closes a client that is no longer reachable, open connection included.
 */
final class VertxMqttConnection {

    private static final Logger LOG = LoggerFactory.getLogger(VertxMqttConnection.class);
    private static final Pattern IP_ADDRESS = Pattern.compile("[0-9.]+|.*:.*");

    private final Vertx vertx;
    private final BrokerAddress broker;
    private final MqttClientOptions options;
    private final VertxMqttConfiguration configuration;

    private Context context;
    private MqttClient client;
    private volatile boolean started;
    private volatile boolean stopping;
    private volatile boolean connected;
    // Why the connection is down or the subscription failed, null while all is well or before the first attempt
    private volatile Throwable failure;
    // Told of a failure once, not again for every attempt that fails the same way
    private final List<Consumer<Throwable>> failureListeners = new CopyOnWriteArrayList<>();

    // Below on the context only
    private boolean connecting;
    // Null without automaticReconnect
    private VertxMqttReconnector reconnector;
    private final List<Promise<MqttClient>> waiting = new ArrayList<>();
    // The subscription of the consumer, made on every connect, the first and every reconnect: the client keeps no
    // session of its own
    private String subscription;
    private int subscriptionQos;
    private Handler<MqttPublishMessage> messageHandler;
    // Messages of quality of service 1 and 2 waiting for their acknowledgement, by packet identifier
    private final Map<Integer, Promise<Void>> unacknowledged = new HashMap<>();

    VertxMqttConnection(final Vertx vertx, final BrokerAddress broker, final MqttClientOptions options,
                        final VertxMqttConfiguration configuration) {
        this.vertx = vertx;
        this.broker = broker;
        this.options = options;
        this.configuration = configuration;
    }

    /**
     * Connects. With automatic reconnect it returns at once and keeps trying in the background, without it a failed
     * connection fails the start.
     */
    synchronized void start() throws Exception {
        if (started) {
            return;
        }
        started = true;
        stopping = false;
        failure = null;
        context = vertx.getOrCreateContext();
        reconnector = configuration.isAutomaticReconnect()
                ? new VertxMqttReconnector(
                        vertx, broker, configuration.getReconnectDelay(), configuration.getMaxReconnectDelay(),
                        () -> connect(null))
                : null;
        client = MqttClient.create(vertx, options);
        final CompletableFuture<Void> first = new CompletableFuture<>();
        context.runOnContext(v -> {
            client.closeHandler(closed -> closed());
            client.exceptionHandler(e -> LOG.debug("Error on the connection to {}", broker, e));
            client.publishCompletionHandler(this::acknowledged);
            client.publishCompletionExpirationHandler(packetId -> unacknowledged(packetId,
                    "The MQTT broker %s did not acknowledge the message within the ack timeout of %s seconds"
                            .formatted(broker, configuration.getAckTimeout())));
            client.publishCompletionUnknownPacketIdHandler(
                    packetId -> LOG.debug("Acknowledgement of the unknown packet {} from {}", packetId, broker));
            client.publishHandler(message -> {
                final Handler<MqttPublishMessage> handler = messageHandler;
                if (handler != null) {
                    handler.handle(message);
                } else {
                    LOG.debug("Message on {} from {} without a subscriber", message.topicName(), broker);
                }
            });
            client.subscribeCompletionHandler(ack -> {
                if (ack.grantedQoSLevels().stream().anyMatch(granted -> granted == null || granted > 2)) {
                    LOG.warn("The MQTT broker {} refused the subscription to {}", broker, subscription);
                    failed(new IllegalStateException(
                            "The MQTT broker %s refused the subscription to %s".formatted(broker, subscription)));
                }
            });
            connect(first);
        });
        if (!configuration.isAutomaticReconnect()) {
            try {
                first.get(configuration.getConnectTimeout() + 5_000, TimeUnit.MILLISECONDS);
            } catch (final ExecutionException e) {
                stop();
                throw new IllegalStateException("Cannot connect to the MQTT broker %s".formatted(broker), e.getCause());
            } catch (final TimeoutException e) {
                stop();
                throw new IllegalStateException("Timed out connecting to the MQTT broker %s".formatted(broker), e);
            }
        }
    }

    /**
     * Disconnects and stops reconnecting. Waits for the disconnect for a while, never fails.
     */
    synchronized void stop() {
        if (!started) {
            return;
        }
        started = false;
        stopping = true;
        final CompletableFuture<Void> done = new CompletableFuture<>();
        context.runOnContext(v -> {
            if (reconnector != null) {
                reconnector.cancel();
            }
            final IllegalStateException stopped = new IllegalStateException("The connection to %s stopped".formatted(broker));
            waiting.forEach(promise -> promise.tryFail(stopped));
            waiting.clear();
            failUnacknowledged(stopped);
            if (client.isConnected()) {
                client.disconnect().onComplete(ar -> done.complete(null));
            } else {
                done.complete(null);
            }
        });
        try {
            done.get(10, TimeUnit.SECONDS);
        } catch (final Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.debug("Disconnecting from {} did not complete", broker, e);
        }
        connected = false;
        // Only now: until the disconnect completed, the client must stay reachable
        client = null;
    }

    private void connect(final CompletableFuture<Void> first) {
        if (stopping) {
            return;
        }
        connecting = true;
        final String serverName = broker.ssl() && !IP_ADDRESS.matcher(broker.host()).matches() ? broker.host() : null;
        client.connect(broker.port(), broker.host(), serverName).onComplete(ar -> {
            connecting = false;
            if (ar.succeeded()) {
                if (stopping) {
                    client.disconnect();
                    return;
                }
                connected = true;
                failure = null;
                if (reconnector != null) {
                    reconnector.succeeded();
                }
                LOG.info("Connected to the MQTT broker {}", broker);
                subscribe();
                waiting.forEach(promise -> promise.tryComplete(client));
                waiting.clear();
                if (first != null) {
                    first.complete(null);
                }
            } else {
                LOG.warn("Cannot connect to the MQTT broker {}: {}", broker, ar.cause().getMessage());
                failed(new IllegalStateException(
                        "Cannot connect to the MQTT broker %s: %s".formatted(broker, ar.cause().getMessage()), ar.cause()));
                if (first != null) {
                    first.completeExceptionally(ar.cause());
                }
                if (reconnector != null) {
                    reconnector.schedule();
                } else {
                    failWaiting(ar.cause());
                }
            }
        });
    }

    private void closed() {
        connected = false;
        // The client keeps no session of its own, a message sent before the loss is not sent again
        failUnacknowledged(new IllegalStateException(
                "The connection to the MQTT broker %s was lost before the message was acknowledged".formatted(broker)));
        // A failed connect closes the connection too, and reconnects from its own completion
        if (stopping || connecting) {
            return;
        }
        LOG.warn("The connection to the MQTT broker {} was lost", broker);
        failed(new IllegalStateException("The connection to the MQTT broker %s was lost".formatted(broker)));
        if (reconnector != null) {
            reconnector.schedule();
        } else {
            failWaiting(new IllegalStateException("The connection to the MQTT broker %s was lost".formatted(broker)));
        }
    }

    private void failWaiting(final Throwable cause) {
        waiting.forEach(promise -> promise.tryFail(cause));
        waiting.clear();
    }

    /**
     * @return the client once it is connected, on the context of the connection, failed if it does not connect within
     *         the connect timeout
     */
    Future<MqttClient> whenConnected() {
        final Promise<MqttClient> promise = Promise.promise();
        final Context running = context;
        if (running == null || stopping) {
            return Future.failedFuture(new IllegalStateException("The connection to %s is not started".formatted(broker)));
        }
        running.runOnContext(v -> {
            if (stopping) {
                promise.tryFail(new IllegalStateException("The connection to %s stopped".formatted(broker)));
            } else if (client.isConnected()) {
                promise.tryComplete(client);
            } else if (reconnector == null && !connecting) {
                promise.tryFail(new IllegalStateException("Not connected to the MQTT broker %s".formatted(broker)));
            } else {
                waiting.add(promise);
                vertx.setTimer(Math.max(1, configuration.getConnectTimeout()), id -> {
                    if (waiting.remove(promise)) {
                        promise.tryFail(new IllegalStateException(
                                "Not connected to the MQTT broker %s within the connect timeout".formatted(broker)));
                    }
                });
            }
        });
        return promise.future();
    }

    /**
     * Publishes a message once the client is connected.
     *
     * @param  properties the MQTT 5 properties, null for MQTT 3.1.1
     * @return            completed when the broker acknowledged a message of quality of service 1 or 2, when it is
     *                    written for 0
     */
    Future<Void> publish(
            final String topic, final Buffer payload, final MqttQoS qos, final boolean retain,
            final MqttProperties properties) {
        // Composed on the context, synchronously with the publish: the packet is registered before the event loop can
        // read its acknowledgement
        return whenConnected().compose(connectedClient -> {
            final Future<Integer> sent = properties == null
                    ? connectedClient.publish(topic, payload, qos, false, retain)
                    : connectedClient.publish(topic, payload, qos, false, retain, properties);
            return sent.compose(packetId -> {
                if (qos == MqttQoS.AT_MOST_ONCE) {
                    return Future.succeededFuture();
                }
                final Promise<Void> acknowledgement = Promise.promise();
                unacknowledged.put(packetId, acknowledgement);
                return acknowledgement.future();
            });
        });
    }

    private void acknowledged(final int packetId) {
        final Promise<Void> acknowledgement = unacknowledged.remove(packetId);
        if (acknowledgement != null) {
            acknowledgement.tryComplete();
        }
    }

    private void unacknowledged(final int packetId, final String reason) {
        final Promise<Void> acknowledgement = unacknowledged.remove(packetId);
        if (acknowledgement != null) {
            acknowledgement.tryFail(new IllegalStateException(reason));
        }
    }

    private void failUnacknowledged(final Throwable cause) {
        unacknowledged.values().forEach(acknowledgement -> acknowledgement.tryFail(cause));
        unacknowledged.clear();
    }

    /**
     * Runs a task on the context of the connection.
     */
    void run(final Runnable task) {
        final Context running = context;
        if (running != null) {
            running.runOnContext(v -> task.run());
        }
    }

    /**
     * Subscribes now if connected, and again after every reconnect.
     *
     * @param handler called on the context for every message the broker sends
     */
    void subscribe(final String filter, final int qos, final Handler<MqttPublishMessage> handler) {
        run(() -> {
            subscription = filter;
            subscriptionQos = qos;
            messageHandler = handler;
            subscribe();
        });
    }

    private void subscribe() {
        if (subscription != null && client != null && client.isConnected()) {
            client.subscribe(subscription, subscriptionQos)
                    .onFailure(e -> {
                        LOG.warn("Cannot subscribe to {} on {}: {}", subscription, broker, e.getMessage());
                        failed(new IllegalStateException(
                                "Cannot subscribe to %s on %s: %s".formatted(subscription, broker, e.getMessage()), e));
                    });
        }
    }

    /**
     * Ends the subscription. Messages that arrive afterwards are dropped.
     *
     * @param atBroker whether to unsubscribe at the broker as well. Not for a session the broker keeps: it holds the
     *                 messages of the subscription until the client is back
     */
    void unsubscribe(final boolean atBroker) {
        final CompletableFuture<Void> done = new CompletableFuture<>();
        final Context running = context;
        if (running == null) {
            return;
        }
        running.runOnContext(v -> {
            final String filter = subscription;
            subscription = null;
            messageHandler = null;
            if (atBroker && filter != null && client != null && client.isConnected()) {
                client.unsubscribe(filter).onComplete(ar -> done.complete(null));
            } else {
                done.complete(null);
            }
        });
        try {
            done.get(10, TimeUnit.SECONDS);
        } catch (final Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.debug("Unsubscribing from {} did not complete", broker, e);
        }
    }

    /**
     * Stops reading from the connection while the consumer catches up, and goes on again.
     */
    void pause(final boolean pause) {
        run(() -> {
            if (client != null) {
                if (pause) {
                    client.pause();
                } else {
                    client.resume();
                }
            }
        });
    }

    /**
     * @return the client while the connection is started, to be used on its context only
     */
    MqttClient client() {
        return client;
    }

    boolean isConnected() {
        return connected;
    }

    /**
     * @return why the connection is down or the subscription failed, {@code null} while all is well or before the first
     *         attempt
     */
    Throwable failure() {
        return failure;
    }

    /**
     * @param listener told of a failure of the connection or the subscription, once while it fails the same way
     */
    void addFailureListener(final Consumer<Throwable> listener) {
        failureListeners.add(listener);
    }

    void removeFailureListener(final Consumer<Throwable> listener) {
        failureListeners.remove(listener);
    }

    private void failed(final Throwable cause) {
        final Throwable previous = failure;
        failure = cause;
        if (previous == null || !String.valueOf(previous.getMessage()).equals(cause.getMessage())) {
            failureListeners.forEach(listener -> listener.accept(cause));
        }
    }

    BrokerAddress broker() {
        return broker;
    }
}
