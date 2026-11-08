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
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Vertx;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The component against a real broker of test-infra. Every test uses topics of its own, so that tests do not see each
 * other's messages.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractBrokerIT {

    private static final Duration WAIT = Duration.ofSeconds(20);
    private static final String PROBE = "subscription-probe";

    private Vertx vertx;
    private CamelContext context;
    private ProducerTemplate template;
    private final Queue<Message> received = new ConcurrentLinkedQueue<>();
    private String prefix;
    private volatile boolean probeSeen;

    /**
     * @return the URL of the started broker, as tcp://host:port
     */
    abstract String brokerUrl();

    @BeforeAll
    void createVertx() {
        vertx = Vertx.vertx();
    }

    @AfterAll
    void closeVertx() throws Exception {
        if (vertx != null) {
            vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        }
    }

    @BeforeEach
    void start() {
        prefix = "it/" + UUID.randomUUID() + "/";
        received.clear();
        context = new DefaultCamelContext();
        context.getRegistry().bind("vertx", vertx);
        context.start();
        template = context.createProducerTemplate();
    }

    @AfterEach
    void stop() throws Exception {
        template.close();
        context.stop();
    }

    private String uri(String topic, String options) {
        return "vertx-mqtt:" + prefix + topic + "?brokerUrl=" + brokerUrl() + options;
    }

    private void consume(String routeId, String topic, String options) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(uri(topic, options)).routeId(routeId).process(exchange -> {
                    if (PROBE.equals(exchange.getMessage().getBody(String.class))) {
                        probeSeen = true;
                    } else {
                        received.add(exchange.getMessage().copy());
                    }
                });
            }
        });
    }

    private Exchange publish(String topic, String options, Object body, Map<String, Object> headers) {
        return template.send(uri(topic, options), exchange -> {
            exchange.getMessage().setBody(body);
            exchange.getMessage().getHeaders().putAll(headers);
        });
    }

    /**
     * The test cannot see the SUBACK of the consumer, so it publishes probes on the filter until one arrives. The
     * consumer drops probes, a late one does not count as a message of the test.
     */
    private void awaitSubscribed(String topic, String options) {
        String probe = topic.replace("+", "probe").replace("#", "probe");
        probeSeen = false;
        await().atMost(WAIT).pollInterval(Duration.ofMillis(200)).until(() -> {
            publish(probe, options, PROBE, Map.of());
            return probeSeen;
        });
    }

    @ParameterizedTest
    @CsvSource({ "MQTT_3_1_1, 0", "MQTT_3_1_1, 1", "MQTT_3_1_1, 2", "MQTT_5_0, 0", "MQTT_5_0, 1", "MQTT_5_0, 2" })
    void roundTrip(String version, int qos) throws Exception {
        String options = "&mqttVersion=" + version + "&qos=" + qos;
        consume("consumer", "sensors/+/temp", options);
        awaitSubscribed("sensors/+/temp", options);

        Exchange done = publish("sensors/1/temp", options, "21.5", Map.of());
        assertNull(done.getException());
        await().atMost(WAIT).until(() -> !received.isEmpty());
        Message message = received.peek();
        assertEquals("21.5", message.getBody(String.class));
        assertEquals(prefix + "sensors/1/temp", message.getHeader(VertxMqttConstants.TOPIC));
        assertEquals(qos, message.getHeader(VertxMqttConstants.QOS));
    }

    @Test
    void aRetainedMessageReachesALaterSubscriber() throws Exception {
        assertNull(publish("status", "&retained=true", "online", Map.of()).getException());
        consume("consumer", "status", "");

        await().atMost(WAIT).until(() -> !received.isEmpty());
        assertEquals("online", received.peek().getBody(String.class));
        assertEquals(Boolean.TRUE, received.peek().getHeader(VertxMqttConstants.RETAINED));
    }

    @Test
    void mqtt5PropertiesTravelThroughTheBroker() throws Exception {
        consume("consumer", "props", "");
        awaitSubscribed("props", "");

        publish("props", "", "{}", Map.of(VertxMqttConstants.USER_PROPERTIES, Map.of("origin", "it"),
                VertxMqttConstants.CONTENT_TYPE, "application/json", VertxMqttConstants.RESPONSE_TOPIC,
                prefix + "replies", VertxMqttConstants.CORRELATION_DATA, new byte[] { 4, 2 }));
        await().atMost(WAIT).until(() -> !received.isEmpty());
        Message message = received.peek();
        assertEquals(Map.of("origin", "it"), message.getHeader(VertxMqttConstants.USER_PROPERTIES));
        assertEquals("application/json", message.getHeader(VertxMqttConstants.CONTENT_TYPE));
        assertEquals(prefix + "replies", message.getHeader(VertxMqttConstants.RESPONSE_TOPIC));
        assertArrayEquals(new byte[] { 4, 2 }, message.getHeader(VertxMqttConstants.CORRELATION_DATA, byte[].class));
    }

    @Test
    void aLargeMessage() throws Exception {
        consume("consumer", "large", "");
        awaitSubscribed("large", "");
        byte[] large = new byte[512 * 1024];
        for (int i = 0; i < large.length; i++) {
            large[i] = (byte) (i * 31);
        }
        assertNull(publish("large", "", large, Map.of()).getException());
        await().atMost(WAIT).until(() -> !received.isEmpty());
        assertArrayEquals(large, received.peek().getBody(byte[].class));
    }

    /**
     * A session the broker keeps: messages published while the consumer is away arrive when it connects again.
     */
    @ParameterizedTest
    @CsvSource({ "MQTT_3_1_1, ''", "MQTT_5_0, &sessionExpiryInterval=300" })
    void aKeptSessionReceivesWhatWasPublishedMeanwhile(String version, String sessionOptions) throws Exception {
        String options = "&mqttVersion=" + version + "&qos=1&cleanStart=false&clientId=it-" + UUID.randomUUID()
                         + sessionOptions;
        consume("consumer", "session", options);
        awaitSubscribed("session", "&mqttVersion=" + version + "&qos=1");
        context.getRouteController().stopRoute("consumer");

        assertNull(publish("session", "&qos=1", "while away", Map.of()).getException());
        context.getRouteController().startRoute("consumer");
        await().atMost(WAIT).until(() -> !received.isEmpty());
        assertEquals("while away", received.peek().getBody(String.class));
    }
}
