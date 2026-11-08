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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttProperties.StringProperty;
import io.netty.handler.codec.mqtt.MqttProperties.UserProperty;
import io.vertx.core.Vertx;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckAware;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.ExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VertxMqttConsumerTest {

    private static final Duration WAIT = Duration.ofSeconds(10);

    private Vertx vertx;
    private TestBroker broker;
    private CamelContext context;
    private final Queue<Message> received = new ConcurrentLinkedQueue<>();

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

    private void consume(String filter, String options) throws Exception {
        consume(filter, options, false);
    }

    private void consume(String filter, String options, boolean fail) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("vertx-mqtt:" + filter + "?brokerUrl=" + broker.url() + options).routeId("consumer")
                        .process(exchange -> {
                            received.add(exchange.getMessage().copy());
                            if (fail) {
                                throw new IllegalStateException("marker failure");
                            }
                        });
            }
        });
        await().atMost(WAIT).until(() -> broker.hasSubscription(filter));
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @CsvSource({ "MQTT_3_1_1, 0", "MQTT_3_1_1, 1", "MQTT_3_1_1, 2", "MQTT_5_0, 0", "MQTT_5_0, 1", "MQTT_5_0, 2" })
    void receives(String version, int qos) throws Exception {
        consume("sensors/1/temp", "&mqttVersion=" + version + "&qos=" + qos);
        broker.send("sensors/1/temp", bytes("21.5"), qos, false, null);

        await().atMost(WAIT).until(() -> !received.isEmpty());
        final Message message = received.peek();
        assertArrayEquals(bytes("21.5"), message.getBody(byte[].class));
        assertEquals("sensors/1/temp", message.getHeader(VertxMqttConstants.TOPIC));
        assertEquals(qos, message.getHeader(VertxMqttConstants.QOS));
        assertEquals(Boolean.FALSE, message.getHeader(VertxMqttConstants.DUPLICATE));
        assertEquals(qos > 0, message.getHeader(VertxMqttConstants.MESSAGE_ID) != null);
    }

    @Test
    void theFilterDecides() throws Exception {
        consume("sensors/+/temp", "");
        broker.send("sensors/1/humidity", bytes("no"), 1, false, null);
        broker.send("sensors/1/temp", bytes("yes"), 1, true, null);

        await().during(Duration.ofMillis(200)).atMost(WAIT).until(() -> received.size() == 1);
        assertEquals("sensors/1/temp", received.peek().getHeader(VertxMqttConstants.TOPIC));
        assertEquals(Boolean.TRUE, received.peek().getHeader(VertxMqttConstants.RETAINED));
    }

    @Test
    void mqtt5PropertiesBecomeHeaders() throws Exception {
        consume("a", "");
        final MqttProperties properties = new MqttProperties();
        properties.add(new UserProperty("origin", "test"));
        properties.add(new StringProperty(MqttProperties.CONTENT_TYPE, "text/plain"));
        broker.send("a", bytes("x"), 1, false, properties);

        await().atMost(WAIT).until(() -> !received.isEmpty());
        assertEquals(List.of(Map.entry("origin", "test")), received.peek().getHeader(VertxMqttConstants.USER_PROPERTIES));
        assertEquals("text/plain", received.peek().getHeader(VertxMqttConstants.CONTENT_TYPE));
    }

    /**
     * Without the maxMessageSize of the component the Vert.x client falls back to the 8092 bytes of the Netty decoder
     * and closes the connection on a larger incoming message.
     */
    @Test
    void receivesAMessageLargerThan8092Bytes() throws Exception {
        consume("a", "");
        final byte[] large = new byte[64 * 1024];
        for (int i = 0; i < large.length; i++) {
            large[i] = (byte) i;
        }
        broker.send("a", large, 1, false, null);

        await().atMost(WAIT).until(() -> !received.isEmpty());
        assertArrayEquals(large, received.peek().getBody(byte[].class));
    }

    @ParameterizedTest
    @CsvSource({ "1", "2" })
    void manualAcknowledgementOfASuccessfulExchange(int qos) throws Exception {
        consume("a", "&manualAcksEnabled=true&qos=" + qos);
        broker.send("a", bytes("succeeds"), qos, false, null);
        await().atMost(WAIT).until(() -> !broker.acknowledged().isEmpty());
    }

    /**
     * Left unacknowledged, a failed message would hold one of the in-flight slots of the broker until the next connect,
     * and the consumer would stall after receiveMaximum failures. Without a session to deliver it from again, or at
     * quality of service 2, whose PUBREC went out on arrival, the broker never delivers it again anyway.
     */
    @ParameterizedTest
    @CsvSource({ "1, true", "2, true", "2, false" })
    void aFailedExchangeIsAcknowledgedWhenTheBrokerWouldNotDeliverItAgain(int qos, boolean cleanStart) throws Exception {
        consume("a", "&manualAcksEnabled=true&clientId=manual&qos=" + qos + "&cleanStart=" + cleanStart, true);
        broker.send("a", bytes("fails"), qos, false, null);
        await().atMost(WAIT).until(() -> !broker.acknowledged().isEmpty());
        assertEquals(1, broker.connects());
    }

    /**
     * At quality of service 1 with a session the broker keeps, the consumer reconnects instead of acknowledging, so
     * that the broker delivers the message again.
     */
    @Test
    void aFailedExchangeOfAKeptSessionIsLeftForTheBrokerToDeliverAgain() throws Exception {
        consume("a", "&manualAcksEnabled=true&clientId=manual&qos=1&cleanStart=false&reconnectDelay=50", true);
        broker.send("a", bytes("fails"), 1, false, null);
        await().atMost(WAIT).until(() -> broker.connects() == 2 && broker.connectedClients() == 1);
        assertTrue(broker.acknowledged().isEmpty(), broker.acknowledged().toString());
    }

    @Test
    void automaticAcknowledgementAlsoForAFailedExchange() throws Exception {
        consume("a", "&qos=1", true);
        broker.send("a", bytes("fails"), 1, false, null);
        await().atMost(WAIT).until(() -> !broker.acknowledged().isEmpty());
    }

    @Test
    void subscribesAgainAfterAReconnect() throws Exception {
        consume("a", "&reconnectDelay=50&maxReconnectDelay=200");
        broker.stop();
        broker.restart();
        await().atMost(WAIT).until(() -> broker.hasSubscription("a"));

        broker.send("a", bytes("after"), 1, false, null);
        await().atMost(WAIT).until(() -> !received.isEmpty());
        assertArrayEquals(bytes("after"), received.peek().getBody(byte[].class));
    }

    @Test
    void aReconnectToASessionTheBrokerKeptDoesNotSubscribeAgain() throws Exception {
        consume("a", "&cleanStart=false&clientId=kept&reconnectDelay=50&maxReconnectDelay=200");
        assertEquals(1, broker.subscribes());
        broker.sessionPresent(true);

        broker.dropConnections();
        await().atMost(WAIT).until(() -> broker.connects() == 2 && broker.connectedClients() == 1);
        // A kept session still holds the subscription: subscribing again would replay its retained messages
        await().during(Duration.ofMillis(300)).atMost(WAIT).until(() -> broker.subscribes() == 1);
    }

    /**
     * The queue of the consumer is bounded by the pause of the client only. With a bounded queue that runs the task on
     * the caller once full, a burst beyond its size would run the route on the event loop of the client.
     */
    @Test
    void neverProcessesOnTheEventLoopOfTheClient() throws Exception {
        final Set<String> threads = ConcurrentHashMap.newKeySet();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("vertx-mqtt:a?brokerUrl=" + broker.url() + "&maxPendingMessages=1500&qos=0")
                        .process(exchange -> threads.add(Thread.currentThread().getName()))
                        .delay(1).syncDelayed()
                        .process(exchange -> received.add(exchange.getMessage()));
            }
        });
        await().atMost(WAIT).until(() -> broker.hasSubscription("a"));
        for (int i = 0; i < 1300; i++) {
            broker.send("a", bytes(Integer.toString(i)), 0, false, null);
        }
        await().atMost(Duration.ofSeconds(60)).until(() -> received.size() == 1300);
        assertTrue(threads.stream().noneMatch(name -> name.startsWith("vert.x-eventloop")), threads.toString());
    }

    /**
     * Every message the client acknowledged when it arrived has to be processed before the consumer stops: the broker
     * will not deliver it again.
     */
    @Test
    void stoppingProcessesEveryMessageItAcknowledged() throws Exception {
        final Set<Integer> processed = ConcurrentHashMap.newKeySet();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("vertx-mqtt:a?brokerUrl=" + broker.url() + "&qos=1").routeId("consumer")
                        .delay(1).syncDelayed()
                        .process(exchange -> processed
                                .add(exchange.getMessage().getHeader(VertxMqttConstants.MESSAGE_ID, Integer.class)));
            }
        });
        await().atMost(WAIT).until(() -> broker.hasSubscription("a"));
        for (int i = 0; i < 1000; i++) {
            broker.send("a", bytes(Integer.toString(i)), 1, false, null);
        }
        await().atMost(WAIT).until(() -> broker.acknowledged().size() >= 100);

        context.getRouteController().stopRoute("consumer");
        final List<Integer> acknowledged = List.copyOf(broker.acknowledged());
        final List<Integer> lost = acknowledged.stream().filter(id -> !processed.contains(id)).toList();
        assertEquals(List.of(), lost, "acknowledged, but not processed");
    }

    @Test
    void stoppingTheRouteEndsTheSubscriptionAndTheConnection() throws Exception {
        consume("a", "");
        await().atMost(WAIT).until(() -> broker.connectedClients() == 1);
        context.getRouteController().stopRoute("consumer");
        await().atMost(WAIT).until(() -> broker.connectedClients() == 0);
        assertFalse(broker.hasSubscription("a"));
    }

    @Test
    void keepsTheOrderWhilePaused() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("vertx-mqtt:a?brokerUrl=" + broker.url() + "&maxPendingMessages=2&qos=0")
                        .delay(5).syncDelayed()
                        .process(exchange -> received.add(exchange.getMessage().copy()));
            }
        });
        await().atMost(WAIT).until(() -> broker.hasSubscription("a"));
        final List<String> sent = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            sent.add(Integer.toString(i));
            broker.send("a", bytes(Integer.toString(i)), 0, false, null);
        }
        await().atMost(WAIT).until(() -> received.size() == 50);
        assertEquals(sent, received.stream().map(message -> message.getBody(String.class)).toList());
        assertNull(received.peek().getHeader(VertxMqttConstants.MESSAGE_ID));
    }

    @Test
    void reportsWhyItCannotConnectUntilItDoes() throws Exception {
        broker.reject(MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED);
        final List<Throwable> handled = new CopyOnWriteArrayList<>();
        context.getRegistry().bind("handler", new ExceptionHandler() {
            @Override
            public void handleException(Throwable exception) {
                handled.add(exception);
            }

            @Override
            public void handleException(String message, Throwable exception) {
                handled.add(exception);
            }

            @Override
            public void handleException(String message, Exchange exchange, Throwable exception) {
                handled.add(exception);
            }
        });
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("vertx-mqtt:a?brokerUrl=" + broker.url() + "&reconnectDelay=50&exceptionHandler=#handler")
                        .routeId("consumer").process(exchange -> received.add(exchange.getMessage().copy()));
            }
        });
        final HealthCheck check = ((HealthCheckAware) context.getRoute("consumer").getConsumer()).getHealthCheck();

        await().atMost(WAIT).untilAsserted(() -> {
            final HealthCheck.Result result = check.call();
            assertEquals(HealthCheck.State.DOWN, result.getState());
            assertTrue(result.getMessage().orElse("").contains("Cannot connect to the MQTT broker"),
                    result.getMessage().orElse(""));
        });
        // Every attempt fails the same way, the handler hears of it once
        await().during(Duration.ofMillis(300)).atMost(WAIT).until(() -> handled.size() == 1);

        broker.reject(null);
        await().atMost(WAIT).until(() -> check.call().getState() == HealthCheck.State.UP);
        assertTrue(broker.hasSubscription("a"));
    }
}
