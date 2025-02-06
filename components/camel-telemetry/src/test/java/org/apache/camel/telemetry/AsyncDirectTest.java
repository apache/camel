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
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.telemetry.mock.MockSpanAdapter;
import org.apache.camel.telemetry.mock.MockTrace;
import org.apache.camel.telemetry.mock.MockTracer;
import org.apache.camel.test.junit5.ExchangeTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class AsyncDirectTest extends ExchangeTestSupport {

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
    void testRouteMultipleRequests() throws InterruptedException {
        int j = 10;
        MockEndpoint mock = getMockEndpoint("mock:end");
        mock.expectedMessageCount(j);
        mock.setAssertPeriod(5000);
        for (int i = 0; i < j; i++) {
            context.createProducerTemplate().sendBody("direct:start", "Hello!");
        }
        mock.assertIsSatisfied(1000);
        Map<String, MockTrace> traces = mockTracer.traces();
        // Each trace should have a unique trace id. It is enough to assert that
        // the number of elements in the map is the same of the requests to prove
        // all traces have been generated uniquely.
        assertEquals(j, traces.size());
        // Each trace should have the same structure
        for (MockTrace trace : traces.values()) {
            checkTrace(trace, "Hello!");
        }

    }

    private void checkTrace(MockTrace trace, String expectedBody) {
        List<Span> spans = trace.spans();
        assertEquals(7, spans.size());
        // Cast to implementation object to be able to
        // inspect the status of the Span.
        MockSpanAdapter testProducer = SpanTestSupport.getSpan(spans, "direct://start", Op.EVENT_SENT);
        MockSpanAdapter direct = SpanTestSupport.getSpan(spans, "direct://start", Op.EVENT_RECEIVED);
        MockSpanAdapter newDirectTo = SpanTestSupport.getSpan(spans, "direct://new", Op.EVENT_SENT);
        MockSpanAdapter log = SpanTestSupport.getSpan(spans, "log://info", Op.EVENT_SENT);
        MockSpanAdapter newDirectFrom = SpanTestSupport.getSpan(spans, "direct://new", Op.EVENT_RECEIVED);
        MockSpanAdapter newLog = SpanTestSupport.getSpan(spans, "log://new", Op.EVENT_SENT);
        MockSpanAdapter newMock = SpanTestSupport.getSpan(spans, "mock://end", Op.EVENT_SENT);

        // Validate span completion
        assertEquals("true", testProducer.getTag("isDone"));
        assertEquals("true", direct.getTag("isDone"));
        assertEquals("true", newDirectTo.getTag("isDone"));
        assertEquals("true", log.getTag("isDone"));
        assertEquals("true", newDirectFrom.getTag("isDone"));
        assertEquals("true", newLog.getTag("isDone"));
        assertEquals("true", newMock.getTag("isDone"));

        // Validate same trace
        assertEquals(testProducer.getTag("traceid"), direct.getTag("traceid"));
        assertEquals(testProducer.getTag("traceid"), newDirectTo.getTag("traceid"));
        assertEquals(testProducer.getTag("traceid"), log.getTag("traceid"));
        assertEquals(testProducer.getTag("traceid"), newDirectFrom.getTag("traceid"));
        assertEquals(testProducer.getTag("traceid"), newLog.getTag("traceid"));
        assertEquals(testProducer.getTag("traceid"), newMock.getTag("traceid"));

        // Validate same Exchange ID
        // As it's a "direct" component, we expect the logic to happen within the same
        // Exchange boundary
        assertEquals(testProducer.getTag("exchangeId"), direct.getTag("exchangeId"));
        assertEquals(testProducer.getTag("exchangeId"), newDirectTo.getTag("exchangeId"));
        assertEquals(testProducer.getTag("exchangeId"), newDirectFrom.getTag("exchangeId"));
        assertEquals(testProducer.getTag("exchangeId"), log.getTag("exchangeId"));
        assertEquals(testProducer.getTag("exchangeId"), newLog.getTag("exchangeId"));
        assertEquals(testProducer.getTag("exchangeId"), newMock.getTag("exchangeId"));

        // // Validate hierarchy
        assertNull(testProducer.getTag("parentSpan"));
        assertEquals(testProducer.getTag("spanid"), direct.getTag("parentSpan"));
        assertEquals(direct.getTag("spanid"), newDirectTo.getTag("parentSpan"));
        assertEquals(direct.getTag("spanid"), log.getTag("parentSpan"));
        assertEquals(newDirectTo.getTag("spanid"), newDirectFrom.getTag("parentSpan"));
        assertEquals(newDirectFrom.getTag("spanid"), newLog.getTag("parentSpan"));
        assertEquals(newDirectFrom.getTag("spanid"), newMock.getTag("parentSpan"));

        // Validate message logging
        assertEquals("A direct message", direct.logEntries().get(0).fields().get("message"));
        assertEquals("A new message", newDirectFrom.logEntries().get(0).fields().get("message"));
        if (expectedBody == null) {
            assertEquals(
                    "Exchange[ExchangePattern: InOut, BodyType: null, Body: [Body is null]]",
                    log.logEntries().get(0).fields().get("message"));
            assertEquals(
                    "Exchange[ExchangePattern: InOut, BodyType: null, Body: [Body is null]]",
                    newLog.logEntries().get(0).fields().get("message"));
        } else {
            assertEquals(
                    "Exchange[ExchangePattern: InOnly, BodyType: String, Body: " + expectedBody + "]",
                    log.logEntries().get(0).fields().get("message"));
            assertEquals(
                    "Exchange[ExchangePattern: InOnly, BodyType: String, Body: " + expectedBody + "]",
                    newLog.logEntries().get(0).fields().get("message"));
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
