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

import java.io.IOException;
import java.util.List;
import java.util.Map;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.opentelemetry2.CamelOpenTelemetryExtension.OtelTrace;
import org.apache.camel.telemetry.Op;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AsyncDirectTest extends OpenTelemetryTracerTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        OpenTelemetryTracer tst = new OpenTelemetryTracer();
        tst.setTracer(otelExtension.getOpenTelemetry().getTracer("traceTest"));
        tst.setContextPropagators(otelExtension.getOpenTelemetry().getPropagators());
        CamelContext context = super.createCamelContext();
        CamelContextAware.trySetCamelContext(tst, context);
        tst.init(context);
        return context;
    }

    @Test
    void testRouteMultipleRequests() throws InterruptedException, IOException {
        int j = 10;
        MockEndpoint mock = getMockEndpoint("mock:end");
        mock.expectedMessageCount(j);
        mock.setAssertPeriod(5000);
        for (int i = 0; i < j; i++) {
            context.createProducerTemplate().sendBody("direct:start", "Hello!");
        }
        mock.assertIsSatisfied(1000);
        Map<String, OtelTrace> traces = otelExtension.getTraces();
        // Each trace should have a unique trace id. It is enough to assert that
        // the number of elements in the map is the same of the requests to prove
        // all traces have been generated uniquely.
        assertEquals(j, traces.size());
        // Each trace should have the same structure
        for (OtelTrace trace : traces.values()) {
            checkTrace(trace, "Hello!");
        }

    }

    private void checkTrace(OtelTrace trace, String expectedBody) {
        List<SpanData> spans = trace.getSpans();
        assertEquals(7, spans.size());
        SpanData testProducer = OpenTelemetryTracerTestSupport.getSpan(spans, "direct://start", Op.EVENT_SENT);
        SpanData direct = OpenTelemetryTracerTestSupport.getSpan(spans, "direct://start", Op.EVENT_RECEIVED);
        SpanData newDirectTo = OpenTelemetryTracerTestSupport.getSpan(spans, "direct://new", Op.EVENT_SENT);
        SpanData log = OpenTelemetryTracerTestSupport.getSpan(spans, "log://info", Op.EVENT_SENT);
        SpanData newDirectFrom = OpenTelemetryTracerTestSupport.getSpan(spans, "direct://new", Op.EVENT_RECEIVED);
        SpanData newLog = OpenTelemetryTracerTestSupport.getSpan(spans, "log://new", Op.EVENT_SENT);
        SpanData newMock = OpenTelemetryTracerTestSupport.getSpan(spans, "mock://end", Op.EVENT_SENT);

        // Validate span completion
        assertTrue(testProducer.hasEnded());
        assertTrue(direct.hasEnded());
        assertTrue(newDirectTo.hasEnded());
        assertTrue(log.hasEnded());
        assertTrue(newDirectFrom.hasEnded());
        assertTrue(newLog.hasEnded());
        assertTrue(newMock.hasEnded());

        // Validate same trace
        assertEquals(testProducer.getSpanContext().getTraceId(), direct.getSpanContext().getTraceId());
        assertEquals(testProducer.getSpanContext().getTraceId(), newDirectTo.getSpanContext().getTraceId());
        assertEquals(testProducer.getSpanContext().getTraceId(), log.getSpanContext().getTraceId());
        assertEquals(testProducer.getSpanContext().getTraceId(), newDirectFrom.getSpanContext().getTraceId());
        assertEquals(testProducer.getSpanContext().getTraceId(), newLog.getSpanContext().getTraceId());
        assertEquals(testProducer.getSpanContext().getTraceId(), newMock.getSpanContext().getTraceId());

        // Validate same Exchange ID
        // As it's a "direct" component, we expect the logic to happen within the same
        // Exchange boundary
        assertEquals(testProducer.getAttributes().get(AttributeKey.stringKey("exchangeId")),
                direct.getAttributes().get(AttributeKey.stringKey("exchangeId")));
        assertEquals(testProducer.getAttributes().get(AttributeKey.stringKey("exchangeId")),
                newDirectTo.getAttributes().get(AttributeKey.stringKey("exchangeId")));
        assertEquals(testProducer.getAttributes().get(AttributeKey.stringKey("exchangeId")),
                newDirectFrom.getAttributes().get(AttributeKey.stringKey("exchangeId")));
        assertEquals(testProducer.getAttributes().get(AttributeKey.stringKey("exchangeId")),
                log.getAttributes().get(AttributeKey.stringKey("exchangeId")));
        assertEquals(testProducer.getAttributes().get(AttributeKey.stringKey("exchangeId")),
                newLog.getAttributes().get(AttributeKey.stringKey("exchangeId")));
        assertEquals(testProducer.getAttributes().get(AttributeKey.stringKey("exchangeId")),
                newMock.getAttributes().get(AttributeKey.stringKey("exchangeId")));

        // Validate hierarchy
        assertFalse(testProducer.getParentSpanContext().isValid());
        assertEquals(testProducer.getSpanContext().getSpanId(), direct.getParentSpanContext().getSpanId());
        assertEquals(direct.getSpanContext().getSpanId(), newDirectTo.getParentSpanContext().getSpanId());
        assertEquals(direct.getSpanContext().getSpanId(), log.getParentSpanContext().getSpanId());
        assertEquals(newDirectTo.getSpanContext().getSpanId(), newDirectFrom.getParentSpanContext().getSpanId());
        assertEquals(newDirectFrom.getSpanContext().getSpanId(), newLog.getParentSpanContext().getSpanId());
        assertEquals(newDirectFrom.getSpanContext().getSpanId(), newMock.getParentSpanContext().getSpanId());

        // Validate message logging
        assertEquals("A direct message", direct.getEvents().get(0).getAttributes().get(
                AttributeKey.stringKey("message")));
        assertEquals("A new message", newDirectFrom.getEvents().get(0).getAttributes().get(
                AttributeKey.stringKey("message")));
        if (expectedBody == null) {
            assertEquals(
                    "Exchange[ExchangePattern: InOut, BodyType: null, Body: [Body is null]]",
                    log.getEvents().get(0).getAttributes().get(
                            AttributeKey.stringKey("message")));
            assertEquals(
                    "Exchange[ExchangePattern: InOut, BodyType: null, Body: [Body is null]]",
                    newLog.getEvents().get(0).getAttributes().get(
                            AttributeKey.stringKey("message")));
        } else {
            assertEquals(
                    "Exchange[ExchangePattern: InOnly, BodyType: String, Body: " + expectedBody + "]",
                    log.getEvents().get(0).getAttributes().get(
                            AttributeKey.stringKey("message")));
            assertEquals(
                    "Exchange[ExchangePattern: InOnly, BodyType: String, Body: " + expectedBody + "]",
                    newLog.getEvents().get(0).getAttributes().get(
                            AttributeKey.stringKey("message")));
        }
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .routeId("start")
                        .to("direct:new")
                        .log("A direct message")
                        .to("log:info");

                from("direct:new")
                        .delay(2000)
                        .routeId("new")
                        .log("A new message")
                        .to("log:new")
                        .to("mock:end");
            }
        };
    }

}
