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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.AsyncResult;
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
 * The connection of an endpoint to its broker, shared by its consumer and producer, connected again by a
 * {@link VertxMqttReconnector} when the connection is lost and {@code automaticReconnect} is on.
 * <p>
 * Every connection attempt gets a client of its own. Vert.x keeps the in-flight bookkeeping of a client across a lost
 * connection (the slots of unacknowledged messages, the inbound state of quality of service 2, the topic aliases), so a
 * client reused for the next connection would carry the leftovers of the last one.
 * <p>
 * Every call of a client runs on one Vert.x context, the one the connection started on, so the state needs no locks,
 * packets leave in the order they were published, and an acknowledgement can never overtake the bookkeeping of the
 * message it acknowledges. The current client is held in a field until the connection stops: Vert.x 5 closes a client
 * that is no longer reachable, open connection included.
 */
final class VertxMqttConnection {

    /**
     * Receives the messages of the subscription.
     */
    interface MessageHandler {

        /**
         * On the context of the connection.
         *
         * @param receiver the client the message arrived on, which an acknowledgement has to go to: a later client
         *                 holds another session state and possibly the same packet identifier for another message
         */
        void handle(MqttPublishMessage message, MqttClient receiver);
    }

    private static final Logger LOG = LoggerFactory.getLogger(VertxMqttConnection.class);
    private static final Pattern IP_ADDRESS = Pattern.compile("[0-9.]+|.*:.*");

    private final Vertx vertx;
    private final BrokerAddress broker;
    private final MqttClientOptions options;
    private final VertxMqttConfiguration configuration;

    private Context context;
    private volatile boolean started;
    private volatile boolean stopping;
    private volatile boolean connected;
    // Why the connection is down or the subscription failed, null while all is well or before the first attempt
    private volatile Throwable failure;
    // Told of a failure once, not again for every attempt that fails the same way
    private final List<Consumer<Throwable>> failureListeners = new CopyOnWriteArrayList<>();

    // Below on the context only
    // The client of the current attempt, null once stopped
    private MqttClient client;
    private boolean connecting;
    // Whether this start connected before: a reconnect to a session the broker kept does not subscribe again
    private boolean reconnect;
    // The connection was closed on purpose, so that the broker delivers an unacknowledged message again
    private boolean redelivering;
    // Set after a reconnect for a redelivery: the reconnect delay keeps growing until an exchange succeeds, so that a
    // message that fails every time does not reconnect once a second forever
    private volatile boolean backingOff;
    // Whether the consumer wants the client to stop reading, applied to every new client as well
    private boolean paused;
    // Null without automaticReconnect
    private VertxMqttReconnector reconnector;
    private final List<Promise<MqttClient>> waiting = new ArrayList<>();
    private String subscription;
    private int subscriptionQos;
    private MessageHandler messageHandler;
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
        // A client copies the options and generates a random identifier on its copy: with a client per attempt every
        // reconnect would come with another identifier and leave the session the broker keeps behind
        if (options.isAutoGeneratedClientId() && (options.getClientId() == null || options.getClientId().isEmpty())) {
            if (!configuration.isCleanStart()) {
                LOG.warn("The connection to the MQTT broker {} keeps its session (cleanStart=false) without a clientId:"
                         + " every restart starts a new session and leaves the last one behind at the broker",
                        broker);
            }
            options.setClientId(UUID.randomUUID().toString());
            options.setAutoGeneratedClientId(false);
        }
        context = vertx.getOrCreateContext();
        reconnector = configuration.isAutomaticReconnect()
                ? new VertxMqttReconnector(
                        vertx, broker, configuration.getReconnectDelay(), configuration.getMaxReconnectDelay(),
                        () -> connect(null))
                : null;
        final CompletableFuture<Void> first = new CompletableFuture<>();
        context.runOnContext(v -> connect(first));
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
     * Disconnects and stops reconnecting, also while an attempt is under way. Waits for the disconnect for a while,
     * never fails.
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
            failWaiting(stopped);
            failUnacknowledged(stopped);
            final MqttClient current = client;
            if (current == null) {
                done.complete(null);
                return;
            }
            // Answers at once when closed. While connecting the client closes the socket itself once the CONNACK
            // arrives, so no connection is left at the broker
            current.disconnect().onComplete(ar -> {
                if (client == current) {
                    client = null;
                }
                done.complete(null);
            });
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
    }

    private void connect(final CompletableFuture<Void> first) {
        if (stopping) {
            if (first != null) {
                first.completeExceptionally(new IllegalStateException("The connection to %s stopped".formatted(broker)));
            }
            return;
        }
        connecting = true;
        final MqttClient attempt = MqttClient.create(vertx, options);
        client = attempt;
        attempt.closeHandler(closed -> closed(attempt));
        attempt.exceptionHandler(e -> LOG.debug("Error on the connection to {}", broker, e));
        attempt.publishCompletionHandler(packetId -> {
            if (attempt == client) {
                acknowledged(packetId);
            }
        });
        attempt.publishCompletionExpirationHandler(packetId -> {
            if (attempt == client) {
                unacknowledged(packetId,
                        "The MQTT broker %s did not acknowledge the message within the ack timeout of %s seconds"
                                .formatted(broker, configuration.getAckTimeout()));
            }
        });
        attempt.publishCompletionUnknownPacketIdHandler(
                packetId -> LOG.debug("Acknowledgement of the unknown packet {} from {}", packetId, broker));
        attempt.publishHandler(message -> {
            final MessageHandler handler = messageHandler;
            if (handler != null) {
                handler.handle(message, attempt);
            } else {
                LOG.debug("Message on {} from {} without a subscriber", message.topicName(), broker);
            }
        });
        attempt.subscribeCompletionHandler(ack -> {
            if (ack.grantedQoSLevels().stream().anyMatch(granted -> granted == null || granted > 2)) {
                report(new IllegalStateException(
                        "The MQTT broker %s refused the subscription to %s".formatted(broker, subscription)));
            }
        });
        final String serverName = broker.ssl() && !IP_ADDRESS.matcher(broker.host()).matches() ? broker.host() : null;
        attempt.connect(broker.port(), broker.host(), serverName).onComplete(ar -> {
            if (attempt != client) {
                attempt.disconnect();
                return;
            }
            connecting = false;
            if (ar.succeeded()) {
                if (stopping) {
                    attempt.disconnect();
                    return;
                }
                connected = true;
                failure = null;
                final boolean afterRedelivery = redelivering;
                redelivering = false;
                backingOff = afterRedelivery;
                if (reconnector != null && !afterRedelivery) {
                    reconnector.succeeded();
                }
                LOG.info("Connected to the MQTT broker {}", broker);
                if (paused) {
                    attempt.pause();
                }
                // A session the broker kept still holds the subscription, subscribing again would replay the retained
                // messages on every reconnect
                if (!reconnect || !ar.result().isSessionPresent()) {
                    subscribe();
                }
                reconnect = true;
                final List<Promise<MqttClient>> released = new ArrayList<>(waiting);
                waiting.clear();
                released.forEach(promise -> promise.tryComplete(attempt));
                if (first != null) {
                    first.complete(null);
                }
            } else if (stopping) {
                LOG.debug("Connecting to {} ended with the stop: {}", broker, ar.cause().getMessage());
                if (first != null) {
                    first.completeExceptionally(ar.cause());
                }
            } else {
                report(new IllegalStateException(
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

    private void closed(final MqttClient closed) {
        if (closed != client) {
            return;
        }
        connected = false;
        // The client keeps no session of its own, a message sent before the loss is not sent again
        failUnacknowledged(new IllegalStateException(
                "The connection to the MQTT broker %s was lost before the message was acknowledged".formatted(broker)));
        // A failed connect closes the connection too, and reconnects from its own completion
        if (stopping || connecting) {
            return;
        }
        if (redelivering) {
            LOG.debug("Reconnecting to the MQTT broker {} for the messages it delivers again", broker);
            // With MQTT 5 the broker ends the session sessionExpiryInterval seconds after the close: the reconnect has
            // to come well before, or the message and everything queued meanwhile are gone
            reconnector.schedule(configuration.getMqttVersion() == VertxMqttVersion.MQTT_5_0
                    ? configuration.getSessionExpiryInterval() * 1000 / 2 : 0);
            return;
        }
        final IllegalStateException lost
                = new IllegalStateException("The connection to the MQTT broker %s was lost".formatted(broker));
        report(lost);
        if (reconnector != null) {
            reconnector.schedule();
        } else {
            failWaiting(lost);
        }
    }

    private void failWaiting(final Throwable cause) {
        final List<Promise<MqttClient>> failing = new ArrayList<>(waiting);
        waiting.clear();
        failing.forEach(promise -> promise.tryFail(cause));
    }

    /**
     * On the context. Hands the client to the handler once it is connected, on the context, or fails if it does not
     * connect within the connect timeout.
     */
    private void awaitConnected(final Handler<AsyncResult<MqttClient>> handler) {
        final Promise<MqttClient> promise = Promise.promise();
        // Attached before the promise can complete, and every completion happens on the context
        promise.future().onComplete(handler);
        if (stopping) {
            promise.tryFail(new IllegalStateException("The connection to %s stopped".formatted(broker)));
        } else if (connected && client != null && client.isConnected()) {
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
    }

    /**
     * @return the client once it is connected, failed if it does not connect within the connect timeout
     */
    Future<MqttClient> whenConnected() {
        final Promise<MqttClient> promise = Promise.promise();
        if (!run(() -> awaitConnected(ar -> complete(promise, ar)))) {
            return Future.failedFuture(new IllegalStateException("The connection to %s is not started".formatted(broker)));
        }
        return promise.future();
    }

    private static <T> void complete(final Promise<T> promise, final AsyncResult<T> result) {
        if (result.succeeded()) {
            promise.tryComplete(result.result());
        } else {
            promise.tryFail(result.cause());
        }
    }

    /**
     * Publishes a message once the client is connected. The whole publish runs on the context: the client assigns
     * packet identifiers and topic aliases under its lock but writes outside it, so publishes from more than one thread
     * could leave in another order than their identifiers.
     *
     * @param  properties the MQTT 5 properties, null for MQTT 3.1.1
     * @return            completed when the broker acknowledged a message of quality of service 1 or 2, when it is
     *                    written for 0
     */
    Future<Void> publish(
            final String topic, final Buffer payload, final MqttQoS qos, final boolean retain,
            final MqttProperties properties) {
        final Promise<Void> result = Promise.promise();
        final boolean running = run(() -> awaitConnected(ar -> {
            if (ar.failed()) {
                result.tryFail(ar.cause());
                return;
            }
            final MqttClient connectedClient = ar.result();
            final Future<Integer> sent = properties == null
                    ? connectedClient.publish(topic, payload, qos, false, retain)
                    : connectedClient.publish(topic, payload, qos, false, retain, properties);
            // Completes on the context once written, before the event loop can read the acknowledgement
            sent.onComplete(written -> {
                if (written.failed()) {
                    result.tryFail(written.cause());
                } else if (qos == MqttQoS.AT_MOST_ONCE) {
                    result.tryComplete();
                } else if (connectedClient != client || !connected) {
                    result.tryFail(new IllegalStateException(
                            "The connection to the MQTT broker %s was lost before the message was acknowledged"
                                    .formatted(broker)));
                } else {
                    unacknowledged.put(written.result(), result);
                }
            });
        }));
        if (!running) {
            return Future.failedFuture(new IllegalStateException("The connection to %s is not started".formatted(broker)));
        }
        return result.future();
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
        final List<Promise<Void>> failing = new ArrayList<>(unacknowledged.values());
        unacknowledged.clear();
        failing.forEach(acknowledgement -> acknowledgement.tryFail(cause));
    }

    /**
     * Runs a task on the context of the connection.
     *
     * @return whether the connection was started, false if the task does not run
     */
    boolean run(final Runnable task) {
        final Context running = context;
        if (running == null) {
            return false;
        }
        running.runOnContext(v -> task.run());
        return true;
    }

    /**
     * Subscribes now if connected, and again after every reconnect to a new session.
     *
     * @param handler called on the context for every message the broker sends
     */
    void subscribe(final String filter, final int qos, final MessageHandler handler) {
        run(() -> {
            subscription = filter;
            subscriptionQos = qos;
            messageHandler = handler;
            subscribe();
        });
    }

    private void subscribe() {
        if (subscription != null && connected && client != null && client.isConnected()) {
            client.subscribe(subscription, subscriptionQos)
                    .onFailure(e -> report(new IllegalStateException(
                            "Cannot subscribe to %s on %s: %s".formatted(subscription, broker, e.getMessage()), e)));
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
        final boolean running = run(() -> {
            final String filter = subscription;
            subscription = null;
            messageHandler = null;
            if (atBroker && filter != null && connected && client != null && client.isConnected()) {
                client.unsubscribe(filter).onComplete(ar -> done.complete(null));
            } else {
                done.complete(null);
            }
        });
        if (!running) {
            return;
        }
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
     * Stops reading from the connection while the consumer catches up, and goes on again. Applied to the clients of
     * later connections too.
     */
    void pause(final boolean pause) {
        run(() -> {
            paused = pause;
            if (connected && client != null && client.isConnected()) {
                if (pause) {
                    client.pause();
                } else {
                    client.resume();
                }
            }
        });
    }

    /**
     * Acknowledges a message the consumer received with manual acknowledgements, unless it arrived on an earlier
     * connection: its packet identifier then means nothing, or another message, to the current session.
     */
    void acknowledge(final MqttPublishMessage message, final MqttClient receiver) {
        run(() -> {
            if (receiver == client && connected) {
                message.ack();
            } else {
                LOG.debug("Not acknowledging the message {} of an earlier connection to {}", message.messageId(), broker);
            }
        });
    }

    /**
     * Closes the connection without acknowledging a message, so that the broker delivers it again after the reconnect,
     * from the session it keeps. Needs automatic reconnect, does nothing without it.
     */
    void redeliver(final MqttClient receiver) {
        run(() -> {
            if (receiver == client && connected && reconnector != null && !redelivering) {
                redelivering = true;
                LOG.warn("Closing the connection to the MQTT broker {}, so that it delivers the messages whose exchange"
                         + " failed again. A message that fails every time comes back after every reconnect",
                        broker);
                receiver.disconnect();
            }
        });
    }

    /**
     * An exchange succeeded: after a reconnect for a redelivery, the reconnect delay starts small again.
     */
    void processed() {
        if (backingOff) {
            run(() -> {
                backingOff = false;
                if (reconnector != null) {
                    reconnector.succeeded();
                }
            });
        }
    }

    boolean isConnected() {
        return connected;
    }

    /**
     * @return whether the broker delivers an unacknowledged message again after a reconnect: with automatic reconnect,
     *         and a session that outlives the connection, which with MQTT 5 needs a session expiry interval
     */
    boolean canRedeliver() {
        return configuration.isAutomaticReconnect() && !configuration.isCleanStart()
                && (configuration.getMqttVersion() == VertxMqttVersion.MQTT_3_1_1
                        || configuration.getSessionExpiryInterval() > 0);
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

    /**
     * Records a failure and reports it once while it fails the same way: to the listeners, the exception handler of a
     * consumer, or else to the log. Not both, a listener logs it itself.
     */
    private void report(final Throwable cause) {
        final Throwable previous = failure;
        failure = cause;
        if (previous != null && String.valueOf(previous.getMessage()).equals(cause.getMessage())) {
            LOG.debug("{}", cause.getMessage());
        } else if (failureListeners.isEmpty()) {
            LOG.warn("{}", cause.getMessage());
        } else {
            failureListeners.forEach(listener -> listener.accept(cause));
        }
    }

    BrokerAddress broker() {
        return broker;
    }
}
