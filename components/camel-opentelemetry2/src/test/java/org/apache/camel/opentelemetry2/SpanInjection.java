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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.opentelemetry2.CamelOpenTelemetryExtension.OtelTrace;
import org.apache.camel.telemetry.Op;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SpanInjection extends OpenTelemetryTracerTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        OpenTelemetryTracer tst = new OpenTelemetryTracer();
        tst.setTracer(otelExtension.getOpenTelemetry().getTracer("traceTest"));
        tst.setContextPropagators(otelExtension.getOpenTelemetry().getPropagators());
        tst.setTraceProcessors(true);
        CamelContext context = super.createCamelContext();
        CamelContextAware.trySetCamelContext(tst, context);
        tst.init(context);
        return context;
    }

    @Test
    void testRouteSingleRequest() throws IOException {
        // NOTE: we simulate that any external third party is the root parent, as we want Camel traces to depend on it.
        Span span = otelExtension.getOpenTelemetry().getTracer("traceTest").spanBuilder("mySpan").startSpan();
        String expectedTrace = span.getSpanContext().getTraceId();
        String expectedSpan = span.getSpanContext().getSpanId();
        try (Scope scope = span.makeCurrent()) {
            template.sendBody("direct:start", "my-body");
            Map<String, OtelTrace> traces = otelExtension.getTraces();
            assertEquals(1, traces.size());
            checkTrace(traces.values().iterator().next(), expectedTrace, expectedSpan);
        }
    }

    @Test
    void testRouteMultipleRequests() throws IOException {
        int i = 10;
        Map<String, String> tracesRef = new HashMap<>();
        for (int j = 0; j < i; j++) {
            // NOTE: we simulate that any external third party is the root parent, as we want Camel traces to depend on it.
            Span span = otelExtension.getOpenTelemetry().getTracer("traceTest").spanBuilder("mySpan").startSpan();
            // We hold the reference of each parent span for each trace
            tracesRef.put(span.getSpanContext().getTraceId(), span.getSpanContext().getSpanId());
            try (Scope scope = span.makeCurrent()) {
                context.createProducerTemplate().sendBody("direct:start", "Hello!");
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
        assertEquals(6, spans.size());
        SpanData testProducer = spans.get(0);
        SpanData direct = spans.get(1);
        SpanData innerLog = spans.get(2);
        SpanData innerProcessor = spans.get(3);
        SpanData log = spans.get(4);
        SpanData innerToLog = spans.get(5);

        // Validate span completion
        assertTrue(testProducer.hasEnded());
        assertTrue(direct.hasEnded());
        assertTrue(innerLog.hasEnded());
        assertTrue(innerProcessor.hasEnded());
        assertTrue(log.hasEnded());
        assertTrue(innerToLog.hasEnded());

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
