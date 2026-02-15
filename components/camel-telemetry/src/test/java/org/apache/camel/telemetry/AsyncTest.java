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
import org.apache.camel.test.junit6.ExchangeTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/*
 * AsyncTest tests the execution of a new spin off async components.
 */
public class AsyncTest extends ExchangeTestSupport {

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
        // We must wait a safe time to let the context completing the async writing to the log trace.
        Thread.sleep(10000);
        Map<String, MockTrace> traces = mockTracer.traces();
        // Each trace should have a unique trace id. It is enough to assert that
        // the number of elements in the map is the same of the requests to prove
        // all traces have been generated uniquely.
        assertEquals(j, traces.size());
        // Each trace should have the same structure
        for (MockTrace trace : traces.values()) {
            // Body will be altered by the async component
            checkTrace(trace, "Hello!");
        }

    }

    private void checkTrace(MockTrace trace, String expectedBody) {
        List<Span> spans = trace.spans();
        assertEquals(8, spans.size());
        // Cast to implementation object to be able to
        // inspect the status of the Span.
        MockSpanAdapter testProducer = SpanTestSupport.getSpan(spans, "direct://start", Op.EVENT_SENT);
        MockSpanAdapter direct = SpanTestSupport.getSpan(spans, "direct://start", Op.EVENT_RECEIVED);
        MockSpanAdapter asyncDirectTo = SpanTestSupport.getSpan(spans, "direct://async", Op.EVENT_SENT);
        MockSpanAdapter asyncDirectFrom = SpanTestSupport.getSpan(spans, "direct://async", Op.EVENT_RECEIVED);
        MockSpanAdapter async = SpanTestSupport.getSpan(spans, "async://bye:camel", Op.EVENT_SENT);
        MockSpanAdapter log = SpanTestSupport.getSpan(spans, "log://info", Op.EVENT_SENT);
        MockSpanAdapter asyncLog = SpanTestSupport.getSpan(spans, "log://tapped", Op.EVENT_SENT);
        MockSpanAdapter asyncMock = SpanTestSupport.getSpan(spans, "mock://end", Op.EVENT_SENT);

        // Validate span completion
        assertEquals("true", testProducer.getTag("isDone"));
        assertEquals("true", direct.getTag("isDone"));
        assertEquals("true", asyncDirectTo.getTag("isDone"));
        assertEquals("true", asyncDirectFrom.getTag("isDone"));
        assertEquals("true", async.getTag("isDone"));
        assertEquals("true", log.getTag("isDone"));
        assertEquals("true", asyncLog.getTag("isDone"));
        assertEquals("true", asyncMock.getTag("isDone"));

        // Validate same trace
        assertEquals(testProducer.getTag("traceid"), direct.getTag("traceid"));
        assertEquals(testProducer.getTag("traceid"), asyncDirectTo.getTag("traceid"));
        assertEquals(testProducer.getTag("traceid"), asyncDirectFrom.getTag("traceid"));
        assertEquals(testProducer.getTag("traceid"), async.getTag("traceid"));
        assertEquals(testProducer.getTag("traceid"), log.getTag("traceid"));
        assertEquals(testProducer.getTag("traceid"), asyncLog.getTag("traceid"));
        assertEquals(testProducer.getTag("traceid"), asyncMock.getTag("traceid"));

        // Validate different Exchange ID
        assertNotEquals(testProducer.getTag("exchangeId"), asyncDirectTo.getTag("exchangeId"));
        assertEquals(testProducer.getTag("exchangeId"), direct.getTag("exchangeId"));
        assertEquals(testProducer.getTag("exchangeId"), log.getTag("exchangeId"));
        assertEquals(asyncDirectTo.getTag("exchangeId"), asyncDirectFrom.getTag("exchangeId"));
        assertEquals(asyncDirectTo.getTag("exchangeId"), async.getTag("exchangeId"));
        assertEquals(asyncDirectTo.getTag("exchangeId"), asyncLog.getTag("exchangeId"));
        assertEquals(asyncDirectTo.getTag("exchangeId"), asyncMock.getTag("exchangeId"));

        // Validate hierarchy
        assertNull(testProducer.getTag("parentSpan"));
        assertEquals(testProducer.getTag("spanid"), direct.getTag("parentSpan"));
        assertEquals(direct.getTag("spanid"), log.getTag("parentSpan"));
        assertEquals(direct.getTag("spanid"), asyncDirectTo.getTag("parentSpan"));
        assertEquals(asyncDirectTo.getTag("spanid"), asyncDirectFrom.getTag("parentSpan"));
        assertEquals(asyncDirectFrom.getTag("spanid"), async.getTag("parentSpan"));
        assertEquals(asyncDirectFrom.getTag("spanid"), asyncLog.getTag("parentSpan"));
        assertEquals(asyncDirectFrom.getTag("spanid"), asyncMock.getTag("parentSpan"));

        // Validate message logging
        assertEquals("A direct message", direct.logEntries().get(0).fields().get("message"));
        assertEquals("An async message", asyncDirectFrom.logEntries().get(0).fields().get("message"));
        String expectedAsyncBody = "Bye Camel";

        if (expectedBody == null) {
            assertEquals(
                    "Exchange[ExchangePattern: InOut, BodyType: null, Body: [Body is null]]",
                    log.logEntries().get(0).fields().get("message"));
        } else {
            assertEquals(
                    "Exchange[ExchangePattern: InOnly, BodyType: String, Body: " + expectedBody + "]",
                    log.logEntries().get(0).fields().get("message"));
        }
        assertEquals(
                "Exchange[ExchangePattern: InOnly, BodyType: String, Body: " + expectedAsyncBody + "]",
                asyncLog.logEntries().get(0).fields().get("message"));
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
