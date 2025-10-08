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
 * WiretappedRouteTest tests the execution of a new spin off component which would create a new exchange,
 * for example, using the wiretap component.
 */
public class AsyncWiretapTest extends MicrometerObservabilityTracerTestSupport {

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
        assertEquals(7, spans.size());
        SimpleSpan testProducer = MicrometerObservabilityTracerTestSupport.getSpan(spans, "direct://start", Op.EVENT_SENT);
        SimpleSpan direct = MicrometerObservabilityTracerTestSupport.getSpan(spans, "direct://start", Op.EVENT_RECEIVED);
        SimpleSpan wiretapDirectTo = MicrometerObservabilityTracerTestSupport.getSpan(spans, "direct://tap", Op.EVENT_SENT);
        SimpleSpan wiretapDirectFrom
                = MicrometerObservabilityTracerTestSupport.getSpan(spans, "direct://tap", Op.EVENT_RECEIVED);
        SimpleSpan log = MicrometerObservabilityTracerTestSupport.getSpan(spans, "log://info", Op.EVENT_SENT);
        SimpleSpan wiretapLog = MicrometerObservabilityTracerTestSupport.getSpan(spans, "log://tapped", Op.EVENT_SENT);
        SimpleSpan wiretapMock = MicrometerObservabilityTracerTestSupport.getSpan(spans, "mock://end", Op.EVENT_SENT);

        // Validate span completion
        assertNotEquals(Instant.EPOCH, testProducer.getEndTimestamp());
        assertNotEquals(Instant.EPOCH, direct.getEndTimestamp());
        assertNotEquals(Instant.EPOCH, wiretapDirectTo.getEndTimestamp());
        assertNotEquals(Instant.EPOCH, log.getEndTimestamp());
        assertNotEquals(Instant.EPOCH, wiretapDirectFrom.getEndTimestamp());
        assertNotEquals(Instant.EPOCH, wiretapLog.getEndTimestamp());
        assertNotEquals(Instant.EPOCH, wiretapMock.getEndTimestamp());

        // Validate same trace
        assertEquals(testProducer.getTraceId(), direct.getTraceId());
        assertEquals(testProducer.getTraceId(), wiretapDirectTo.getTraceId());
        assertEquals(testProducer.getTraceId(), log.getTraceId());
        assertEquals(testProducer.getTraceId(), wiretapDirectFrom.getTraceId());
        assertEquals(testProducer.getTraceId(), wiretapLog.getTraceId());
        assertEquals(testProducer.getTraceId(), wiretapMock.getTraceId());

        // Validate different Exchange ID
        assertNotEquals(testProducer.getTags().get("exchangeId"), wiretapDirectTo.getTags().get("exchangeId"));
        assertEquals(testProducer.getTags().get("exchangeId"), direct.getTags().get("exchangeId"));
        assertEquals(testProducer.getTags().get("exchangeId"), log.getTags().get("exchangeId"));
        assertEquals(wiretapDirectTo.getTags().get("exchangeId"), wiretapDirectFrom.getTags().get("exchangeId"));
        assertEquals(wiretapDirectTo.getTags().get("exchangeId"), wiretapLog.getTags().get("exchangeId"));
        assertEquals(wiretapDirectTo.getTags().get("exchangeId"), wiretapMock.getTags().get("exchangeId"));

        // Validate hierarchy
        assertTrue(testProducer.getParentId().isEmpty());
        assertEquals(testProducer.getSpanId(), direct.getParentId());
        assertEquals(direct.getSpanId(), wiretapDirectTo.getParentId());
        assertEquals(direct.getSpanId(), log.getParentId());
        assertEquals(wiretapDirectTo.getSpanId(), wiretapDirectFrom.getParentId());
        assertEquals(wiretapDirectFrom.getSpanId(), wiretapLog.getParentId());
        assertEquals(wiretapDirectFrom.getSpanId(), wiretapMock.getParentId());

        // Validate message logging
        assertEquals("message=A direct message", direct.getEvents().iterator().next().getValue());
        assertEquals("message=A tapped message", wiretapDirectFrom.getEvents().iterator().next().getValue());

        if (expectedBody == null) {
            assertEquals(
                    "message=Exchange[ExchangePattern: InOut, BodyType: null, Body: [Body is null]]",
                    log.getEvents().iterator().next().getValue());
            assertEquals(
                    "message=Exchange[ExchangePattern: InOut, BodyType: null, Body: [Body is null]]",
                    wiretapLog.getEvents().iterator().next().getValue());
        } else {
            assertEquals(
                    "message=Exchange[ExchangePattern: InOnly, BodyType: String, Body: " + expectedBody + "]",
                    log.getEvents().iterator().next().getValue());
            assertEquals(
                    "message=Exchange[ExchangePattern: InOnly, BodyType: String, Body: " + expectedBody + "]",
                    wiretapLog.getEvents().iterator().next().getValue());
        }

    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .routeId("start")
                        .wireTap("direct:tap")
                        .log("A direct message")
                        .to("log:info");

                from("direct:tap")
                        .delay(2000)
                        .routeId("wiretapped")
                        .log("A tapped message")
                        .to("log:tapped")
                        .to("mock:end");
            }
        };
    }
}
