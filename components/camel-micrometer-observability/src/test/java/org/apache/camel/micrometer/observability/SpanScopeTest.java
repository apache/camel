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

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import io.opentelemetry.sdk.trace.data.SpanData;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.micrometer.observability.CamelOpenTelemetryExtension.OtelTrace;
import org.apache.camel.telemetry.Op;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies that spans are properly put in scope during route execution. This is critical for routes triggered by
 * consumers that don't have framework-level tracing (e.g., JMS), where Camel must manage the span scope itself.
 *
 * Without proper scope management, {@code tracer.currentSpan()} returns null during route execution, which prevents the
 * trace from being exported and for downstream instrumentation from attaching to the Camel trace.
 */
public class SpanScopeTest extends MicrometerObservabilityTracerPropagationTestSupport {

    private final AtomicReference<io.micrometer.tracing.Span> capturedCurrentSpan = new AtomicReference<>();

    @Test
    void testSpanIsInScopeDuringRouteExecution() {
        template.sendBody("direct:start", "Test Message");

        // The processor captured tracer.currentSpan() during route execution.
        io.micrometer.tracing.Span current = capturedCurrentSpan.get();
        assertNotNull(current);
    }

    @Test
    void testCapturedSpanMatchesTraceId() {
        template.sendBody("direct:start", "Test Message");

        io.micrometer.tracing.Span current = capturedCurrentSpan.get();
        assertNotNull(current);

        // The captured current span should belong to the same trace as the recorded spans
        Map<String, OtelTrace> traces = otelExtension.getTraces();
        assertEquals(1, traces.size());
        String expectedTraceId = traces.keySet().iterator().next();
        List<SpanData> spans = traces.get(expectedTraceId).getSpans();

        SpanData receivedSpan = getSpan(spans, "direct://start", Op.EVENT_RECEIVED);
        assertEquals(expectedTraceId, current.context().traceId());
        assertEquals(receivedSpan.getSpanId(), current.context().spanId());
    }

    @Test
    void testSpanScopeIsCleanedUpAfterRouteExecution() {
        template.sendBody("direct:start", "Test Message");

        // After the route completes, the scope should be closed and
        // tracer.currentSpan() should return null
        io.micrometer.tracing.Span afterRoute = micrometerTracer.currentSpan();
        assertNull(afterRoute);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .routeId("start")
                        .process(exchange -> {
                            capturedCurrentSpan.set(micrometerTracer.currentSpan());
                        })
                        .to("log:info");
            }
        };
    }

}
