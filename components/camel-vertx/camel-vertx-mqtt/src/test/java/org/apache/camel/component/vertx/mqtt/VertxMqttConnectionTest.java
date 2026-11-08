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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.vertx.core.Vertx;
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
        VertxMqttEndpoint endpoint = endpoint("");
        VertxMqttConnection first = endpoint.acquireConnection();
        VertxMqttConnection second = endpoint.acquireConnection();
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
        VertxMqttConnection connection = endpoint(FAST_RECONNECT).acquireConnection();
        await().atMost(WAIT).until(connection::isConnected);

        broker.stop();
        await().atMost(WAIT).until(() -> !connection.isConnected());
        broker.restart();
        await().atMost(WAIT).until(() -> connection.isConnected() && broker.connects() == 2);
    }

    @Test
    void reconnectsAfterTheConnectionDropped() throws Exception {
        VertxMqttConnection connection = endpoint(FAST_RECONNECT).acquireConnection();
        await().atMost(WAIT).until(connection::isConnected);

        broker.dropConnections();
        await().atMost(WAIT).until(() -> broker.connects() == 2 && connection.isConnected());
    }

    @Test
    void keepsTryingWhileTheBrokerRefuses() throws Exception {
        broker.reject(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED);
        VertxMqttConnection connection = endpoint(FAST_RECONNECT).acquireConnection();
        await().atMost(WAIT).until(() -> broker.connects() >= 3);
        assertFalse(connection.isConnected());

        broker.reject(null);
        await().atMost(WAIT).until(connection::isConnected);
    }

    @Test
    void withoutAutomaticReconnectAFailedConnectFailsTheStart() throws Exception {
        broker.stop();
        VertxMqttEndpoint endpoint = endpoint("&automaticReconnect=false&connectTimeout=2000");
        assertThrows(IllegalStateException.class, endpoint::acquireConnection);
    }

    @Test
    void aStoppedConnectionNoLongerReconnects() throws Exception {
        broker.stop();
        VertxMqttEndpoint endpoint = endpoint(FAST_RECONNECT);
        endpoint.acquireConnection();
        endpoint.releaseConnection();

        broker.restart();
        Thread.sleep(500);
        assertEquals(0, broker.connects());
    }

    @Test
    void waitingForTheConnectionEndsWithTheConnectTimeout() throws Exception {
        broker.stop();
        VertxMqttConnection connection = endpoint(FAST_RECONNECT + "&connectTimeout=300").acquireConnection();
        ExecutionException e = assertThrows(ExecutionException.class,
                () -> connection.whenConnected().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS));
        assertTrue(e.getCause().getMessage().contains("connect timeout"), e.getCause().getMessage());

        broker.restart();
        assertTrue(connection.whenConnected().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS)
                .isConnected());
    }
}
