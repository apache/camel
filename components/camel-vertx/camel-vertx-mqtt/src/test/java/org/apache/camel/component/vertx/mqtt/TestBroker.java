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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.TrustOptions;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;
import io.vertx.mqtt.MqttTopicSubscription;
import io.vertx.mqtt.messages.MqttPublishMessage;

import static org.awaitility.Awaitility.await;

/**
 * A small MQTT broker for the tests, on the Vert.x MQTT server: it accepts every client, routes publications to the
 * matching subscriptions and records what it sees. No retained messages, no sessions.
 * <p>
 * Its port comes from the range 20000 to 32000, below the ephemeral ports of Linux, macOS and Windows: a client that
 * reconnects while the broker is down could otherwise get the very port as its local port and connect to itself.
 */
final class TestBroker implements AutoCloseable {

    private static final int FIRST_PORT = 20_000;
    private static final int LAST_PORT = 32_000;

    /**
     * A message the broker received from a client.
     */
    record Received(String clientId, String topic, byte[] payload, int qos, boolean retain, MqttProperties properties) {
    }

    private final Vertx vertx;
    private final MqttServerOptions options;
    private MqttServer server;
    private int port;

    private final Map<MqttEndpoint, Map<String, Integer>> subscriptions = new ConcurrentHashMap<>();
    private final Queue<Received> received = new ConcurrentLinkedQueue<>();
    private final Queue<Integer> acknowledged = new ConcurrentLinkedQueue<>();
    private final List<MqttEndpoint> endpoints = new CopyOnWriteArrayList<>();
    private final AtomicInteger connects = new AtomicInteger();
    private final AtomicInteger packetIds = new AtomicInteger(1);
    private volatile MqttEndpoint lastConnect;
    private volatile boolean acknowledgePublications = true;
    private volatile MqttConnectReturnCode reject;
    private volatile boolean holdConnects;
    private volatile boolean sessionPresent;
    private final Queue<MqttEndpoint> heldConnects = new ConcurrentLinkedQueue<>();
    private final AtomicInteger subscribes = new AtomicInteger();
    // The packet identifiers of the publications of quality of service 1 and 2, per connection in the order they arrived
    private final Map<MqttEndpoint, List<Integer>> packetIdsByConnection = new ConcurrentHashMap<>();

    private TestBroker(Vertx vertx, MqttServerOptions options) {
        this.vertx = vertx;
        this.options = options;
    }

    static TestBroker start(Vertx vertx) throws Exception {
        return start(vertx, null);
    }

    /**
     * @param keyCert the certificate of the broker, for TLS, or null for a plain broker
     */
    static TestBroker start(Vertx vertx, KeyCertOptions keyCert) throws Exception {
        return start(vertx, keyCert, null);
    }

    /**
     * @param clientTrust the certificates of clients the broker requires a client certificate of, null for none
     */
    static TestBroker start(Vertx vertx, KeyCertOptions keyCert, TrustOptions clientTrust) throws Exception {
        final MqttServerOptions options = new MqttServerOptions().setHost("127.0.0.1")
                .setMaxMessageSize(VertxMqttConstants.MAX_MESSAGE_SIZE);
        if (keyCert != null) {
            options.setSsl(true).setKeyCertOptions(keyCert);
        }
        if (clientTrust != null) {
            options.setTrustOptions(clientTrust).setClientAuth(ClientAuth.REQUIRED);
        }
        final TestBroker broker = new TestBroker(vertx, options);
        Exception last = null;
        for (int attempt = 0; attempt < 20; attempt++) {
            final int candidate = ThreadLocalRandom.current().nextInt(FIRST_PORT, LAST_PORT);
            try {
                broker.listen(candidate);
                return broker;
            } catch (Exception e) {
                last = e;
            }
        }
        throw last;
    }

    private void listen(int candidate) throws Exception {
        final MqttServer created = MqttServer.create(vertx, new MqttServerOptions(options).setPort(candidate));
        created.endpointHandler(this::accept);
        created.listen().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        server = created;
        port = candidate;
    }

    /**
     * Stops the broker, closing every connection.
     */
    void stop() throws Exception {
        if (server != null) {
            server.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
            server = null;
        }
        endpoints.clear();
        subscriptions.clear();
    }

    /**
     * Starts the stopped broker again on its port, retrying while the port is still taken.
     */
    void restart() {
        await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(100)).ignoreExceptions().until(() -> {
            listen(port);
            return true;
        });
    }

    @Override
    public void close() throws Exception {
        stop();
    }

    private void accept(MqttEndpoint endpoint) {
        connects.incrementAndGet();
        lastConnect = endpoint;
        final MqttConnectReturnCode refusal = reject;
        if (refusal != null) {
            endpoint.reject(refusal);
            return;
        }
        endpoints.add(endpoint);
        endpoint.publishAutoAck(false);
        endpoint.subscribeHandler(subscribe -> {
            subscribes.incrementAndGet();
            final Map<String, Integer> filters = subscriptions.computeIfAbsent(endpoint, e -> new ConcurrentHashMap<>());
            final List<MqttQoS> granted = new CopyOnWriteArrayList<>();
            for (final MqttTopicSubscription subscription : subscribe.topicSubscriptions()) {
                filters.put(subscription.topicName(), subscription.qualityOfService().value());
                granted.add(subscription.qualityOfService());
            }
            endpoint.subscribeAcknowledge(subscribe.messageId(), granted);
        });
        endpoint.unsubscribeHandler(unsubscribe -> {
            final Map<String, Integer> filters = subscriptions.get(endpoint);
            if (filters != null) {
                unsubscribe.topics().forEach(filters::remove);
            }
            endpoint.unsubscribeAcknowledge(unsubscribe.messageId());
        });
        endpoint.publishHandler(message -> publication(endpoint, message));
        endpoint.publishReleaseHandler(endpoint::publishComplete);
        // What the broker sent, acknowledged by the client
        endpoint.publishAcknowledgeHandler(acknowledged::add);
        endpoint.publishReceivedHandler(endpoint::publishRelease);
        endpoint.publishCompletionHandler(acknowledged::add);
        endpoint.closeHandler(v -> {
            endpoints.remove(endpoint);
            subscriptions.remove(endpoint);
        });
        endpoint.disconnectHandler(v -> endpoints.remove(endpoint));
        if (holdConnects) {
            heldConnects.add(endpoint);
        } else {
            endpoint.accept(sessionPresent);
        }
    }

    private void publication(MqttEndpoint endpoint, MqttPublishMessage message) {
        if (message.qosLevel() != MqttQoS.AT_MOST_ONCE) {
            packetIdsByConnection.computeIfAbsent(endpoint, e -> new CopyOnWriteArrayList<>()).add(message.messageId());
        }
        if (!acknowledgePublications) {
            return;
        }
        received.add(new Received(
                endpoint.clientIdentifier(), message.topicName(), message.payload().getBytes(),
                message.qosLevel().value(), message.isRetain(), message.properties()));
        switch (message.qosLevel()) {
            case AT_LEAST_ONCE -> endpoint.publishAcknowledge(message.messageId());
            case EXACTLY_ONCE -> endpoint.publishReceived(message.messageId());
            default -> {
            }
        }
        route(message.topicName(), message.payload(), message.qosLevel().value(), message.isRetain(),
                message.properties());
    }

    /**
     * Publishes a message to every client subscribed to a matching filter, as if another client had sent it.
     */
    void send(String topic, byte[] payload, int qos, boolean retain, MqttProperties properties) {
        vertx.runOnContext(v -> route(topic, Buffer.buffer(payload), qos, retain, properties));
    }

    private void route(String topic, Buffer payload, int qos, boolean retain, MqttProperties properties) {
        subscriptions.forEach((subscriber, filters) -> filters.forEach((filter, granted) -> {
            if (matches(filter, topic) && subscriber.isConnected()) {
                final int effective = Math.min(qos, granted);
                // A packet identifier for quality of service 1 and 2 is 1 to 65535, 0 for 0
                final int packetId = effective == 0 ? 0 : packetIds.getAndUpdate(id -> id >= 65_535 ? 1 : id + 1);
                subscriber.publish(topic, payload, MqttQoS.valueOf(effective), false, retain, packetId,
                        properties == null ? MqttProperties.NO_PROPERTIES : properties);
            }
        }));
    }

    /**
     * @return whether an MQTT topic filter, with + and #, matches a topic
     */
    static boolean matches(String filter, String topic) {
        final String[] f = filter.split("/", -1);
        final String[] t = topic.split("/", -1);
        for (int i = 0; i < f.length; i++) {
            if ("#".equals(f[i])) {
                return true;
            }
            if (i >= t.length || !"+".equals(f[i]) && !f[i].equals(t[i])) {
                return false;
            }
        }
        return f.length == t.length;
    }

    /**
     * @param acknowledge false to swallow every publication of a client without acknowledging or routing it
     */
    void acknowledgePublications(boolean acknowledge) {
        this.acknowledgePublications = acknowledge;
    }

    /**
     * @param code the return code to refuse every connection with, null to accept them again
     */
    void reject(MqttConnectReturnCode code) {
        this.reject = code;
    }

    /**
     * Closes every connection without stopping the broker, as a network failure would.
     */
    void dropConnections() {
        vertx.runOnContext(v -> endpoints.forEach(MqttEndpoint::close));
    }

    int port() {
        return port;
    }

    String url() {
        return (options.isSsl() ? "ssl" : "tcp") + "://127.0.0.1:" + port;
    }

    int connects() {
        return connects.get();
    }

    MqttEndpoint lastConnect() {
        return lastConnect;
    }

    int connectedClients() {
        return (int) endpoints.stream().filter(MqttEndpoint::isConnected).count();
    }

    boolean hasSubscription(String filter) {
        return subscriptions.values().stream().anyMatch(filters -> filters.containsKey(filter));
    }

    Queue<Received> received() {
        return received;
    }

    Queue<Integer> acknowledged() {
        return acknowledged;
    }

    /**
     * @param hold true to keep the CONNACK of every new connection back until {@link #releaseConnects()}
     */
    void holdConnects(boolean hold) {
        this.holdConnects = hold;
    }

    int heldConnects() {
        return heldConnects.size();
    }

    /**
     * Sends the CONNACK of every connection held back, to the clients that are still there.
     */
    void releaseConnects() {
        holdConnects = false;
        vertx.runOnContext(v -> {
            MqttEndpoint held;
            while ((held = heldConnects.poll()) != null) {
                // Not connected before its CONNACK, but still in the list unless its socket closed
                if (endpoints.contains(held)) {
                    held.accept(sessionPresent);
                }
            }
        });
    }

    /**
     * @param present whether the CONNACK of later connections says that the broker kept the session of the client
     */
    void sessionPresent(boolean present) {
        this.sessionPresent = present;
    }

    /**
     * @return how many SUBSCRIBE packets the broker received, over all connections
     */
    int subscribes() {
        return subscribes.get();
    }

    /**
     * @return the packet identifiers of the publications of quality of service 1 and 2, one list per connection in the
     *         order they arrived
     */
    List<List<Integer>> packetIdsByConnection() {
        return List.copyOf(packetIdsByConnection.values());
    }
}
