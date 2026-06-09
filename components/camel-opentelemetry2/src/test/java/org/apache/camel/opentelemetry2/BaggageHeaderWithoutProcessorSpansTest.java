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
import java.util.Map;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Tracer;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.opentelemetry2.CamelOpenTelemetryExtension.OtelTrace;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that OTEL_BAGGAGE_* headers set in a route are visible in Baggage.current() inside processors without
 * requiring traceProcessors=true (CAMEL-23564).
 */
public class BaggageHeaderWithoutProcessorSpansTest extends OpenTelemetryTracerTestSupport {

    Tracer tracer = otelExtension.getOpenTelemetry().getTracer("baggageTest");

    @Override
    protected CamelContext createCamelContext() throws Exception {
        OpenTelemetryTracer tst = new OpenTelemetryTracer();
        tst.setTracer(tracer);
        tst.setContextPropagators(otelExtension.getOpenTelemetry().getPropagators());
        // traceProcessors is NOT enabled — the default
        CamelContext context = super.createCamelContext();
        CamelContextAware.trySetCamelContext(tst, context);
        tst.init(context);
        return context;
    }

    @Test
    void testBaggageHeaderVisibleWithoutProcessorSpans() throws IOException {
        template.sendBody("direct:start", "my-body");
        Map<String, OtelTrace> traces = otelExtension.getTraces();
        assertEquals(1, traces.size());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .routeId("start")
                        .setHeader("OTEL_BAGGAGE_BusinessReference", constant("REF-42"))
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                assertEquals("REF-42", Baggage.current().getEntryValue("BusinessReference"));
                            }
                        })
                        .to("log:info");
            }
        };
    }
}
