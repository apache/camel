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
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VertxMqttConnectionTest {

    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final String FAST_RECONNECT = "&reconnectDelay=50&maxReconnectDelay=200";

    private Vertx vertx;
    private TestBroker broker;
    private CamelContext context;

    @BeforeEach
    void start() throws Exception {
        vertx = Vertx.vertx();
        broker = TestBroker.start(vertx);
        context = new DefaultCamelContext();
        context.getRegistry().bind("vertx", vertx);
        context.start();
    }

    @AfterEach
    void stop() throws Exception {
        context.stop();
        broker.close();
        vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    private VertxMqttEndpoint endpoint(String options) {
        return context.getEndpoint("vertx-mqtt:a?brokerUrl=" + broker.url() + options, VertxMqttEndpoint.class);
    }

    @Test
    void connectsAndDisconnectsWithItsUsers() throws Exception {
        final VertxMqttEndpoint endpoint = endpoint("");
        final VertxMqttConnection first = endpoint.acquireConnection();
        final VertxMqttConnection second = endpoint.acquireConnection();
        assertSame(first, second);
        await().atMost(WAIT).until(() -> first.isConnected() && broker.connectedClients() == 1);

        endpoint.releaseConnection();
        assertTrue(first.isConnected());
        endpoint.releaseConnection();
        assertFalse(first.isConnected());
        await().atMost(WAIT).until(() -> broker.connectedClients() == 0);
        assertEquals(1, broker.connects());
    }

    @Test
    void reconnectsAfterTheBrokerRestarted() throws Exception {
        final VertxMqttConnection connection = endpoint(FAST_RECONNECT).acquireConnection();
        await().atMost(WAIT).until(connection::isConnected);

        broker.stop();
        await().atMost(WAIT).until(() -> !connection.isConnected());
        broker.restart();
        await().atMost(WAIT).until(() -> connection.isConnected() && broker.connects() == 2);
    }

    @Test
    void reconnectsAfterTheConnectionDropped() throws Exception {
        final VertxMqttConnection connection = endpoint(FAST_RECONNECT).acquireConnection();
        await().atMost(WAIT).until(connection::isConnected);

        broker.dropConnections();
        await().atMost(WAIT).until(() -> broker.connects() == 2 && connection.isConnected());
    }

    @Test
    void keepsTryingWhileTheBrokerRefuses() throws Exception {
        broker.reject(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED);
        final VertxMqttConnection connection = endpoint(FAST_RECONNECT).acquireConnection();
        await().atMost(WAIT).until(() -> broker.connects() >= 3);
        assertFalse(connection.isConnected());

        broker.reject(null);
        await().atMost(WAIT).until(connection::isConnected);
    }

    @Test
    void withoutAutomaticReconnectAFailedConnectFailsTheStart() throws Exception {
        broker.stop();
        final VertxMqttEndpoint endpoint = endpoint("&automaticReconnect=false&connectTimeout=2000");
        assertThrows(IllegalStateException.class, endpoint::acquireConnection);
    }

    @Test
    void aStoppedConnectionNoLongerReconnects() throws Exception {
        broker.stop();
        final VertxMqttEndpoint endpoint = endpoint(FAST_RECONNECT);
        endpoint.acquireConnection();
        endpoint.releaseConnection();

        broker.restart();
        await().during(Duration.ofMillis(500)).atMost(WAIT).until(() -> broker.connects() == 0);
    }

    @Test
    void waitingForTheConnectionEndsWithTheConnectTimeout() throws Exception {
        broker.stop();
        final VertxMqttConnection connection = endpoint(FAST_RECONNECT + "&connectTimeout=300").acquireConnection();
        final ExecutionException e = assertThrows(ExecutionException.class,
                () -> connection.whenConnected().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS));
        assertTrue(e.getCause().getMessage().contains("connect timeout"), e.getCause().getMessage());

        broker.restart();
        assertTrue(connection.whenConnected().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS)
                .isConnected());
    }

    private static void get(Future<?> future) throws Exception {
        future.toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    /**
     * Without a clientId the identifier is random, but the same for every reconnect of a connection: a client per
     * attempt would otherwise come back with another one and leave the session the broker keeps behind.
     */
    @Test
    void aReconnectKeepsTheGeneratedClientIdentifier() throws Exception {
        final VertxMqttConnection connection = endpoint(FAST_RECONNECT).acquireConnection();
        await().atMost(WAIT).until(connection::isConnected);
        broker.dropConnections();
        await().atMost(WAIT).until(() -> broker.connects() == 2 && connection.isConnected());

        final List<String> clientIds = broker.clientIds();
        assertFalse(clientIds.get(0).isEmpty());
        assertEquals(clientIds.get(0), clientIds.get(1));
    }

    /**
     * Vert.x keeps the in-flight slots of a client across a lost connection: a client reused for the next connection
     * would refuse every message of quality of service 1 and 2 once maxInflightQueue messages were lost, and with
     * ackTimeout=0 for good.
     */
    @Test
    void theInFlightSlotsOfALostConnectionAreFreeAgain() throws Exception {
        final VertxMqttConnection connection
                = endpoint(FAST_RECONNECT + "&maxInflightQueue=10&ackTimeout=0").acquireConnection();
        await().atMost(WAIT).until(connection::isConnected);
        broker.acknowledgePublications(false);
        final List<Future<Void>> lost = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            lost.add(connection.publish("a", Buffer.buffer("lost"), MqttQoS.AT_LEAST_ONCE, false, null));
        }
        await().atMost(WAIT).until(() -> broker.packetIdsByConnection().stream().mapToInt(List::size).sum() == 10);

        broker.acknowledgePublications(true);
        broker.dropConnections();
        await().atMost(WAIT).until(() -> lost.stream().allMatch(Future::failed));
        await().atMost(WAIT).until(() -> broker.connects() == 2 && connection.isConnected());

        get(connection.publish("a", Buffer.buffer("after"), MqttQoS.AT_LEAST_ONCE, false, null));
    }

    /**
     * A stop while the CONNECT waits for its CONNACK: the client must not stay connected at the broker, and nothing may
     * fail on the event loop.
     */
    @Test
    void stoppingWhileConnectingLeavesNoConnectionAndNoError() throws Exception {
        final List<Throwable> errors = new CopyOnWriteArrayList<>();
        vertx.exceptionHandler(errors::add);
        broker.holdConnects(true);
        final VertxMqttEndpoint endpoint = endpoint(FAST_RECONNECT);
        endpoint.acquireConnection();
        await().atMost(WAIT).until(() -> broker.heldConnects() == 1);

        endpoint.releaseConnection();
        broker.releaseConnects();
        await().during(Duration.ofMillis(500)).atMost(WAIT).until(() -> broker.connectedClients() == 0);
        assertEquals(List.of(), errors);
    }

    /**
     * The consumer pauses the client while it catches up. The pause has to hold for the client of the next connection
     * too, or the consumer runs over its bound after every reconnect.
     */
    @Test
    void aPauseHoldsForTheConnectionAfterAReconnect() throws Exception {
        final VertxMqttConnection connection = endpoint(FAST_RECONNECT).acquireConnection();
        final Queue<String> received = new ConcurrentLinkedQueue<>();
        connection.subscribe("a", 1, (message, receiver) -> received.add(message.payload().toString()));
        await().atMost(WAIT).until(() -> broker.hasSubscription("a"));
        connection.pause(true);

        broker.dropConnections();
        await().atMost(WAIT).until(() -> broker.connects() == 2 && broker.hasSubscription("a"));
        broker.send("a", "paused".getBytes(), 1, false, null);
        await().during(Duration.ofMillis(500)).atMost(WAIT).until(received::isEmpty);

        connection.pause(false);
        await().atMost(WAIT).until(() -> received.size() == 1);
    }
}
