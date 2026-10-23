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
package org.apache.camel.component.tahu;

import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.eclipse.tahu.message.model.EdgeNodeDescriptor;
import org.eclipse.tahu.message.model.Metric.MetricBuilder;
import org.eclipse.tahu.message.model.MetricDataType;
import org.eclipse.tahu.message.model.SparkplugBPayload;
import org.eclipse.tahu.message.model.SparkplugBPayloadMap;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An Edge Node producer must connect and publish again after its route was stopped and started.
 */
class TahuEdgeProducerRestartTest {

    @Test
    void edgeNodePublishesAgainAfterTheRouteIsRestarted() throws Exception {
        try (FakeMqttBroker broker = new FakeMqttBroker(); CamelContext context = new DefaultCamelContext()) {
            String uri = "tahu-edge:RestartGroup/RestartNode?deviceIds=D1&servers=Mqtt:tcp://localhost:" + broker.getPort();
            TahuEdgeEndpoint endpoint = context.getEndpoint(uri, TahuEdgeEndpoint.class);
            endpoint.setMetricDataTypePayloadMap(new SparkplugBPayloadMap.SparkplugBPayloadMapBuilder()
                    .addMetric(new MetricBuilder("level", MetricDataType.Int32, 0).createMetric()).createPayload());
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:node").routeId("node").to(endpoint);
                }
            });
            context.start();

            String ndata = "spBv1.0/RestartGroup/NDATA/RestartNode";
            waitFor(broker, () -> broker.getPublishedTopics().contains("spBv1.0/RestartGroup/NBIRTH/RestartNode"));
            ProducerTemplate template = context.createProducerTemplate();
            template.sendBody("direct:node", payload(1));
            waitFor(broker, () -> count(broker, ndata) == 1);

            context.getRouteController().stopRoute("node");
            context.getRouteController().startRoute("node");

            // without the fix the producer of the restarted route took the client that the stop had shut down: it never
            // connected again, and the data was dropped (logged) while the exchange completed
            waitFor(broker, () -> broker.getPublishedTopics().stream()
                    .filter(t -> t.equals("spBv1.0/RestartGroup/NBIRTH/RestartNode")).count() == 2);
            template.sendBody("direct:node", payload(2));
            waitFor(broker, () -> count(broker, ndata) == 2);
            assertEquals(2, broker.getConnects());
        }
    }

    @Test
    void deviceOfAnotherRoutePublishesAgainAfterTheEdgeNodeRouteIsRestarted() throws Exception {
        try (FakeMqttBroker broker = new FakeMqttBroker(); CamelContext context = new DefaultCamelContext()) {
            TahuEdgeEndpoint node = context.getEndpoint(
                    "tahu-edge:DeviceGroup/DeviceNode?deviceIds=D1&servers=Mqtt:tcp://localhost:" + broker.getPort(),
                    TahuEdgeEndpoint.class);
            node.setMetricDataTypePayloadMap(new SparkplugBPayloadMap.SparkplugBPayloadMapBuilder()
                    .addMetric(new MetricBuilder("level", MetricDataType.Int32, 0).createMetric()).createPayload());
            TahuEdgeEndpoint device = context.getEndpoint("tahu-edge:DeviceGroup/DeviceNode/D1", TahuEdgeEndpoint.class);
            device.setMetricDataTypePayloadMap(new SparkplugBPayloadMap.SparkplugBPayloadMapBuilder()
                    .addMetric(new MetricBuilder("level", MetricDataType.Int32, 0).createMetric()).createPayload());
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:node").routeId("node").to(node);
                    from("direct:device").routeId("device").to(device);
                }
            });
            context.start();

            String ddata = "spBv1.0/DeviceGroup/DDATA/DeviceNode/D1";
            waitFor(broker, () -> broker.getPublishedTopics().contains("spBv1.0/DeviceGroup/DBIRTH/DeviceNode/D1"));
            ProducerTemplate template = context.createProducerTemplate();
            template.sendBody("direct:device", payload(1));
            waitFor(broker, () -> count(broker, ddata) == 1);

            // only the route of the Edge Node is restarted: the producer of the device keeps running
            context.getRouteController().stopRoute("node");
            context.getRouteController().startRoute("node");

            waitFor(broker, () -> count(broker, "spBv1.0/DeviceGroup/DBIRTH/DeviceNode/D1") == 2);
            template.sendBody("direct:device", payload(2));
            waitFor(broker, () -> count(broker, ddata) == 2);
        }
    }

    @Test
    void deviceStillBornAfterTheEdgeNodeRestartWhenOneOfTwoProducersOfTheDeviceStopped() throws Exception {
        try (FakeMqttBroker broker = new FakeMqttBroker(); CamelContext context = new DefaultCamelContext()) {
            TahuEdgeEndpoint node = context.getEndpoint(
                    "tahu-edge:SharedGroup/SharedNode?deviceIds=D1&servers=Mqtt:tcp://localhost:" + broker.getPort(),
                    TahuEdgeEndpoint.class);
            node.setMetricDataTypePayloadMap(new SparkplugBPayloadMap.SparkplugBPayloadMapBuilder()
                    .addMetric(new MetricBuilder("level", MetricDataType.Int32, 0).createMetric()).createPayload());
            TahuEdgeEndpoint device = context.getEndpoint("tahu-edge:SharedGroup/SharedNode/D1", TahuEdgeEndpoint.class);
            device.setMetricDataTypePayloadMap(new SparkplugBPayloadMap.SparkplugBPayloadMapBuilder()
                    .addMetric(new MetricBuilder("level", MetricDataType.Int32, 0).createMetric()).createPayload());
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:node").routeId("node").to(node);
                    // two producers to the same Device endpoint share its metric map
                    from("direct:device1").routeId("device1").to(device);
                    from("direct:device2").routeId("device2").to(device);
                }
            });
            context.start();

            String dbirth = "spBv1.0/SharedGroup/DBIRTH/SharedNode/D1";
            waitFor(broker, () -> count(broker, dbirth) == 1);

            // one of the two Device producers stops, the other keeps running
            context.getRouteController().stopRoute("device2");
            context.getRouteController().stopRoute("node");
            context.getRouteController().startRoute("node");

            // the new client of the Edge Node still has the metric map of the Device, so it is born again
            waitFor(broker, () -> count(broker, dbirth) == 2);
            ProducerTemplate template = context.createProducerTemplate();
            template.sendBody("direct:device1", payload(1));
            waitFor(broker, () -> count(broker, "spBv1.0/SharedGroup/DDATA/SharedNode/D1") == 1);
        }
    }

    @Test
    void clientIsShutDownWhenBothProducersOfTheEdgeNodeStopAfterOneWasRestarted() throws Exception {
        try (FakeMqttBroker broker = new FakeMqttBroker(); CamelContext context = new DefaultCamelContext()) {
            TahuEdgeEndpoint node = context.getEndpoint(
                    "tahu-edge:TwoGroup/TwoNode?deviceIds=D1&servers=Mqtt:tcp://localhost:" + broker.getPort(),
                    TahuEdgeEndpoint.class);
            node.setMetricDataTypePayloadMap(new SparkplugBPayloadMap.SparkplugBPayloadMapBuilder()
                    .addMetric(new MetricBuilder("level", MetricDataType.Int32, 0).createMetric()).createPayload());
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    // two producers of the same Edge Node
                    from("direct:node1").routeId("node1").to(node);
                    from("direct:node2").routeId("node2").to(node);
                }
            });
            context.start();

            String nbirth = "spBv1.0/TwoGroup/NBIRTH/TwoNode";
            String ndeath = "spBv1.0/TwoGroup/NDEATH/TwoNode";
            waitFor(broker, () -> count(broker, nbirth) == 1);

            // the restarted producer shuts down the first client and starts a second one; the other producer still
            // refers to the first client
            context.getRouteController().stopRoute("node1");
            context.getRouteController().startRoute("node1");
            waitFor(broker, () -> count(broker, nbirth) == 2);

            context.getRouteController().stopRoute("node2");
            context.getRouteController().stopRoute("node1");

            // the second client is shut down (its NDEATH is published) and removed, so that no client of this Edge
            // Node is left running without a started producer
            waitFor(broker, () -> count(broker, ndeath) == 2);
            assertNull(TahuEdgeProducer.descriptorClients.get(new EdgeNodeDescriptor("TwoGroup", "TwoNode")));
        }
    }

    @Test
    void deviceProducerCreatedWhileTheEdgeNodeIsStoppedUsesTheClientOfTheEdgeNode() throws Exception {
        try (FakeMqttBroker broker = new FakeMqttBroker(); CamelContext context = new DefaultCamelContext()) {
            TahuEdgeEndpoint node = context.getEndpoint(
                    "tahu-edge:LateGroup/LateNode?deviceIds=D1&servers=Mqtt:tcp://localhost:" + broker.getPort(),
                    TahuEdgeEndpoint.class);
            node.setMetricDataTypePayloadMap(new SparkplugBPayloadMap.SparkplugBPayloadMapBuilder()
                    .addMetric(new MetricBuilder("level", MetricDataType.Int32, 0).createMetric()).createPayload());
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:node").routeId("node").to(node);
                }
            });
            context.start();
            waitFor(broker, () -> count(broker, "spBv1.0/LateGroup/NBIRTH/LateNode") == 1);
            context.getRouteController().stopRoute("node");

            // the Device URI has none of the Edge Node options (deviceIds, servers): without the fix its producer
            // created the client of the Edge Node from them and failed to start with a NullPointerException
            TahuEdgeEndpoint device = context.getEndpoint("tahu-edge:LateGroup/LateNode/D1", TahuEdgeEndpoint.class);
            device.setMetricDataTypePayloadMap(new SparkplugBPayloadMap.SparkplugBPayloadMapBuilder()
                    .addMetric(new MetricBuilder("level", MetricDataType.Int32, 0).createMetric()).createPayload());
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:device").routeId("device").to(device);
                }
            });

            // the Edge Node has no started producer, so the Device data cannot be published
            ProducerTemplate template = context.createProducerTemplate();
            Exchange failed = template.send("direct:device", e -> e.getMessage().setBody(payload(1)));
            IllegalStateException e = assertInstanceOf(IllegalStateException.class, failed.getException());
            assertTrue(e.getMessage().contains("LateGroup/LateNode"), e.getMessage());

            // the Edge Node producer creates the client from its own endpoint, with the metric map of the Device
            context.getRouteController().startRoute("node");
            waitFor(broker, () -> count(broker, "spBv1.0/LateGroup/DBIRTH/LateNode/D1") == 1);
            template.sendBody("direct:device", payload(2));
            waitFor(broker, () -> count(broker, "spBv1.0/LateGroup/DDATA/LateNode/D1") == 1);
        }
    }

    private static void waitFor(FakeMqttBroker broker, BooleanSupplier condition) {
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(condition.getAsBoolean(),
                () -> "not published; connects: " + broker.getConnects() + ", published: " + broker.getPublishedTopics()));
    }

    private static long count(FakeMqttBroker broker, String topic) {
        return broker.getPublishedTopics().stream().filter(topic::equals).count();
    }

    private static SparkplugBPayload payload(int value) throws Exception {
        return new SparkplugBPayload.SparkplugBPayloadBuilder().setTimestamp(new Date())
                .addMetric(new MetricBuilder("level", MetricDataType.Int32, value).createMetric()).createPayload();
    }
}
