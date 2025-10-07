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
import java.time.Instant;
import java.util.List;
import java.util.Map;

import io.micrometer.tracing.test.simple.SimpleSpan;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.telemetry.Op;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * AsyncTest tests the execution of a new spin off async components.
 */
public class AsyncTest extends MicrometerObservabilityTracerTestSupport {

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
        Map<String, MicrometerObservabilityTrace> traces = traces();
        // Each trace should have a unique trace id. It is enough to assert that
        // the number of elements in the map is the same of the requests to prove
        // all traces have been generated uniquely.
        assertEquals(j, traces.size());
        // Each trace should have the same structure
        for (MicrometerObservabilityTrace trace : traces.values()) {
            checkTrace(trace, "Hello!");
        }

    }

    private void checkTrace(MicrometerObservabilityTrace trace, String expectedBody) {
        List<SimpleSpan> spans = trace.getSpans();
        assertEquals(8, spans.size());
        SimpleSpan testProducer = MicrometerObservabilityTracerTestSupport.getSpan(spans, "direct://start", Op.EVENT_SENT);
        SimpleSpan direct = MicrometerObservabilityTracerTestSupport.getSpan(spans, "direct://start", Op.EVENT_RECEIVED);
        SimpleSpan asyncDirectTo = MicrometerObservabilityTracerTestSupport.getSpan(spans, "direct://async", Op.EVENT_SENT);
        SimpleSpan asyncDirectFrom
                = MicrometerObservabilityTracerTestSupport.getSpan(spans, "direct://async", Op.EVENT_RECEIVED);
        SimpleSpan async = MicrometerObservabilityTracerTestSupport.getSpan(spans, "async://bye:camel", Op.EVENT_SENT);
        SimpleSpan log = MicrometerObservabilityTracerTestSupport.getSpan(spans, "log://info", Op.EVENT_SENT);
        SimpleSpan asyncLog = MicrometerObservabilityTracerTestSupport.getSpan(spans, "log://tapped", Op.EVENT_SENT);
        SimpleSpan asyncMock = MicrometerObservabilityTracerTestSupport.getSpan(spans, "mock://end", Op.EVENT_SENT);

        // Validate span completion
        assertNotEquals(Instant.EPOCH, testProducer.getEndTimestamp());
        assertNotEquals(Instant.EPOCH, direct.getEndTimestamp());
        assertNotEquals(Instant.EPOCH, asyncDirectTo.getEndTimestamp());
        assertNotEquals(Instant.EPOCH, log.getEndTimestamp());
        assertNotEquals(Instant.EPOCH, asyncDirectFrom.getEndTimestamp());
        assertNotEquals(Instant.EPOCH, async.getEndTimestamp());
        assertNotEquals(Instant.EPOCH, asyncLog.getEndTimestamp());
        assertNotEquals(Instant.EPOCH, asyncMock.getEndTimestamp());

        // Validate same trace
        assertEquals(testProducer.getTraceId(), direct.getTraceId());
        assertEquals(testProducer.getTraceId(), asyncDirectTo.getTraceId());
        assertEquals(testProducer.getTraceId(), log.getTraceId());
        assertEquals(testProducer.getTraceId(), asyncDirectFrom.getTraceId());
        assertEquals(testProducer.getTraceId(), async.getTraceId());
        assertEquals(testProducer.getTraceId(), asyncLog.getTraceId());
        assertEquals(testProducer.getTraceId(), asyncMock.getTraceId());

        // Validate different Exchange ID
        assertNotEquals(testProducer.getTags().get("exchangeId"), asyncDirectTo.getTags().get("exchangeId"));
        assertEquals(testProducer.getTags().get("exchangeId"), direct.getTags().get("exchangeId"));
        assertEquals(testProducer.getTags().get("exchangeId"), log.getTags().get("exchangeId"));
        assertEquals(asyncDirectTo.getTags().get("exchangeId"), asyncDirectFrom.getTags().get("exchangeId"));
        assertEquals(asyncDirectTo.getTags().get("exchangeId"), async.getTags().get("exchangeId"));
        assertEquals(asyncDirectTo.getTags().get("exchangeId"), asyncLog.getTags().get("exchangeId"));
        assertEquals(asyncDirectTo.getTags().get("exchangeId"), asyncMock.getTags().get("exchangeId"));

        // Validate hierarchy
        assertTrue(testProducer.getParentId().isEmpty());
        assertEquals(testProducer.getSpanId(), direct.getParentId());
        assertEquals(direct.getSpanId(), asyncDirectTo.getParentId());
        assertEquals(direct.getSpanId(), log.getParentId());
        assertEquals(asyncDirectTo.getSpanId(), asyncDirectFrom.getParentId());
        assertEquals(asyncDirectFrom.getSpanId(), async.getParentId());
        assertEquals(asyncDirectFrom.getSpanId(), asyncLog.getParentId());
        assertEquals(asyncDirectFrom.getSpanId(), asyncMock.getParentId());

        // Validate message logging
        assertEquals("message=A direct message", direct.getEvents().iterator().next().getValue());
        assertEquals("message=An async message", asyncDirectFrom.getEvents().iterator().next().getValue());
        String expectedBodyAsync = "Bye Camel";
        if (expectedBody == null) {
            assertEquals(
                    "message=Exchange[ExchangePattern: InOut, BodyType: null, Body: [Body is null]]",
                    log.getEvents().iterator().next().getValue());
        } else {
            assertEquals(
                    "message=Exchange[ExchangePattern: InOnly, BodyType: String, Body: " + expectedBody + "]",
                    log.getEvents().iterator().next().getValue());
        }
        assertEquals(
                "message=Exchange[ExchangePattern: InOnly, BodyType: String, Body: " + expectedBodyAsync + "]",
                asyncLog.getEvents().iterator().next().getValue());
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
