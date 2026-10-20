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
package org.apache.camel.component.paho;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Consumers of different topics that use the same client (the client option) each receive the messages of their own
 * topic.
 */
class PahoSharedClientConsumersTest {

    @RegisterExtension
    static ArtemisService service = ArtemisServiceFactory.createSingletonMQTTService();

    private MqttClient shared;
    private MqttClient publisher;
    private CamelContext context;

    @BeforeEach
    void setUp() throws Exception {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        shared = new MqttClient(brokerUrl(), "shared-" + System.nanoTime(), new MemoryPersistence());
        shared.connect(options);
        publisher = new MqttClient(brokerUrl(), "publisher-" + System.nanoTime(), new MemoryPersistence());
        publisher.connect(options);

        context = new DefaultCamelContext();
        context.getRegistry().bind("sharedClient", shared);
    }

    /**
     * Replaces the shared client by one with manual acks: the client sends no PUBACK for a QoS 1 message until it is
     * acknowledged.
     *
     * @return the client id of the new shared client
     */
    private String useSharedClientWithManualAcks() throws Exception {
        shared.disconnect();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        String clientId = "shared-manual-" + System.nanoTime();
        shared = new MqttClient(brokerUrl(), clientId, new MemoryPersistence());
        shared.setManualAcks(true);
        shared.connect(options);
        context.getRegistry().bind("sharedClient", shared);
        return clientId;
    }

    private void startRoutes(String uriA, String uriB) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(uriA).routeId("a").to("mock:a");
                from(uriB).routeId("b").to("mock:b");
            }
        });
        context.start();
    }

    private void startRoutesOfTopicsAAndB() throws Exception {
        startRoutes("paho:sharedA?client=#sharedClient", "paho:sharedB?client=#sharedClient");
    }

    private static String brokerUrl() {
        // the local address, as the address of the network interface may not be reachable from here
        return "tcp://localhost:" + service.brokerPort();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (context != null) {
            context.stop();
        }
        if (publisher != null && publisher.isConnected()) {
            publisher.disconnect();
        }
        if (shared != null && shared.isConnected()) {
            shared.disconnect();
        }
    }

    @Test
    void eachConsumerReceivesItsOwnTopic() throws Exception {
        startRoutesOfTopicsAAndB();
        MockEndpoint a = context.getEndpoint("mock:a", MockEndpoint.class);
        MockEndpoint b = context.getEndpoint("mock:b", MockEndpoint.class);
        a.expectedBodiesReceived("for A");
        b.expectedBodiesReceived("for B");

        publisher.publish("sharedA", new MqttMessage("for A".getBytes(StandardCharsets.UTF_8)));
        publisher.publish("sharedB", new MqttMessage("for B".getBytes(StandardCharsets.UTF_8)));

        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
    }

    @Test
    void stoppedConsumerNoLongerReceivesAndOtherConsumerKeepsItsTopic() throws Exception {
        startRoutesOfTopicsAAndB();
        context.getRouteController().stopRoute("b");

        MockEndpoint a = context.getEndpoint("mock:a", MockEndpoint.class);
        MockEndpoint b = context.getEndpoint("mock:b", MockEndpoint.class);
        a.expectedBodiesReceived("for A");
        b.expectedMessageCount(0);

        publisher.publish("sharedB", new MqttMessage("for stopped B".getBytes(StandardCharsets.UTF_8)));
        publisher.publish("sharedA", new MqttMessage("for A".getBytes(StandardCharsets.UTF_8)));

        a.setResultWaitTime(10000);

        a.assertIsSatisfied();
        b.assertIsSatisfied(1000);
    }

    @Test
    void restartedConsumerKeepsUsingTheSharedClient() throws Exception {
        startRoutesOfTopicsAAndB();
        context.getRouteController().stopRoute("a");
        context.getRouteController().startRoute("a");

        MockEndpoint a = context.getEndpoint("mock:a", MockEndpoint.class);
        MockEndpoint b = context.getEndpoint("mock:b", MockEndpoint.class);
        a.expectedBodiesReceived("for A");
        b.expectedBodiesReceived("for B");

        publisher.publish("sharedA", new MqttMessage("for A".getBytes(StandardCharsets.UTF_8)));
        publisher.publish("sharedB", new MqttMessage("for B".getBytes(StandardCharsets.UTF_8)));

        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
    }

    @Test
    void messagesOfAStoppedDurableConsumerAreNotRoutedToTheConsumerLeft() throws Exception {
        // with cleanSession=false the stopped consumer keeps its subscription on the connected client
        startRoutes("paho:sharedA?client=#sharedClient&cleanSession=false",
                "paho:sharedB?client=#sharedClient&cleanSession=false");
        context.getRouteController().stopRoute("b");

        MockEndpoint a = context.getEndpoint("mock:a", MockEndpoint.class);
        a.expectedBodiesReceived("for A");

        publisher.publish("sharedB", new MqttMessage("for stopped B".getBytes(StandardCharsets.UTF_8)));
        publisher.publish("sharedA", new MqttMessage("for A".getBytes(StandardCharsets.UTF_8)));

        a.setResultWaitTime(10000);
        a.assertIsSatisfied();
    }

    @Test
    void stoppingOneOfTwoConsumersOfTheSameTopicKeepsTheOtherSubscribed() throws Exception {
        startRoutes("paho:sharedA?client=#sharedClient", "paho:sharedA?client=#sharedClient&qos=1");
        context.getRouteController().stopRoute("b");

        MockEndpoint a = context.getEndpoint("mock:a", MockEndpoint.class);
        a.expectedBodiesReceived("for A");

        publisher.publish("sharedA", new MqttMessage("for A".getBytes(StandardCharsets.UTF_8)));

        a.setResultWaitTime(10000);
        a.assertIsSatisfied();
    }

    @Test
    void messagesOfAStoppedDurableConsumerAreAcknowledgedWithManualAcks() throws Exception {
        // the stopped consumer keeps its subscription (cleanSession=false), so the broker keeps sending its messages:
        // they must be acknowledged, or each of them keeps one of the unacknowledged messages that the broker sends to
        // the client (such as max_inflight_messages of mosquitto), and then the broker sends nothing more to the client
        String clientId = useSharedClientWithManualAcks();
        String options = "?client=#sharedClient&qos=1&manualAcksEnabled=true&cleanSession=false";
        startRoutes("paho:sharedA" + options, "paho:sharedB" + options);
        context.getRouteController().stopRoute("b");

        MockEndpoint a = context.getEndpoint("mock:a", MockEndpoint.class);
        a.expectedBodiesReceived("for A");

        publisher.publish("sharedB", qos1Message("for stopped B"));
        publisher.publish("sharedA", qos1Message("for A"));

        a.setResultWaitTime(10000);
        a.assertIsSatisfied();
        // the broker keeps a message that the client did not acknowledge in the queue of the subscription
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                () -> assertThat(service.getQueueQueryResult(clientId + ".sharedB").getMessageCount()).isZero());
    }

    private static MqttMessage qos1Message(String body) {
        MqttMessage message = new MqttMessage(body.getBytes(StandardCharsets.UTF_8));
        message.setQos(1);
        return message;
    }
}
