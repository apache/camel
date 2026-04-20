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

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer.SpanInScope;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.micrometer.observability.CamelOpenTelemetryExtension.OtelTrace;
import org.apache.camel.telemetry.Op;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SpanInjectionTest extends MicrometerObservabilityTracerPropagationTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        tst.setTraceProcessors(true);
        return super.createCamelContext();
    }

    @Test
    void testRouteSingleRequest() throws IOException {
        // NOTE: we simulate that any external third party is the root parent, as we want Camel traces to depend on it.
        try (Scope rootScope = Context.root().makeCurrent()) {
            Span mySpan = tracer.nextSpan().name("mySpan").start();
            String expectedTrace = mySpan.context().traceId();
            String expectedSpan = mySpan.context().spanId();
            try (SpanInScope scope = tracer.withSpan(mySpan)) {
                template.sendBody("direct:start", "my-body");
                mySpan.end();
                Map<String, OtelTrace> traces = otelExtension.getTraces();
                assertEquals(1, traces.size());
                checkTrace(traces.values().iterator().next(), expectedTrace, expectedSpan);
            }
        }
    }

    @Test
    void testRouteMultipleRequests() throws IOException {
        int i = 10;
        Map<String, String> tracesRef = new HashMap<>();
        for (int j = 0; j < i; j++) {
            try (Scope rootScope = Context.root().makeCurrent()) {
                // NOTE: we simulate that any external third party is the root parent, as we want Camel traces to depend on it.
                Span mySpan = tracer.nextSpan().name("mySpan").start();
                // We hold the reference of each parent span for each trace
                tracesRef.put(mySpan.context().traceId(), mySpan.context().spanId());
                try (SpanInScope scope = tracer.withSpan(mySpan)) {
                    context.createProducerTemplate().sendBody("direct:start", "Hello!");
                }
                mySpan.end();
            }
        }
        Map<String, OtelTrace> traces = otelExtension.getTraces();
        // Each trace should have a unique trace id. It is enough to assert that
        // the number of elements in the map is the same of the requests to prove
        // all traces have been generated uniquely.
        assertEquals(i, traces.size());
        // Each trace should have the same structure
        for (OtelTrace trace : traces.values()) {
            String expectedTrace = trace.traceId;
            String expectedSpan = tracesRef.get(expectedTrace);
            checkTrace(trace, expectedTrace, expectedSpan);
        }
    }

    private void checkTrace(OtelTrace trace, String parentTrace, String parentSpan) {
        List<SpanData> spans = trace.getSpans();
        assertEquals(7, spans.size());

        SpanData mySpan = spans.get(0);
        SpanData testProducer = spans.get(1);
        SpanData direct = spans.get(2);
        SpanData innerLog = spans.get(3);
        SpanData innerProcessor = spans.get(4);
        SpanData log = spans.get(5);
        SpanData innerToLog = spans.get(6);

        // Validate span completion
        assertTrue(mySpan.hasEnded());
        assertTrue(testProducer.hasEnded());
        assertTrue(direct.hasEnded());
        assertTrue(innerLog.hasEnded());
        assertTrue(innerProcessor.hasEnded());
        assertTrue(log.hasEnded());
        assertTrue(innerToLog.hasEnded());

        // MySpan validation
        assertEquals("mySpan", mySpan.getName());

        // Validate same trace
        assertEquals(parentTrace, testProducer.getSpanContext().getTraceId());
        assertEquals(testProducer.getSpanContext().getTraceId(), direct.getSpanContext().getTraceId());
        assertEquals(testProducer.getSpanContext().getTraceId(), innerLog.getSpanContext().getTraceId());
        assertEquals(testProducer.getSpanContext().getTraceId(), innerProcessor.getSpanContext().getTraceId());
        assertEquals(testProducer.getSpanContext().getTraceId(), log.getSpanContext().getTraceId());
        assertEquals(testProducer.getSpanContext().getTraceId(), innerToLog.getSpanContext().getTraceId());

        // Validate operations
        assertEquals(Op.EVENT_RECEIVED.toString(), direct.getAttributes().get(AttributeKey.stringKey("op")));
        assertEquals(Op.EVENT_PROCESS.toString(), innerProcessor.getAttributes().get(AttributeKey.stringKey("op")));

        // Validate hierarchy
        // The parent now must be a valid trace as it was generated by a third party (our test in this case).
        assertTrue(testProducer.getParentSpanContext().isValid());
        assertEquals(parentSpan, testProducer.getParentSpanContext().getSpanId());

        assertEquals(testProducer.getSpanContext().getSpanId(), direct.getParentSpanContext().getSpanId());
        assertEquals(direct.getSpanContext().getSpanId(), innerLog.getParentSpanContext().getSpanId());
        assertEquals(direct.getSpanContext().getSpanId(), innerProcessor.getParentSpanContext().getSpanId());
        assertEquals(direct.getSpanContext().getSpanId(), log.getParentSpanContext().getSpanId());
        assertEquals(log.getSpanContext().getSpanId(), innerToLog.getParentSpanContext().getSpanId());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .routeId("start")
                        .log("A message")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                exchange.getIn().setHeader("operation", "fake");
                            }
                        })
                        .to("log:info");
            }
        };
    }
}
