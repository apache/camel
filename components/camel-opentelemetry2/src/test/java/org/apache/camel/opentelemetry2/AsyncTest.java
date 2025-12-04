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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

/*
 * AsyncTest tests the execution of a new spin off async components.
 */
public class AsyncTest extends OpenTelemetryTracerTestSupport {

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
        assertEquals(8, spans.size());
        SpanData testProducer = OpenTelemetryTracerTestSupport.getSpan(spans, "direct://start", Op.EVENT_SENT);
        SpanData direct = OpenTelemetryTracerTestSupport.getSpan(spans, "direct://start", Op.EVENT_RECEIVED);
        SpanData asyncDirectTo = OpenTelemetryTracerTestSupport.getSpan(spans, "direct://async", Op.EVENT_SENT);
        SpanData asyncDirectFrom = OpenTelemetryTracerTestSupport.getSpan(spans, "direct://async", Op.EVENT_RECEIVED);
        SpanData async = OpenTelemetryTracerTestSupport.getSpan(spans, "async://bye:camel", Op.EVENT_SENT);
        SpanData log = OpenTelemetryTracerTestSupport.getSpan(spans, "log://info", Op.EVENT_SENT);
        SpanData asyncLog = OpenTelemetryTracerTestSupport.getSpan(spans, "log://tapped", Op.EVENT_SENT);
        SpanData asyncMock = OpenTelemetryTracerTestSupport.getSpan(spans, "mock://end", Op.EVENT_SENT);

        // Validate span completion
        assertTrue(testProducer.hasEnded());
        assertTrue(direct.hasEnded());
        assertTrue(asyncDirectTo.hasEnded());
        assertTrue(asyncDirectFrom.hasEnded());
        assertTrue(async.hasEnded());
        assertTrue(log.hasEnded());
        assertTrue(asyncLog.hasEnded());
        assertTrue(asyncMock.hasEnded());

        // Validate same trace
        assertEquals(
                testProducer.getSpanContext().getTraceId(),
                direct.getSpanContext().getTraceId());
        assertEquals(
                testProducer.getSpanContext().getTraceId(),
                asyncDirectTo.getSpanContext().getTraceId());
        assertEquals(
                testProducer.getSpanContext().getTraceId(),
                asyncDirectFrom.getSpanContext().getTraceId());
        assertEquals(
                testProducer.getSpanContext().getTraceId(),
                async.getSpanContext().getTraceId());
        assertEquals(
                testProducer.getSpanContext().getTraceId(), log.getSpanContext().getTraceId());
        assertEquals(
                testProducer.getSpanContext().getTraceId(),
                asyncLog.getSpanContext().getTraceId());
        assertEquals(
                testProducer.getSpanContext().getTraceId(),
                asyncMock.getSpanContext().getTraceId());

        // Validate different Exchange ID
        assertNotEquals(
                testProducer.getAttributes().get(AttributeKey.stringKey("exchangeId")),
                asyncDirectTo.getAttributes().get(AttributeKey.stringKey("exchangeId")));
        assertEquals(
                testProducer.getAttributes().get(AttributeKey.stringKey("exchangeId")),
                direct.getAttributes().get(AttributeKey.stringKey("exchangeId")));
        assertEquals(
                testProducer.getAttributes().get(AttributeKey.stringKey("exchangeId")),
                log.getAttributes().get(AttributeKey.stringKey("exchangeId")));
        assertEquals(
                asyncDirectTo.getAttributes().get(AttributeKey.stringKey("exchangeId")),
                asyncDirectFrom.getAttributes().get(AttributeKey.stringKey("exchangeId")));
        assertEquals(
                asyncDirectTo.getAttributes().get(AttributeKey.stringKey("exchangeId")),
                async.getAttributes().get(AttributeKey.stringKey("exchangeId")));
        assertEquals(
                asyncDirectTo.getAttributes().get(AttributeKey.stringKey("exchangeId")),
                asyncLog.getAttributes().get(AttributeKey.stringKey("exchangeId")));
        assertEquals(
                asyncDirectTo.getAttributes().get(AttributeKey.stringKey("exchangeId")),
                asyncMock.getAttributes().get(AttributeKey.stringKey("exchangeId")));

        // Validate hierarchy
        assertFalse(testProducer.getParentSpanContext().isValid());
        assertEquals(
                testProducer.getSpanContext().getSpanId(),
                direct.getParentSpanContext().getSpanId());
        assertEquals(
                direct.getSpanContext().getSpanId(), log.getParentSpanContext().getSpanId());
        assertEquals(
                direct.getSpanContext().getSpanId(),
                asyncDirectTo.getParentSpanContext().getSpanId());
        assertEquals(
                asyncDirectTo.getSpanContext().getSpanId(),
                asyncDirectFrom.getParentSpanContext().getSpanId());
        assertEquals(
                asyncDirectFrom.getSpanContext().getSpanId(),
                async.getParentSpanContext().getSpanId());
        assertEquals(
                asyncDirectFrom.getSpanContext().getSpanId(),
                asyncLog.getParentSpanContext().getSpanId());
        assertEquals(
                asyncDirectFrom.getSpanContext().getSpanId(),
                asyncMock.getParentSpanContext().getSpanId());

        // Validate message logging
        assertEquals(
                "A direct message", direct.getEvents().get(0).getAttributes().get(AttributeKey.stringKey("message")));
        assertEquals(
                "An async message",
                asyncDirectFrom.getEvents().get(0).getAttributes().get(AttributeKey.stringKey("message")));
        String expectedBodyAsync = "Bye Camel";
        if (expectedBody == null) {
            assertEquals(
                    "Exchange[ExchangePattern: InOut, BodyType: null, Body: [Body is null]]",
                    log.getEvents().get(0).getAttributes().get(AttributeKey.stringKey("message")));
        } else {
            assertEquals(
                    "Exchange[ExchangePattern: InOnly, BodyType: String, Body: " + expectedBody + "]",
                    log.getEvents().get(0).getAttributes().get(AttributeKey.stringKey("message")));
        }

        assertEquals(
                "Exchange[ExchangePattern: InOnly, BodyType: String, Body: " + expectedBodyAsync + "]",
                asyncLog.getEvents().get(0).getAttributes().get(AttributeKey.stringKey("message")));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.addComponent("async", new MyAsyncComponent());
                from("direct:start")
                        .routeId("start")
                        .log("A direct message")
                        .to("log:info")
                        .recipientList(constant("direct:async"));

                from("direct:async")
                        .to("async:bye:camel")
                        .routeId("async")
                        .log("An async message")
                        .to("log:tapped")
                        .to("mock:end");
            }
        };
    }
}
