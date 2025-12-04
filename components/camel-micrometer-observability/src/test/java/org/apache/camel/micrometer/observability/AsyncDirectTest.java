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

package org.apache.camel.micrometer.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.micrometer.observability.CamelOpenTelemetryExtension.OtelTrace;
import org.apache.camel.telemetry.Op;
import org.junit.jupiter.api.Test;

public class AsyncDirectTest extends MicrometerObservabilityTracerPropagationTestSupport {

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
        SpanData testProducer = getSpan(spans, "direct://start", Op.EVENT_SENT);
        SpanData direct = getSpan(spans, "direct://start", Op.EVENT_RECEIVED);
        SpanData newDirectTo = getSpan(spans, "direct://new", Op.EVENT_SENT);
        SpanData log = getSpan(spans, "log://info", Op.EVENT_SENT);
        SpanData newDirectFrom = getSpan(spans, "direct://new", Op.EVENT_RECEIVED);
        SpanData newLog = getSpan(spans, "log://new", Op.EVENT_SENT);
        SpanData newMock = getSpan(spans, "mock://end", Op.EVENT_SENT);

        // Validate span completion
        assertTrue(testProducer.hasEnded());
        assertTrue(direct.hasEnded());
        assertTrue(newDirectTo.hasEnded());
        assertTrue(log.hasEnded());
        assertTrue(newDirectFrom.hasEnded());
        assertTrue(newLog.hasEnded());
        assertTrue(newMock.hasEnded());

        // Validate same trace
        assertEquals(testProducer.getTraceId(), direct.getTraceId());
        assertEquals(testProducer.getTraceId(), newDirectTo.getTraceId());
        assertEquals(testProducer.getTraceId(), log.getTraceId());
        assertEquals(testProducer.getTraceId(), newDirectFrom.getTraceId());
        assertEquals(testProducer.getTraceId(), newLog.getTraceId());
        assertEquals(testProducer.getTraceId(), newMock.getTraceId());

        // Validate same Exchange ID
        // As it's a "direct" component, we expect the logic to happen within the same
        // Exchange boundary
        assertEquals(
                testProducer.getAttributes().get(AttributeKey.stringKey("exchangeId")),
                direct.getAttributes().get(AttributeKey.stringKey("exchangeId")));
        assertEquals(
                testProducer.getAttributes().get(AttributeKey.stringKey("exchangeId")),
                newDirectTo.getAttributes().get(AttributeKey.stringKey("exchangeId")));
        assertEquals(
                testProducer.getAttributes().get(AttributeKey.stringKey("exchangeId")),
                newDirectFrom.getAttributes().get(AttributeKey.stringKey("exchangeId")));
        assertEquals(
                testProducer.getAttributes().get(AttributeKey.stringKey("exchangeId")),
                log.getAttributes().get(AttributeKey.stringKey("exchangeId")));
        assertEquals(
                testProducer.getAttributes().get(AttributeKey.stringKey("exchangeId")),
                newLog.getAttributes().get(AttributeKey.stringKey("exchangeId")));
        assertEquals(
                testProducer.getAttributes().get(AttributeKey.stringKey("exchangeId")),
                newMock.getAttributes().get(AttributeKey.stringKey("exchangeId")));

        // // Validate hierarchy
        assertEquals(SpanId.getInvalid(), testProducer.getParentSpanId());
        assertEquals(testProducer.getSpanId(), direct.getParentSpanId());
        assertEquals(direct.getSpanId(), newDirectTo.getParentSpanId());
        assertEquals(direct.getSpanId(), log.getParentSpanId());
        assertEquals(newDirectTo.getSpanId(), newDirectFrom.getParentSpanId());
        assertEquals(newDirectFrom.getSpanId(), newLog.getParentSpanId());
        assertEquals(newDirectFrom.getSpanId(), newMock.getParentSpanId());

        // Validate message logging
        assertEquals("message=A direct message", direct.getEvents().get(0).getName());
        assertEquals("message=A new message", newDirectFrom.getEvents().get(0).getName());

        if (expectedBody == null) {
            assertEquals(
                    "message=Exchange[ExchangePattern: InOut, BodyType: null, Body: [Body is null]]",
                    log.getEvents().get(0).getName());
            assertEquals(
                    "message=Exchange[ExchangePattern: InOut, BodyType: null, Body: [Body is null]]",
                    newLog.getEvents().get(0).getName());
        } else {
            assertEquals(
                    "message=Exchange[ExchangePattern: InOnly, BodyType: String, Body: " + expectedBody + "]",
                    log.getEvents().get(0).getName());
            assertEquals(
                    "message=Exchange[ExchangePattern: InOnly, BodyType: String, Body: " + expectedBody + "]",
                    newLog.getEvents().get(0).getName());
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
