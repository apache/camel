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
package org.apache.camel.telemetry;

import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.telemetry.mock.MockSpanAdapter;
import org.apache.camel.telemetry.mock.MockTrace;
import org.apache.camel.telemetry.mock.MockTracer;
import org.apache.camel.test.junit5.ExchangeTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SpanPropagationTest extends ExchangeTestSupport {

    MockTracer mockTracer;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        this.mockTracer = new MockTracer();
        CamelContextAware.trySetCamelContext(mockTracer, context);
        mockTracer.init(context);
        return context;
    }

    @Test
    void testPropagateUpstreamTraceRequest() {
        template.requestBodyAndHeader("direct:start", "sample body",
                "traceparent", "123456789-123456");
        Map<String, MockTrace> traces = mockTracer.traces();
        assertEquals(1, traces.size());
        checkTrace(traces.values().iterator().next());
    }

    private void checkTrace(MockTrace trace) {
        List<Span> spans = trace.spans();
        assertEquals(3, spans.size());
        // Cast to implementation object to be able to
        // inspect the status of the Span.
        MockSpanAdapter testProducer = (MockSpanAdapter) spans.get(0);
        MockSpanAdapter direct = (MockSpanAdapter) spans.get(1);
        MockSpanAdapter log = (MockSpanAdapter) spans.get(2);

        // Validate span completion
        assertEquals("true", testProducer.getTag("isDone"));
        assertEquals("true", direct.getTag("isDone"));
        assertEquals("true", log.getTag("isDone"));

        // Validate same trace
        assertEquals("123456789", testProducer.getTag("traceid"));
        assertEquals(testProducer.getTag("traceid"), direct.getTag("traceid"));
        assertEquals(direct.getTag("traceid"), log.getTag("traceid"));

        // Validate hierarchy
        assertEquals("123456", testProducer.getTag("parentSpan"));
        assertEquals(testProducer.getTag("spanid"), direct.getTag("parentSpan"));
        assertEquals(direct.getTag("spanid"), log.getTag("parentSpan"));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .routeId("start")
                        .log("A message")
                        .to("log:info");
            }
        };
    }

}
