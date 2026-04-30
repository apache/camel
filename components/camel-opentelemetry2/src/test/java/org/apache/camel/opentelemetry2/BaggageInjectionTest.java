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

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.opentelemetry2.CamelOpenTelemetryExtension.OtelTrace;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BaggageInjectionTest extends OpenTelemetryTracerTestSupport {

    Tracer tracer = otelExtension.getOpenTelemetry().getTracer("spanInjection");

    @Override
    protected CamelContext createCamelContext() throws Exception {
        OpenTelemetryTracer tst = new OpenTelemetryTracer();
        tst.setTracer(tracer);
        tst.setContextPropagators(otelExtension.getOpenTelemetry().getPropagators());
        tst.setTraceProcessors(true);
        CamelContext context = super.createCamelContext();
        CamelContextAware.trySetCamelContext(tst, context);
        tst.init(context);
        return context;
    }

    @Test
    void testRouteExternalBaggage() throws IOException {
        try (Scope rootScope = Context.root().makeCurrent();
             // Add a baggage the is expected to be propagated
             Scope baggageScope = Baggage.current().toBuilder().put("external.id", "9876").build().makeCurrent()) {
            Span span = tracer.spanBuilder("mySpan").startSpan();
            try (Scope scope = span.makeCurrent()) {
                template.sendBody("direct:start", "my-body");
                Map<String, OtelTrace> traces = otelExtension.getTraces();
                assertEquals(1, traces.size());
                checkTrace(traces.values().iterator().next());
            }
        }
    }

    private void checkTrace(OtelTrace trace) {
        List<SpanData> spans = trace.getSpans();
        assertEquals(8, spans.size());
        SpanData testProducer = spans.get(0);
        SpanData direct = spans.get(1);
        SpanData innerProcessor1 = spans.get(2);
        SpanData setHeaders = spans.get(3);
        SpanData innerLog = spans.get(4);
        SpanData innerProcessor2 = spans.get(5);
        SpanData log = spans.get(6);
        SpanData innerToLog = spans.get(7);

        // Validate span completion
        assertTrue(testProducer.hasEnded());
        assertTrue(direct.hasEnded());
        assertTrue(innerProcessor1.hasEnded());
        assertTrue(setHeaders.hasEnded());
        assertTrue(innerLog.hasEnded());
        assertTrue(innerProcessor2.hasEnded());
        assertTrue(log.hasEnded());
        assertTrue(innerToLog.hasEnded());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                from("direct:start")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                // This is set from the external caller
                                assertEquals("9876", Baggage.current().getEntryValue("external.id"));
                                // This is not yet set
                                assertNull(Baggage.current().getEntryValue("tenant.id"));
                            }
                        })
                        .setHeader("OTEL_BAGGAGE_tenant.id", constant("1234"))
                        .routeId("start")
                        .log("A message")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                exchange.getIn().setHeader("operation", "fake");

                                assertEquals("9876", Baggage.current().getEntryValue("external.id"));
                                assertEquals("1234", Baggage.current().getEntryValue("tenant.id"));
                            }
                        })
                        .to("log:info");
            }
        };
    }
}
