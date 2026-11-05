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

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.eclipse.tahu.message.SparkplugBPayloadDecoder;
import org.eclipse.tahu.message.model.Metric;
import org.eclipse.tahu.message.model.Metric.MetricBuilder;
import org.eclipse.tahu.message.model.MetricDataType;
import org.eclipse.tahu.message.model.SparkplugBPayload;
import org.eclipse.tahu.message.model.SparkplugBPayloadMap;
import org.eclipse.tahu.model.MetricDataTypeMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The message body of an Edge Node producer: a SparkplugBPayload is published as is, any other body is the body of a
 * payload whose metrics are the metric headers.
 */
class TahuEdgeProducerBodyTest {

    private static final String NDATA = "spBv1.0/BodyGroup/NDATA/BodyNode";

    private FakeMqttBroker broker;
    private CamelContext context;
    private ProducerTemplate template;

    @BeforeEach
    void startEdgeNode() throws Exception {
        broker = new FakeMqttBroker();
        context = new DefaultCamelContext();
        TahuEdgeEndpoint endpoint = context.getEndpoint(
                "tahu-edge:BodyGroup/BodyNode?deviceIds=D1&servers=Mqtt:tcp://localhost:" + broker.getPort(),
                TahuEdgeEndpoint.class);
        endpoint.setMetricDataTypePayloadMap(new SparkplugBPayloadMap.SparkplugBPayloadMapBuilder()
                .addMetric(new MetricBuilder("level", MetricDataType.Int32, 0).createMetric()).createPayload());
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:node").to(endpoint);
            }
        });
        context.start();
        template = context.createProducerTemplate();
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> broker.getPublishedTopics().contains("spBv1.0/BodyGroup/NBIRTH/BodyNode"));
    }

    @AfterEach
    void stopEdgeNode() throws Exception {
        context.close();
        broker.close();
    }

    @Test
    void textBodyIsThePayloadBody() throws Exception {
        template.sendBodyAndHeader("direct:node", "Hello Sparkplug", TahuConstants.METRIC_HEADER_PREFIX + "level", 7);

        // without the fix a text body failed with an InvalidPayloadException, as it does not convert to a
        // SparkplugBPayload
        SparkplugBPayload payload = publishedData();
        assertArrayEquals("Hello Sparkplug".getBytes(StandardCharsets.UTF_8), payload.getBody());
        assertLevel(payload, 7);
    }

    @Test
    void bytesBodyIsThePayloadBody() throws Exception {
        byte[] body = { 1, 2, 3 };
        template.sendBodyAndHeader("direct:node", body, TahuConstants.METRIC_HEADER_PREFIX + "level", 8);

        SparkplugBPayload payload = publishedData();
        assertArrayEquals(body, payload.getBody());
        assertLevel(payload, 8);
    }

    @Test
    void noBodyGivesAPayloadWithoutBody() throws Exception {
        template.sendBodyAndHeader("direct:node", null, TahuConstants.METRIC_HEADER_PREFIX + "level", 9);

        SparkplugBPayload payload = publishedData();
        assertNull(payload.getBody());
        assertLevel(payload, 9);
    }

    @Test
    void sparkplugPayloadBodyIsPublishedAsIs() throws Exception {
        SparkplugBPayload body = new SparkplugBPayload.SparkplugBPayloadBuilder().setTimestamp(new Date())
                .setBody(new byte[] { 4, 5 })
                .addMetric(new MetricBuilder("level", MetricDataType.Int32, 10).createMetric()).createPayload();
        // the header is not used, as the body is the payload
        template.sendBodyAndHeader("direct:node", body, TahuConstants.METRIC_HEADER_PREFIX + "level", 11);

        SparkplugBPayload payload = publishedData();
        assertArrayEquals(new byte[] { 4, 5 }, payload.getBody());
        assertLevel(payload, 10);
    }

    @Test
    void bodyThatDoesNotConvertToBytesFails() {
        Exchange exchange = template.send("direct:node", e -> e.getMessage().setBody(new Reading(12)));

        assertInstanceOf(InvalidPayloadException.class, exchange.getException());
        assertFalse(broker.getPublishedTopics().contains(NDATA), () -> "published: " + broker.getPublishedTopics());
    }

    private SparkplugBPayload publishedData() throws Exception {
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> broker.getPublished().stream().anyMatch(p -> NDATA.equals(p.topic())));
        byte[] bytes = broker.getPublished().stream().filter(p -> NDATA.equals(p.topic())).findFirst().orElseThrow()
                .payload();
        // the data types of the metrics are only in the birth message
        MetricDataTypeMap dataTypes = new MetricDataTypeMap();
        dataTypes.addMetricDataType("level", MetricDataType.Int32);
        return new SparkplugBPayloadDecoder().buildFromByteArray(bytes, dataTypes);
    }

    private static void assertLevel(SparkplugBPayload payload, int level) {
        Metric metric = payload.getMetrics().stream().filter(m -> "level".equals(m.getName())).findFirst()
                .orElseThrow(() -> new AssertionError("no level metric: " + payload.getMetrics()));
        assertEquals(level, ((Number) metric.getValue()).intValue());
    }

    record Reading(int value) {
    }
}
