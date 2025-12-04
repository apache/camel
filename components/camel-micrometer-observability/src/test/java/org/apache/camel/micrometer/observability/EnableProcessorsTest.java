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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.micrometer.observability.CamelOpenTelemetryExtension.OtelTrace;
import org.apache.camel.telemetry.Op;
import org.junit.jupiter.api.Test;

public class EnableProcessorsTest extends MicrometerObservabilityTracerPropagationTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        tst.setTraceProcessors(true);
        return super.createCamelContext();
    }

    @Test
    void testProcessorsTraceRequest() throws IOException {
        template.sendBody("direct:start", "my-body");
        Map<String, OtelTrace> traces = otelExtension.getTraces();
        assertEquals(1, traces.size());
        checkTrace(traces.values().iterator().next());
    }

    private void checkTrace(OtelTrace trace) {
        List<SpanData> spans = trace.getSpans();
        assertEquals(6, spans.size());

        SpanData testProducer = spans.get(0);
        SpanData direct = spans.get(1);
        SpanData innerLog = spans.get(2);
        SpanData innerProcessor = spans.get(3);
        SpanData log = spans.get(4);
        SpanData innerToLog = spans.get(5);

        // Validate span completion
        assertTrue(testProducer.hasEnded());
        assertTrue(direct.hasEnded());
        assertTrue(innerLog.hasEnded());
        assertTrue(innerProcessor.hasEnded());
        assertTrue(log.hasEnded());
        assertTrue(innerToLog.hasEnded());

        // Validate same trace
        assertEquals(testProducer.getTraceId(), direct.getTraceId());
        assertEquals(testProducer.getTraceId(), innerLog.getTraceId());
        assertEquals(testProducer.getTraceId(), innerProcessor.getTraceId());
        assertEquals(testProducer.getTraceId(), log.getTraceId());
        assertEquals(testProducer.getTraceId(), innerToLog.getTraceId());

        // Validate op
        assertEquals(Op.EVENT_RECEIVED.toString(), direct.getAttributes().get(AttributeKey.stringKey("op")));
        assertEquals(Op.EVENT_PROCESS.toString(), innerProcessor.getAttributes().get(AttributeKey.stringKey("op")));

        // Validate hierarchy
        assertEquals(SpanId.getInvalid(), testProducer.getParentSpanId());
        assertEquals(testProducer.getSpanId(), direct.getParentSpanId());
        assertEquals(direct.getSpanId(), innerLog.getParentSpanId());
        assertEquals(direct.getSpanId(), innerProcessor.getParentSpanId());
        assertEquals(direct.getSpanId(), log.getParentSpanId());
        assertEquals(log.getSpanId(), innerToLog.getParentSpanId());
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
