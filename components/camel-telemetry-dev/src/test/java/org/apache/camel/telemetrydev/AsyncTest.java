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

package org.apache.camel.telemetrydev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.telemetry.Op;
import org.junit.jupiter.api.Test;

/*
 * AsyncTest tests the execution of a new spin off async components.
 */
public class AsyncTest extends TelemetryDevTracerTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        TelemetryDevTracer tst = new TelemetryDevTracer();
        tst.setTraceFormat("json");
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
        Map<String, DevTrace> traces = tracesFromLog();
        // Each trace should have a unique trace id. It is enough to assert that
        // the number of elements in the map is the same of the requests to prove
        // all traces have been generated uniquely.
        assertEquals(j, traces.size());
        // Each trace should have the same structure
        for (DevTrace trace : traces.values()) {
            checkTrace(trace, "Hello!");
        }
    }

    private void checkTrace(DevTrace trace, String expectedBody) {
        List<DevSpanAdapter> spans = trace.getSpans();
        assertEquals(8, spans.size());
        DevSpanAdapter testProducer = TelemetryDevTracerTestSupport.getSpan(spans, "direct://start", Op.EVENT_SENT);
        DevSpanAdapter direct = TelemetryDevTracerTestSupport.getSpan(spans, "direct://start", Op.EVENT_RECEIVED);
        DevSpanAdapter asyncDirectTo = TelemetryDevTracerTestSupport.getSpan(spans, "direct://async", Op.EVENT_SENT);
        DevSpanAdapter asyncDirectFrom =
                TelemetryDevTracerTestSupport.getSpan(spans, "direct://async", Op.EVENT_RECEIVED);
        DevSpanAdapter async = TelemetryDevTracerTestSupport.getSpan(spans, "async://bye:camel", Op.EVENT_SENT);
        DevSpanAdapter log = TelemetryDevTracerTestSupport.getSpan(spans, "log://info", Op.EVENT_SENT);
        DevSpanAdapter asyncLog = TelemetryDevTracerTestSupport.getSpan(spans, "log://tapped", Op.EVENT_SENT);
        DevSpanAdapter asyncMock = TelemetryDevTracerTestSupport.getSpan(spans, "mock://end", Op.EVENT_SENT);

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
        assertEquals(
                "A direct message", direct.getLogEntries().get(0).getFields().get("message"));
        assertEquals(
                "An async message",
                asyncDirectFrom.getLogEntries().get(0).getFields().get("message"));
        String expectedAsyncBody = "Bye Camel";

        if (expectedBody == null) {
            assertEquals(
                    "Exchange[ExchangePattern: InOut, BodyType: null, Body: [Body is null]]",
                    log.getLogEntries().get(0).getFields().get("message"));
        } else {
            assertEquals(
                    "Exchange[ExchangePattern: InOnly, BodyType: String, Body: " + expectedBody + "]",
                    log.getLogEntries().get(0).getFields().get("message"));
        }
        assertEquals(
                "Exchange[ExchangePattern: InOnly, BodyType: String, Body: " + expectedAsyncBody + "]",
                asyncLog.getLogEntries().get(0).getFields().get("message"));
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
