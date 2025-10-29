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
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.telemetry.Op;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EnableProcessorsTest extends MicrometerObservabilityTracerTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        tst.setTraceProcessors(true);
        return super.createCamelContext();
    }

    @Test
    void testProcessorsTraceRequest() throws IOException {
        template.sendBody("direct:start", "my-body");
        Map<String, MicrometerObservabilityTrace> traces = traces();
        assertEquals(1, traces.size());
        checkTrace(traces.values().iterator().next());
    }

    private void checkTrace(MicrometerObservabilityTrace trace) {
        List<SimpleSpan> spans = trace.getSpans();
        assertEquals(6, spans.size());

        SimpleSpan testProducer = spans.get(0);
        SimpleSpan direct = spans.get(1);
        SimpleSpan innerLog = spans.get(2);
        SimpleSpan innerProcessor = spans.get(3);
        SimpleSpan log = spans.get(4);
        SimpleSpan innerToLog = spans.get(5);

        // Validate span completion
        assertNotEquals(Instant.EPOCH, testProducer.getEndTimestamp());
        assertNotEquals(Instant.EPOCH, direct.getEndTimestamp());
        assertNotEquals(Instant.EPOCH, innerLog.getEndTimestamp());
        assertNotEquals(Instant.EPOCH, innerProcessor.getEndTimestamp());
        assertNotEquals(Instant.EPOCH, log.getEndTimestamp());
        assertNotEquals(Instant.EPOCH, innerToLog.getEndTimestamp());

        // Validate same trace
        assertEquals(testProducer.getTraceId(), direct.getTraceId());
        assertEquals(testProducer.getTraceId(), innerLog.getTraceId());
        assertEquals(testProducer.getTraceId(), innerProcessor.getTraceId());
        assertEquals(testProducer.getTraceId(), log.getTraceId());
        assertEquals(testProducer.getTraceId(), innerToLog.getTraceId());

        // Validate op
        assertEquals(Op.EVENT_RECEIVED.toString(), direct.getTags().get("op"));
        assertEquals(Op.EVENT_PROCESS.toString(), innerProcessor.getTags().get("op"));

        // Validate hierarchy
        assertTrue(testProducer.getParentId().isEmpty());
        assertEquals(testProducer.getSpanId(), direct.getParentId());
        assertEquals(direct.getSpanId(), innerLog.getParentId());
        assertEquals(direct.getSpanId(), innerProcessor.getParentId());
        assertEquals(direct.getSpanId(), log.getParentId());
        assertEquals(log.getSpanId(), innerToLog.getParentId());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .routeId("start")
                        .log("A message")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                exchange.getIn().setHeader("operation", "fake");
                            }
                        })
                        .to("log:info");
            }
        };
    }

}
