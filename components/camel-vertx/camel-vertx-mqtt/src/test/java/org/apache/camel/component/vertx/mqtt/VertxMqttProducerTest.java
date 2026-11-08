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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import io.netty.handler.codec.mqtt.MqttProperties;
import io.vertx.core.Vertx;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VertxMqttProducerTest {

    private static final Duration WAIT = Duration.ofSeconds(10);

    private Vertx vertx;
    private TestBroker broker;
    private CamelContext context;
    private ProducerTemplate template;

    @BeforeEach
    void start() throws Exception {
        vertx = Vertx.vertx();
        broker = TestBroker.start(vertx);
        context = new DefaultCamelContext();
        context.getRegistry().bind("vertx", vertx);
        context.start();
        template = context.createProducerTemplate();
    }

    @AfterEach
    void stop() throws Exception {
        template.close();
        context.stop();
        broker.close();
        vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    private String uri(String topic, String options) {
        return "vertx-mqtt:" + topic + "?brokerUrl=" + broker.url() + options;
    }

    @ParameterizedTest
    @CsvSource({ "MQTT_3_1_1, 0", "MQTT_3_1_1, 1", "MQTT_3_1_1, 2", "MQTT_5_0, 0", "MQTT_5_0, 1", "MQTT_5_0, 2" })
    void publishes(String version, int qos) {
        final Exchange done = template.send(uri("sensors/1/temp", "&mqttVersion=" + version + "&qos=" + qos),
                exchange -> exchange.getMessage().setBody("21.5"));
        assertNull(done.getException());
        await().atMost(WAIT).until(() -> !broker.received().isEmpty());
        final TestBroker.Received received = broker.received().peek();
        assertEquals("sensors/1/temp", received.topic());
        assertEquals("21.5", new String(received.payload(), StandardCharsets.UTF_8));
        assertEquals(qos, received.qos());
        assertFalse(received.retain());
    }

    @Test
    void headersOverrideTopicQosAndRetained() {
        template.send(uri("a", ""), exchange -> {
            exchange.getMessage().setHeader(VertxMqttConstants.OVERRIDE_TOPIC, "b/c");
            exchange.getMessage().setHeader(VertxMqttConstants.OVERRIDE_QOS, 2);
            exchange.getMessage().setHeader(VertxMqttConstants.OVERRIDE_RETAINED, true);
            exchange.getMessage().setBody(new byte[] { 1, 2, 3 });
        });
        await().atMost(WAIT).until(() -> !broker.received().isEmpty());
        final TestBroker.Received received = broker.received().peek();
        assertEquals("b/c", received.topic());
        assertEquals(2, received.qos());
        assertTrue(received.retain());
        assertArrayEquals(new byte[] { 1, 2, 3 }, received.payload());
    }

    @Test
    void noBodyIsAnEmptyPayload() {
        assertNull(template.send(uri("a", ""), exchange -> exchange.getMessage().setBody(null)).getException());
        await().atMost(WAIT).until(() -> !broker.received().isEmpty());
        assertEquals(0, broker.received().peek().payload().length);
    }

    @Test
    void mqtt5PropertiesFromTheHeaders() {
        template.send(uri("a", ""), exchange -> {
            exchange.getMessage().setHeader(VertxMqttConstants.USER_PROPERTIES, Map.of("origin", "test"));
            exchange.getMessage().setHeader(VertxMqttConstants.CONTENT_TYPE, "application/json");
            exchange.getMessage().setHeader(VertxMqttConstants.RESPONSE_TOPIC, "replies");
            exchange.getMessage().setHeader(VertxMqttConstants.CORRELATION_DATA, new byte[] { 7 });
            exchange.getMessage().setHeader(VertxMqttConstants.MESSAGE_EXPIRY_INTERVAL, 60L);
            exchange.getMessage().setBody("{}");
        });
        await().atMost(WAIT).until(() -> !broker.received().isEmpty());
        final DefaultExchange read = new DefaultExchange(context);
        VertxMqttProperties.toHeaders(broker.received().peek().properties(), read.getMessage());
        assertEquals(Map.of("origin", "test"), read.getMessage().getHeader(VertxMqttConstants.USER_PROPERTIES));
        assertEquals("application/json", read.getMessage().getHeader(VertxMqttConstants.CONTENT_TYPE));
        assertEquals("replies", read.getMessage().getHeader(VertxMqttConstants.RESPONSE_TOPIC));
        assertArrayEquals(new byte[] { 7 },
                read.getMessage().getHeader(VertxMqttConstants.CORRELATION_DATA, byte[].class));
        assertEquals(60L, read.getMessage().getHeader(VertxMqttConstants.MESSAGE_EXPIRY_INTERVAL));
    }

    @Test
    void mqtt3SendsNoProperties() {
        template.send(uri("a", "&mqttVersion=MQTT_3_1_1"), exchange -> {
            exchange.getMessage().setHeader(VertxMqttConstants.CONTENT_TYPE, "application/json");
            exchange.getMessage().setBody("{}");
        });
        await().atMost(WAIT).until(() -> !broker.received().isEmpty());
        final MqttProperties properties = broker.received().peek().properties();
        assertTrue(properties == null || properties.isEmpty());
    }

    @Test
    void aWildcardTopicIsRefused() {
        for (final String topic : new String[] { "a/+/b", "a/#" }) {
            final Exchange done = template.send(uri("a", ""),
                    exchange -> exchange.getMessage().setHeader(VertxMqttConstants.OVERRIDE_TOPIC, topic));
            assertInstanceOf(IllegalArgumentException.class, done.getException(), topic);
        }
        assertTrue(broker.received().isEmpty());
    }

    @Test
    void anUnacknowledgedMessageFailsAfterTheAckTimeout() {
        broker.acknowledgePublications(false);
        final Exchange done = template.send(uri("a", "&ackTimeout=1"), exchange -> exchange.getMessage().setBody("x"));
        assertInstanceOf(IllegalStateException.class, done.getException());
        assertTrue(done.getException().getMessage().contains("ack timeout"), done.getException().getMessage());
    }

    @Test
    void aFullInflightQueueFailsTheMessage() throws Exception {
        broker.acknowledgePublications(false);
        final String uri = uri("a", "&maxInflightQueue=1&ackTimeout=30");
        final CompletableFuture<Exchange> first = template.asyncSend(uri, exchange -> exchange.getMessage().setBody("1"));
        await().atMost(WAIT).until(() -> broker.packetIdsByConnection().size() == 1);
        final Exchange second = template.send(uri, exchange -> exchange.getMessage().setBody("2"));
        assertTrue(second.getException() != null, "the second message is beyond the inflight queue");
        assertFalse(first.isDone());
    }

    @Test
    void aLostConnectionFailsTheUnacknowledgedMessage() throws Exception {
        broker.acknowledgePublications(false);
        final CompletableFuture<Exchange> sent = template.asyncSend(uri("a", "&ackTimeout=30"),
                exchange -> exchange.getMessage().setBody("x"));
        await().atMost(WAIT).until(() -> broker.packetIdsByConnection().size() == 1);
        broker.dropConnections();
        final Exchange done = sent.get(10, TimeUnit.SECONDS);
        assertInstanceOf(IllegalStateException.class, done.getException());
        assertTrue(done.getException().getMessage().contains("lost"), done.getException().getMessage());
    }

    /**
     * The rest of the route must not run on the event loop that carries the connection: a blocking step would block its
     * keep alive, and that of every other connection on the loop.
     */
    @Test
    void completesTheExchangeOffTheEventLoop() throws Exception {
        final List<String> threads = new CopyOnWriteArrayList<>();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:in").to(uri("a", "&qos=1")).process(exchange -> threads.add(Thread.currentThread().getName()));
            }
        });
        for (int i = 0; i < 5; i++) {
            assertNull(template.send("direct:in", exchange -> exchange.getMessage().setBody("x")).getException());
        }
        assertEquals(5, threads.size());
        assertTrue(threads.stream().noneMatch(name -> name.startsWith("vert.x-eventloop")), threads.toString());
    }

    @Test
    void waitsForTheFirstConnection() throws Exception {
        broker.stop();
        final CompletableFuture<Exchange> sent = template.asyncSend(
                uri("a", "&reconnectDelay=50&maxReconnectDelay=100&connectTimeout=10000"),
                exchange -> exchange.getMessage().setBody("late"));
        await().during(Duration.ofMillis(300)).atMost(WAIT).until(() -> !sent.isDone());
        broker.restart();
        assertNull(sent.get(10, TimeUnit.SECONDS).getException());
        await().atMost(WAIT).until(() -> !broker.received().isEmpty());
    }
}
