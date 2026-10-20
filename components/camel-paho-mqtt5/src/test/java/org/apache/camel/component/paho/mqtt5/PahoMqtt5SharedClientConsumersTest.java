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
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Consumers of different topics that use the same client (the client option) each receive the messages of their own
 * topic.
 */
class PahoMqtt5SharedClientConsumersTest {

    @RegisterExtension
    static ArtemisService service = ArtemisServiceFactory.createSingletonMQTTService();

    private MqttClient shared;
    private MqttClient publisher;
    private CamelContext context;

    @BeforeEach
    void setUp() throws Exception {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(true);
        shared = new MqttClient(brokerUrl(), "shared-" + System.nanoTime(), new MemoryPersistence());
        shared.connect(options);
        publisher = new MqttClient(brokerUrl(), "publisher-" + System.nanoTime(), new MemoryPersistence());
        publisher.connect(options);

        context = new DefaultCamelContext();
        context.getRegistry().bind("sharedClient", shared);
    }

    /**
     * Replaces the shared client by one with manual acks and the given receive maximum: the client sends no PUBACK for
     * a QoS 1 message until it is acknowledged, and the broker sends no more than receiveMaximum unacknowledged
     * messages to the client.
     */
    private void useSharedClientWithManualAcks(int receiveMaximum) throws Exception {
        shared.disconnect();
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(true);
        options.setReceiveMaximum(receiveMaximum);
        shared = new MqttClient(brokerUrl(), "shared-manual-" + System.nanoTime(), new MemoryPersistence());
        shared.setManualAcks(true);
        shared.connect(options);
        context.getRegistry().bind("sharedClient", shared);
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
        startRoutes("paho-mqtt5:sharedA?client=#sharedClient", "paho-mqtt5:sharedB?client=#sharedClient");
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
        // with cleanStart=false the stopped consumer keeps its subscription on the connected client
        startRoutes("paho-mqtt5:sharedA?client=#sharedClient&cleanStart=false",
                "paho-mqtt5:sharedB?client=#sharedClient&cleanStart=false");
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
        startRoutes("paho-mqtt5:sharedA?client=#sharedClient", "paho-mqtt5:sharedA?client=#sharedClient&qos=1");
        context.getRouteController().stopRoute("b");

        MockEndpoint a = context.getEndpoint("mock:a", MockEndpoint.class);
        a.expectedBodiesReceived("for A");

        publisher.publish("sharedA", new MqttMessage("for A".getBytes(StandardCharsets.UTF_8)));

        a.setResultWaitTime(10000);
        a.assertIsSatisfied();
    }

    @Test
    void messagesOfAStoppedDurableConsumerAreAcknowledgedWithManualAcks() throws Exception {
        // the stopped consumer keeps its subscription (cleanStart=false), so the broker keeps sending its messages:
        // they must be acknowledged, or after two of them the broker sends nothing more to the client
        useSharedClientWithManualAcks(2);
        String options = "?client=#sharedClient&qos=1&manualAcksEnabled=true&cleanStart=false";
        startRoutes("paho-mqtt5:sharedA" + options, "paho-mqtt5:sharedB" + options);
        context.getRouteController().stopRoute("b");

        MockEndpoint a = context.getEndpoint("mock:a", MockEndpoint.class);
        a.expectedBodiesReceived("for A");

        for (int i = 1; i <= 3; i++) {
            publisher.publish("sharedB", qos1Message("for stopped B " + i));
        }
        publisher.publish("sharedA", qos1Message("for A"));

        a.setResultWaitTime(10000);
        a.assertIsSatisfied();
    }

    private static MqttMessage qos1Message(String body) {
        return new MqttMessage(body.getBytes(StandardCharsets.UTF_8), 1, false, null);
    }
}
