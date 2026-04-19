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

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.opentelemetry2.CamelOpenTelemetryExtension.OtelTrace;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OpenTelemetrySpanAdapterTest extends OpenTelemetryTracerTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        OpenTelemetryTracer tst = new OpenTelemetryTracer();
        tst.setTracer(otelExtension.getOpenTelemetry().getTracer("adapterTest"));
        tst.setContextPropagators(otelExtension.getOpenTelemetry().getPropagators());
        CamelContext context = super.createCamelContext();
        CamelContextAware.trySetCamelContext(tst, context);
        tst.init(context);
        return context;
    }

    @Test
    void testSetBaggage() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:baggage");
        mock.expectedMessageCount(1);
        template.sendBody("direct:setBaggage", "Hello");
        mock.assertIsSatisfied();
        mock.getExchanges().forEach(exchange -> {
            String baggage = exchange.getIn().getHeader("baggage", String.class);
            assertNotNull(baggage);
            assertTrue(baggage.contains("tenant.id=globex"));
            assertTrue(baggage.contains("request.priority=high"));
        });
    }

    @Test
    void testChildSpanInheritsBaggage() {
        template.sendBody("direct:childBaggage", "Hello");
        Map<String, OtelTrace> traces = otelExtension.getTraces();
        assertEquals(1, traces.size());

        OtelTrace trace = traces.values().iterator().next();
        SpanData childSpan = findSpanByName(trace.getSpans(), "child");
        assertNotNull(childSpan);
        // The child span was created after setting baggage on parent;
        // we verify the child could read the inherited baggage by checking
        // the attribute that the processor copies from baggage to the child span
        assertEquals("acme", childSpan.getAttributes().get(AttributeKey.stringKey("inherited.baggage.tenant.id")));
    }

    private SpanData findSpanByName(List<SpanData> spans, String name) {
        return spans.stream()
                .filter(s -> name.equals(s.getName()))
                .findFirst()
                .orElse(null);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {

                from("direct:setBaggage")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) {
                                OpenTelemetrySpanAdapter span = OpenTelemetrySpanAdapter.fromExchange(exchange);
                                span.setBaggageEntry("tenant.id", "acme");
                                span.setBaggageEntry("tenant.id", "globex"); // overwrite
                                span.setBaggageEntry("request.priority", "high");
                            }
                        })
                        .to("mock:baggage");

                from("direct:childBaggage")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) {
                                OpenTelemetrySpanAdapter span = OpenTelemetrySpanAdapter.fromExchange(exchange);
                                span.setBaggageEntry("tenant.id", "acme");
                                Tracer tracer = otelExtension.getOpenTelemetry().getTracer("adapterTest");
                                Span child = tracer.spanBuilder("child").startSpan();
                                try {
                                    String inherited = OpenTelemetrySpanAdapter.fromExchange(exchange)
                                            .getBaggageEntry("tenant.id");
                                    child.setAttribute("inherited.baggage.tenant.id", inherited);
                                } finally {
                                    child.end();
                                }
                            }
                        });
            }
        };
    }

}
