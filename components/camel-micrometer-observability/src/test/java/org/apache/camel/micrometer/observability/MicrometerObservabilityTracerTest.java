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
import org.apache.camel.telemetry.Op;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MicrometerObservabilityTracerTest extends MicrometerObservabilityTracerTestSupport {

    @Test
    void testRouteSingleRequest() throws IOException {
        template.request("direct:start", null);
        Map<String, MicrometerObservabilityTrace> traces = traces();
        assertEquals(1, traces.size());
        checkTrace(traces.values().iterator().next(), null);
    }

    @Test
    void testRouteMultipleRequests() throws IOException {
        for (int i = 1; i <= 10; i++) {
            context.createProducerTemplate().sendBody("direct:start", "Hello!");
        }
        Map<String, MicrometerObservabilityTrace> traces = traces();
        // Each trace should have a unique trace id. It is enough to assert that
        // the number of elements in the map is the same of the requests to prove
        // all traces have been generated uniquely.
        assertEquals(10, traces.size());
        // Each trace should have the same structure
        for (MicrometerObservabilityTrace trace : traces.values()) {
            checkTrace(trace, "Hello!");
        }
    }

    private void checkTrace(MicrometerObservabilityTrace trace, String expectedBody) {
        List<SimpleSpan> spans = trace.getSpans();
        assertEquals(3, spans.size());
        SimpleSpan testProducer = spans.get(0);
        SimpleSpan direct = spans.get(1);
        SimpleSpan log = spans.get(2);

        // Validate span completion
        assertNotEquals(Instant.EPOCH, testProducer.getEndTimestamp());
        assertNotEquals(Instant.EPOCH, direct.getEndTimestamp());
        assertNotEquals(Instant.EPOCH, log.getEndTimestamp());

        // Validate same trace
        assertEquals(testProducer.getTraceId(), direct.getTraceId());
        assertEquals(direct.getTraceId(), log.getTraceId());

        // Validate hierarchy
        assertTrue(testProducer.getParentId().isEmpty());
        assertEquals(testProducer.getSpanId(), direct.getParentId());
        assertEquals(direct.getSpanId(), log.getParentId());

        // Validate operations
        assertEquals(Op.EVENT_SENT.toString(), testProducer.getTags().get("op"));
        assertEquals(Op.EVENT_RECEIVED.toString(), direct.getTags().get("op"));

        // Validate message logging
        assertEquals("message=A message", direct.getEvents().iterator().next().getValue());
        if (expectedBody == null) {
            assertEquals(
                    "message=Exchange[ExchangePattern: InOut, BodyType: null, Body: [Body is null]]",
                    log.getEvents().iterator().next().getValue());
        } else {
            assertEquals(
                    "message=Exchange[ExchangePattern: InOnly, BodyType: String, Body: " + expectedBody + "]",
                    log.getEvents().iterator().next().getValue());
        }

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
