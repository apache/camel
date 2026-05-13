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
import java.util.List;
import java.util.Map;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.micrometer.observability.CamelOpenTelemetryExtension.OtelTrace;
import org.apache.camel.telemetry.Op;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SpanBeanTest extends MicrometerObservabilityTracerPropagationTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext ctx = super.createCamelContext();
        tst.setDisableCoreProcessors(false);

        return ctx;
    }

    @Test
    void testRouteSingleRequest() throws IOException {
        template.request("direct:start", null);
        Map<String, OtelTrace> traces = otelExtension.getTraces();
        assertEquals(1, traces.size());
        checkTrace(traces.values().iterator().next());
    }

    private void checkTrace(OtelTrace trace) {
        List<SpanData> spans = trace.getSpans();
        assertEquals(7, spans.size());
        SpanData testProducer = spans.get(0);
        SpanData direct = spans.get(1);
        SpanData innerLog = spans.get(2);
        SpanData beanProcessor = spans.get(3);
        SpanData customSpan = spans.get(4);
        SpanData log = spans.get(5);
        SpanData innerToLog = spans.get(6);

        // Validate span completion
        assertTrue(testProducer.hasEnded());
        assertTrue(direct.hasEnded());
        assertTrue(innerLog.hasEnded());
        assertTrue(beanProcessor.hasEnded());
        assertTrue(customSpan.hasEnded());
        assertTrue(log.hasEnded());
        assertTrue(innerToLog.hasEnded());

        // Validate same trace
        assertEquals(testProducer.getSpanContext().getTraceId(), direct.getSpanContext().getTraceId());
        assertEquals(testProducer.getSpanContext().getTraceId(), direct.getSpanContext().getTraceId());
        assertEquals(testProducer.getSpanContext().getTraceId(), innerLog.getSpanContext().getTraceId());
        assertEquals(testProducer.getSpanContext().getTraceId(), beanProcessor.getSpanContext().getTraceId());
        assertEquals(testProducer.getSpanContext().getTraceId(), customSpan.getSpanContext().getTraceId());
        assertEquals(testProducer.getSpanContext().getTraceId(), log.getSpanContext().getTraceId());
        assertEquals(testProducer.getSpanContext().getTraceId(), innerToLog.getSpanContext().getTraceId());

        // Validate operations
        assertEquals(Op.EVENT_RECEIVED.toString(), direct.getAttributes().get(AttributeKey.stringKey("op")));
        assertEquals(Op.EVENT_PROCESS.toString(), beanProcessor.getAttributes().get(AttributeKey.stringKey("op")));

        // Validate hierarchy
        assertFalse(testProducer.getParentSpanContext().isValid());
        assertEquals(testProducer.getSpanContext().getSpanId(), direct.getParentSpanContext().getSpanId());
        assertEquals(direct.getSpanContext().getSpanId(), beanProcessor.getParentSpanContext().getSpanId());
        assertEquals(beanProcessor.getSpanContext().getSpanId(), customSpan.getParentSpanContext().getSpanId());

        // Validate custom span
        assertEquals("mySpan", customSpan.getName());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                MyBean myBean = new MyBean();
                this.getCamelContext().getRegistry().bind("myBean", myBean);

                from("direct:start")
                        .routeId("start")
                        .log("A message")
                        .bean(MyBean.class)
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                // noop
                            }
                        })
                        .to("log:info");
            }
        };
    }

    class MyBean {
        // NOTE: the commented annotation below would work only when an agent or a runtime framework (quarkus or spring)
        // is available. We simulate it creating the Span by hand instead.
        //@WithSpan
        public void helloWorld() {
            io.opentelemetry.api.trace.Span mySpan = otelTracer.spanBuilder("mySpan").startSpan();
            // Do the work here
            mySpan.end();
        }
    }
}
