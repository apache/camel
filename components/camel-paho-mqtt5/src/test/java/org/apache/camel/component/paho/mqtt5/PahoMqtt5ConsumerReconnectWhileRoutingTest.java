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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTUtil;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttClientException;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * manualAcksEnabled on a session that the broker keeps, with a synchronous route: the exchange runs on the callback
 * thread of Paho. A failed message makes the consumer reconnect while the next message is still in the route. Paho
 * 1.2.5 starts the threads of a connection again only once the threads of the previous connection of the same client
 * have ended, and waits for them forever otherwise, so a connect of the same client hung there (CAMEL-27043). A real
 * Paho client against the embedded Artemis MQTT broker.
 */
@Timeout(60)
class PahoMqtt5ConsumerReconnectWhileRoutingTest {

    @RegisterExtension
    static ArtemisService service = ArtemisServiceFactory.createSingletonMQTTService();

    private final List<String> routed = new CopyOnWriteArrayList<>();
    private final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();
    private final CountDownLatch slowInRoute = new CountDownLatch(1);
    private final CountDownLatch releaseSlow = new CountDownLatch(1);

    @Test
    void reconnectWhileAMessageIsInTheRouteRoutesTheNextMessages() throws Exception {
        String id = UUID.randomUUID().toString();
        String clientId = "reconnect-routing-" + id;
        String topic = "reconnect/routing/" + id;
        String uri = "paho-mqtt5:" + topic + "?brokerUrl=" + brokerUrl()
                     + "&qos=1&manualAcksEnabled=true&cleanStart=false&sessionExpiryInterval=60&clientId=" + clientId;
        CamelContext context = createContext(uri);
        try {
            PahoMqtt5Consumer consumer = (PahoMqtt5Consumer) context.getRoutes().get(0).getConsumer();
            MqttClient first = consumer.getClient();

            // fail-1 fails, so the consumer reconnects after 1 second, while slow-2 is still in the route
            publish(topic, "fail-1", "slow-2");
            assertThat(slowInRoute.await(10, TimeUnit.SECONDS)).isTrue();

            // slow-2 stays in the route: the consumer connects again all the same, the broker sends fail-1 and slow-2
            // again, and the consumer receives new messages
            await().atMost(15, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertThat(routed).contains("fail-1", "slow-2"));
            publish(topic, "after-3");
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertThat(routed).contains("after-3"));
            assertThat(consumer.getClient().isConnected()).isTrue();
            // the client of the earlier connection is closed, so it cannot connect later and take the session over
            assertThat(consumer.getClient()).isNotSameAs(first);
            assertThatThrownBy(first::connect).isInstanceOfSatisfying(MqttException.class,
                    e -> assertThat(e.getReasonCode()).isEqualTo(MqttClientException.REASON_CODE_CLIENT_CLOSED));

            // the first delivery of slow-2 leaves the route: it is not acknowledged (its connection is closed), and the
            // consumer stays connected
            releaseSlow.countDown();
            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertThat(routed).containsExactlyInAnyOrder("fail-1", "slow-2", "after-3",
                            "slow-2"));
            assertThat(attempts.get("fail-1")).hasValue(2);
            assertThat(attempts.get("after-3")).hasValue(1);
            assertThat(consumer.getClient().isConnected()).isTrue();
            // nothing is left unacknowledged at the broker
            String queue = MQTTUtil.getCoreQueueFromMqttTopic(topic, clientId, new WildcardConfiguration());
            await().atMost(10, TimeUnit.SECONDS).until(() -> service.countMessages(queue) == 0);
            assertThat(consumer.getFailedMessageReconnects()).isZero();
        } finally {
            releaseSlow.countDown();
            context.stop();
        }
    }

    private CamelContext createContext(String uri) throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(uri).process(exchange -> {
                    String body = exchange.getMessage().getBody(String.class);
                    int attempt = attempts.computeIfAbsent(body, b -> new AtomicInteger()).incrementAndGet();
                    if (body.startsWith("fail") && attempt == 1) {
                        throw new IllegalStateException("Forced failure of " + body);
                    }
                    if (body.startsWith("slow") && attempt == 1) {
                        // a route step that takes longer than the reconnect delay (1 second), such as a slow call
                        slowInRoute.countDown();
                        releaseSlow.await(30, TimeUnit.SECONDS);
                    }
                    routed.add(body);
                });
            }
        });
        context.start();
        return context;
    }

    private static void publish(String topic, String... bodies) throws Exception {
        MqttClient publisher
                = new MqttClient(brokerUrl(), "reconnect-routing-publisher-" + UUID.randomUUID(), new MemoryPersistence());
        try {
            publisher.connect();
            for (String body : bodies) {
                publisher.publish(topic, body.getBytes(StandardCharsets.UTF_8), 1, false);
            }
            publisher.disconnect();
        } finally {
            publisher.close();
        }
    }

    private static String brokerUrl() {
        return "tcp://localhost:" + service.brokerPort();
    }
}
