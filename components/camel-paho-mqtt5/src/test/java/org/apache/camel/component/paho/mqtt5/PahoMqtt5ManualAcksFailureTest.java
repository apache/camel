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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptionsBuilder;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * With manualAcksEnabled=true a message whose exchange failed is not acknowledged. The broker sends at most
 * receiveMaximum unacknowledged QoS 1/2 messages to a client, and sends an unacknowledged message again only when the
 * client connects again to the same session. These tests use a real broker (embedded Artemis, MQTT 5).
 */
class PahoMqtt5ManualAcksFailureTest {

    @RegisterExtension
    static ArtemisService service = ArtemisServiceFactory.createSingletonMQTTService();

    private final List<String> routed = new CopyOnWriteArrayList<>();
    private final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();
    private final Map<String, List<Long>> attemptTimes = new ConcurrentHashMap<>();

    @Test
    void failedMessagesDoNotStopTheConsumer() throws Exception {
        // clean session (the default) and a receive maximum of 2: after two failed exchanges the broker has two
        // unacknowledged messages in flight for this client
        String id = UUID.randomUUID().toString();
        String topic = "manualack/clean/" + id;
        String uri = "paho-mqtt5:" + topic + "?brokerUrl=" + brokerUrl()
                     + "&qos=1&manualAcksEnabled=true&receiveMaximum=2&clientId=manual-ack-clean-" + id;
        CamelContext context = createContext(uri, 1);
        try {
            publish(topic, "fail-1", "fail-2", "ok-3", "ok-4");

            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertThat(routed).containsExactly("ok-3", "ok-4"));
        } finally {
            context.stop();
        }
    }

    @Test
    void acknowledgedMessagesKeepTheReceiveWindowOpen() throws Exception {
        String id = UUID.randomUUID().toString();
        String topic = "manualack/ok/" + id;
        String uri = "paho-mqtt5:" + topic + "?brokerUrl=" + brokerUrl()
                     + "&qos=1&manualAcksEnabled=true&receiveMaximum=2&clientId=manual-ack-ok-" + id;
        CamelContext context = createContext(uri, 1);
        try {
            publish(topic, "ok-1", "ok-2", "ok-3", "ok-4");
            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertThat(routed).containsExactly("ok-1", "ok-2", "ok-3", "ok-4"));
        } finally {
            context.stop();
        }
    }

    @Test
    void failedMessageIsDeliveredAgainWithPersistentSession() throws Exception {
        // a session that the broker keeps: the documented rollback and retry of a failed message
        String id = UUID.randomUUID().toString();
        String topic = "manualack/persistent/" + id;
        String uri = "paho-mqtt5:" + topic + "?brokerUrl=" + brokerUrl()
                     + "&qos=1&manualAcksEnabled=true&cleanStart=false&sessionExpiryInterval=60"
                     + "&clientId=manual-ack-persistent-" + id;
        CamelContext context = createContext(uri, 1);
        try {
            publish(topic, "fail-1", "ok-2");

            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertThat(routed).containsExactlyInAnyOrder("fail-1", "ok-2"));
            assertThat(attempts.get("fail-1")).hasValue(2);
        } finally {
            context.stop();
        }
    }

    @Test
    void failedQos2MessageIsAcknowledgedWithoutReconnect() throws Exception {
        // Paho completes the QoS 2 flow (PUBREC, PUBCOMP) without waiting for the manual acknowledgement, so the broker
        // never sends a QoS 2 message again, even to a session that it keeps: a reconnect would not retry it
        String id = UUID.randomUUID().toString();
        String topic = "manualack/qos2/" + id;
        String uri = "paho-mqtt5:" + topic + "?brokerUrl=" + brokerUrl()
                     + "&qos=2&manualAcksEnabled=true&receiveMaximum=2&cleanStart=false&sessionExpiryInterval=60"
                     + "&clientId=manual-ack-qos2-" + id;
        CamelContext context = createContext(uri, 1);
        try {
            PahoMqtt5Consumer consumer = (PahoMqtt5Consumer) context.getRoutes().get(0).getConsumer();
            publish(topic, 2, false, "fail-1", "fail-2");
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                assertThat(attempts.get("fail-1")).hasValue(1);
                assertThat(attempts.get("fail-2")).hasValue(1);
            });
            await().during(1500, TimeUnit.MILLISECONDS).atMost(3, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertThat(consumer.getFailedMessageReconnects()).isZero());

            publish(topic, 2, false, "ok-3", "ok-4");
            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertThat(routed).containsExactly("ok-3", "ok-4"));
            assertThat(attempts.get("fail-1")).hasValue(1);
            assertThat(attempts.get("fail-2")).hasValue(1);
        } finally {
            context.stop();
        }
    }

    @Test
    void failedMessageIsAcknowledgedWhenTheSessionEndsWithTheConnection() throws Exception {
        // cleanStart=false without sessionExpiryInterval: the interval is not sent, which means 0, so the broker ends
        // the session when the client disconnects, and a reconnect would lose the messages instead of sending them again
        String id = UUID.randomUUID().toString();
        String topic = "manualack/noexpiry/" + id;
        String uri = "paho-mqtt5:" + topic + "?brokerUrl=" + brokerUrl()
                     + "&qos=1&manualAcksEnabled=true&receiveMaximum=1&cleanStart=false"
                     + "&clientId=manual-ack-noexpiry-" + id;
        CamelContext context = createContext(uri, 1);
        try {
            PahoMqtt5Consumer consumer = (PahoMqtt5Consumer) context.getRoutes().get(0).getConsumer();
            publish(topic, "fail-1");
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertThat(attempts.get("fail-1")).hasValue(1));
            await().during(1500, TimeUnit.MILLISECONDS).atMost(3, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertThat(consumer.getFailedMessageReconnects()).isZero());

            // with a receive maximum of 1 the broker sends the next message only after fail-1 is acknowledged
            publish(topic, "ok-2");
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertThat(routed).containsExactly("ok-2"));
            assertThat(attempts.get("fail-1")).hasValue(1);
        } finally {
            context.stop();
        }
    }

    @Test
    void reconnectToKeptSessionDoesNotSubscribeAgain() throws Exception {
        // the subscription is part of the session that the broker kept; subscribing again would make the broker send
        // the retained message of the topic again
        String id = UUID.randomUUID().toString();
        String topic = "manualack/retained/" + id;
        publish(topic, 1, true, "retained-0");
        String uri = "paho-mqtt5:" + topic + "?brokerUrl=" + brokerUrl()
                     + "&qos=1&manualAcksEnabled=true&cleanStart=false&sessionExpiryInterval=60"
                     + "&clientId=manual-ack-retained-" + id;
        CamelContext context = createContext(uri, 1);
        try {
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertThat(routed).containsExactly("retained-0"));

            publish(topic, "fail-1");
            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertThat(routed).containsExactly("retained-0", "fail-1"));
            assertThat(attempts.get("fail-1")).hasValue(2);
            await().during(1, TimeUnit.SECONDS).atMost(2, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertThat(attempts.get("retained-0")).hasValue(1));
        } finally {
            context.stop();
            publish(topic, 1, true);
        }
    }

    @Test
    void reconnectAfterAnEarlierConnectionLossDoesNotSubscribeAgain() throws Exception {
        // once the client lost a connection on its own, Paho reports every later connect to connectComplete as a
        // reconnect: the reconnect of a failed exchange must still not subscribe again to the session the broker kept
        String id = UUID.randomUUID().toString();
        String topic = "manualack/lost/" + id;
        String clientId = "manual-ack-lost-" + id;
        publish(topic, 1, true, "retained-0");
        String uri = "paho-mqtt5:" + topic + "?brokerUrl=" + brokerUrl()
                     + "&qos=1&manualAcksEnabled=true&cleanStart=false&sessionExpiryInterval=60&clientId=" + clientId;
        CamelContext context = createContext(uri, 1);
        try {
            PahoMqtt5Consumer consumer = (PahoMqtt5Consumer) context.getRoutes().get(0).getConsumer();
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertThat(routed).containsExactly("retained-0"));

            // another connection with the same client id takes the session over: the broker disconnects the consumer's
            // client, which reconnects on its own (automaticReconnect) once the session is free again
            MqttClient other = new MqttClient(brokerUrl(), clientId, new MemoryPersistence());
            try {
                other.connect(new MqttConnectionOptionsBuilder().cleanStart(false).sessionExpiryInterval(60L).build());
                other.disconnect();
            } finally {
                other.close();
            }
            await().atMost(10, TimeUnit.SECONDS).until(() -> consumer.getClient().isConnected());
            // the automatic reconnect of Paho subscribes again (Retain Handling 0), so the broker sends the retained
            // message again
            await().atMost(10, TimeUnit.SECONDS).until(() -> attempts.get("retained-0").get() == 2);
            int retainedBefore = attempts.get("retained-0").get();

            publish(topic, "fail-1");
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertThat(attempts.get("fail-1")).hasValue(2));
            await().during(1, TimeUnit.SECONDS).atMost(2, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertThat(attempts.get("retained-0")).hasValue(retainedBefore));
        } finally {
            context.stop();
            publish(topic, 1, true);
        }
    }

    @Test
    void reconnectsAfterFailuresInARowBackOff() throws Exception {
        // a message that fails three times in a row: the reconnects wait 1 s, then 2 s capped to maxReconnectDelay
        String id = UUID.randomUUID().toString();
        String topic = "manualack/backoff/" + id;
        String uri = "paho-mqtt5:" + topic + "?brokerUrl=" + brokerUrl()
                     + "&qos=1&manualAcksEnabled=true&cleanStart=false&sessionExpiryInterval=60&maxReconnectDelay=1500"
                     + "&clientId=manual-ack-backoff-" + id;
        CamelContext context = createContext(uri, 3);
        try {
            publish(topic, "fail-3");

            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> assertThat(routed).containsExactly("fail-3"));
            List<Long> times = attemptTimes.get("fail-3");
            assertThat(times).hasSize(4);
            assertThat(TimeUnit.NANOSECONDS.toMillis(times.get(1) - times.get(0))).isGreaterThanOrEqualTo(900);
            assertThat(TimeUnit.NANOSECONDS.toMillis(times.get(2) - times.get(1))).isGreaterThanOrEqualTo(1400);
            assertThat(TimeUnit.NANOSECONDS.toMillis(times.get(3) - times.get(2))).isGreaterThanOrEqualTo(1400);
            // the successful exchange resets the backoff
            PahoMqtt5Consumer consumer = (PahoMqtt5Consumer) context.getRoutes().get(0).getConsumer();
            await().atMost(5, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertThat(consumer.getFailedMessageReconnects()).isZero());
        } finally {
            context.stop();
        }
    }

    @Test
    void reconnectWaitsAtLeastOneSecondWithASmallMaxReconnectDelay() throws Exception {
        // maxReconnectDelay below the minimum delay must not make a failing message reconnect in a tight loop
        String id = UUID.randomUUID().toString();
        String topic = "manualack/smallmax/" + id;
        String uri = "paho-mqtt5:" + topic + "?brokerUrl=" + brokerUrl()
                     + "&qos=1&manualAcksEnabled=true&cleanStart=false&sessionExpiryInterval=60&maxReconnectDelay=0"
                     + "&clientId=manual-ack-smallmax-" + id;
        CamelContext context = createContext(uri, 2);
        try {
            publish(topic, "fail-small");

            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> assertThat(routed).containsExactly("fail-small"));
            List<Long> times = attemptTimes.get("fail-small");
            assertThat(times).hasSize(3);
            assertThat(TimeUnit.NANOSECONDS.toMillis(times.get(1) - times.get(0))).isGreaterThanOrEqualTo(900);
            assertThat(TimeUnit.NANOSECONDS.toMillis(times.get(2) - times.get(1))).isGreaterThanOrEqualTo(900);
        } finally {
            context.stop();
        }
    }

    private static String brokerUrl() {
        return "tcp://localhost:" + service.brokerPort();
    }

    /**
     * A route that fails the first {@code failures} attempts of every message whose body starts with "fail".
     */
    private CamelContext createContext(String uri, int failures) throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(uri).process(exchange -> {
                    String body = exchange.getMessage().getBody(String.class);
                    int attempt = attempts.computeIfAbsent(body, b -> new AtomicInteger()).incrementAndGet();
                    attemptTimes.computeIfAbsent(body, b -> new CopyOnWriteArrayList<>()).add(System.nanoTime());
                    if (body.startsWith("fail") && attempt <= failures) {
                        throw new IllegalStateException("Forced failure of " + body);
                    }
                    routed.add(body);
                });
            }
        });
        context.start();
        return context;
    }

    private static void publish(String topic, String... bodies) throws Exception {
        publish(topic, 1, false, bodies);
    }

    /**
     * Publishes the given bodies; a retained publish without bodies clears the retained message of the topic.
     */
    private static void publish(String topic, int qos, boolean retained, String... bodies) throws Exception {
        MqttClient publisher
                = new MqttClient(brokerUrl(), "manual-ack-publisher-" + UUID.randomUUID(), new MemoryPersistence());
        try {
            publisher.connect();
            if (retained && bodies.length == 0) {
                publisher.publish(topic, new byte[0], qos, true);
            }
            for (String body : bodies) {
                publisher.publish(topic, body.getBytes(StandardCharsets.UTF_8), qos, retained);
            }
            publisher.disconnect();
        } finally {
            publisher.close();
        }
    }
}
