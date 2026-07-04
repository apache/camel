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
package org.apache.camel.opentelemetry2;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.opentelemetry2.CamelOpenTelemetryExtension.OtelTrace;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests OTel span completeness for multicast to seda/stub topologies with varying levels of async nesting.
 *
 * Verifies that EVENT_RECEIVED spans are properly created and ended for all consumer routes, including those consuming
 * from seda/stub endpoints produced by a multicast inside another seda consumer route (nested async paths).
 *
 * See CAMEL-23708 for background on the production observation that triggered this investigation.
 */
public class MulticastSedaTest extends OpenTelemetryTracerTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        OpenTelemetryTracer tst = new OpenTelemetryTracer();
        tst.setTracer(otelExtension.getOpenTelemetry().getTracer("traceTest"));
        tst.setContextPropagators(otelExtension.getOpenTelemetry().getPropagators());
        tst.setDisableCoreProcessors(true);
        CamelContext context = super.createCamelContext();
        CamelContextAware.trySetCamelContext(tst, context);
        tst.init(context);
        return context;
    }

    /**
     * Baseline: direct → seda → multicast → seda consumer routes.
     */
    @Test
    void testMulticastToSeda() throws Exception {
        MockEndpoint mockAlpha = getMockEndpoint("mock:alpha");
        MockEndpoint mockBeta = getMockEndpoint("mock:beta");
        mockAlpha.expectedMessageCount(1);
        mockBeta.expectedMessageCount(1);

        template.sendBody("direct:start", "Hello");

        mockAlpha.assertIsSatisfied(5000);
        mockBeta.assertIsSatisfied(5000);

        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    Map<String, OtelTrace> traces = otelExtension.getTraces();
                    assertEquals(1, traces.size());
                    OtelTrace trace = traces.values().iterator().next();
                    assertTrue(trace.getSpans().size() >= 10,
                            "Expected at least 10 spans, got " + trace.getSpans().size());
                });

        OtelTrace trace = otelExtension.getTraces().values().iterator().next();
        List<SpanData> spans = trace.getSpans();

        assertReceivedSpanExists(spans, "seda://orders");
        assertReceivedSpanExists(spans, "seda://alpha");
        assertReceivedSpanExists(spans, "seda://beta");
    }

    /**
     * Nested async: seda → multicast → seda → leaf seda consumers.
     */
    @Test
    void testNestedSedaMulticast() throws Exception {
        MockEndpoint mockAlphaOut = getMockEndpoint("mock:alpha-out");
        MockEndpoint mockBetaOut = getMockEndpoint("mock:beta-out");
        mockAlphaOut.expectedMessageCount(1);
        mockBetaOut.expectedMessageCount(1);

        template.sendBody("seda:orders", "Hello");

        mockAlphaOut.assertIsSatisfied(5000);
        mockBetaOut.assertIsSatisfied(5000);

        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    List<SpanData> allSpans = otelExtension.getSpans();
                    assertTrue(allSpans.size() >= 10, "Expected at least 10 spans, got " + allSpans.size());
                });

        List<SpanData> allSpans = otelExtension.getSpans();

        assertReceivedSpanExists(allSpans, "seda://orders");
        assertReceivedSpanExists(allSpans, "seda://alpha");
        assertReceivedSpanExists(allSpans, "seda://beta");
    }

    /**
     * Production-like topology matching the route-topology example with stub replacing kafka.
     *
     * <pre>
     * direct:order-entry → direct:process → direct:validate → stub:orders
     *   → dispatcher route (multicast)
     *       → stub:fulfillment → fulfillment route → stub:warehouse → mock:warehouse
     *       → stub:notifications → notification route → stub:email → mock:email
     * </pre>
     */
    @Test
    void testProductionTopology() throws Exception {
        MockEndpoint mockWarehouse = getMockEndpoint("mock:warehouse");
        MockEndpoint mockEmail = getMockEndpoint("mock:email");
        mockWarehouse.expectedMessageCount(1);
        mockEmail.expectedMessageCount(1);

        template.sendBody("direct:order-entry", "order-001");

        mockWarehouse.assertIsSatisfied(5000);
        mockEmail.assertIsSatisfied(5000);

        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    List<SpanData> allSpans = otelExtension.getSpans();
                    assertTrue(allSpans.size() >= 12, "Expected at least 12 spans, got " + allSpans.size());
                });

        List<SpanData> allSpans = otelExtension.getSpans();

        // Direct routes RECEIVED spans
        assertReceivedSpanExists(allSpans, "direct://order-entry");
        assertReceivedSpanExists(allSpans, "direct://process");
        assertReceivedSpanExists(allSpans, "direct://validate");

        // First async hop: dispatcher consumes from stub:orders
        assertReceivedSpanExists(allSpans, "stub://orders");

        // Second async hop: leaf routes consume from stub endpoints produced by multicast
        assertReceivedSpanExists(allSpans, "stub://fulfillment");
        assertReceivedSpanExists(allSpans, "stub://notifications");

        // Third async hop: terminal consumers
        assertReceivedSpanExists(allSpans, "stub://warehouse");
        assertReceivedSpanExists(allSpans, "stub://email");
    }

    /**
     * Multiple messages through the production topology to verify span consistency.
     */
    @Test
    void testProductionTopologyMultipleMessages() throws Exception {
        int count = 5;
        MockEndpoint mockWarehouse = getMockEndpoint("mock:warehouse");
        MockEndpoint mockEmail = getMockEndpoint("mock:email");
        mockWarehouse.expectedMessageCount(count);
        mockEmail.expectedMessageCount(count);

        for (int i = 0; i < count; i++) {
            template.sendBody("direct:order-entry", "order-" + i);
        }

        mockWarehouse.assertIsSatisfied(10000);
        mockEmail.assertIsSatisfied(10000);

        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    Map<String, OtelTrace> traces = otelExtension.getTraces();
                    assertEquals(count, traces.size(), "Should have " + count + " traces");
                });

        Map<String, OtelTrace> traces = otelExtension.getTraces();
        int tracesWithMissingSpans = 0;

        for (OtelTrace trace : traces.values()) {
            List<SpanData> spans = trace.getSpans();

            boolean hasFulfillmentReceived = hasReceivedSpan(spans, "stub://fulfillment");
            boolean hasNotificationReceived = hasReceivedSpan(spans, "stub://notifications");

            if (!hasFulfillmentReceived || !hasNotificationReceived) {
                tracesWithMissingSpans++;
            }
        }

        assertEquals(0, tracesWithMissingSpans,
                tracesWithMissingSpans + " of " + count
                                                + " traces have missing RECEIVED spans for stub:fulfillment or stub:notifications");
    }

    private void assertReceivedSpanExists(List<SpanData> spans, String uri) {
        List<SpanData> found = spans.stream()
                .filter(s -> uri.equals(s.getAttributes().get(AttributeKey.stringKey("camel.uri"))))
                .filter(s -> "EVENT_RECEIVED".equals(s.getAttributes().get(AttributeKey.stringKey("op"))))
                .collect(Collectors.toList());
        assertEquals(1, found.size(), uri + " RECEIVED span should exist");
        assertTrue(found.get(0).hasEnded(), uri + " RECEIVED span should have ended");
    }

    private boolean hasReceivedSpan(List<SpanData> spans, String uri) {
        return spans.stream()
                .anyMatch(s -> uri.equals(s.getAttributes().get(AttributeKey.stringKey("camel.uri")))
                        && "EVENT_RECEIVED".equals(s.getAttributes().get(AttributeKey.stringKey("op"))));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // === Simple seda topology ===
                from("direct:start")
                        .routeId("entry")
                        .to("seda:orders");

                from("seda:orders")
                        .routeId("dispatcher")
                        .multicast()
                        .to("seda:alpha", "seda:beta")
                        .end();

                from("seda:alpha")
                        .routeId("alpha")
                        .to("seda:alpha-out")
                        .to("mock:alpha");

                from("seda:beta")
                        .routeId("beta")
                        .to("seda:beta-out")
                        .to("mock:beta");

                from("seda:alpha-out")
                        .routeId("alpha-out")
                        .to("mock:alpha-out");

                from("seda:beta-out")
                        .routeId("beta-out")
                        .to("mock:beta-out");

                // === Production-like topology (stub simulating kafka) ===
                from("direct:order-entry")
                        .routeId("order-entry")
                        .to("direct:process");

                from("direct:process")
                        .routeId("process-order")
                        .to("direct:validate")
                        .to("stub:orders");

                from("direct:validate")
                        .routeId("validate-order")
                        .log("Validating: ${body}");

                from("stub:orders")
                        .routeId("order-dispatcher")
                        .multicast()
                        .to("stub:fulfillment", "stub:notifications")
                        .end();

                from("stub:fulfillment")
                        .routeId("fulfillment")
                        .to("stub:warehouse");

                from("stub:notifications")
                        .routeId("notification")
                        .to("stub:email");

                from("stub:warehouse")
                        .routeId("warehouse")
                        .to("mock:warehouse");

                from("stub:email")
                        .routeId("email")
                        .to("mock:email");
            }
        };
    }
}
