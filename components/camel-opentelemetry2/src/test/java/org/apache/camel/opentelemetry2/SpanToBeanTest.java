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
package org.apache.camel.opentelemetry2;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.opentelemetry2.CamelOpenTelemetryExtension.OtelTrace;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SpanToBeanTest extends OpenTelemetryTracerTestSupport {

    Tracer tracer = otelExtension.getOpenTelemetry().getTracer("spanInjection");

    @Override
    protected CamelContext createCamelContext() throws Exception {
        OpenTelemetryTracer tst = new OpenTelemetryTracer();
        tst.setTracer(tracer);
        tst.setContextPropagators(otelExtension.getOpenTelemetry().getPropagators());
        CamelContext context = super.createCamelContext();
        CamelContextAware.trySetCamelContext(tst, context);
        tst.init(context);
        return context;
    }

    @Test
    void testRouteSingleRequest() throws IOException {
        template.sendBody("direct:start", "my-body");
        Map<String, OtelTrace> traces = otelExtension.getTraces();
        assertEquals(1, traces.size());
        checkTrace(traces.values().iterator().next());
    }

    private void checkTrace(OtelTrace trace) {
        List<SpanData> spans = trace.getSpans();
        assertEquals(8, spans.size());
        SpanData testProducer = spans.get(0);
        SpanData direct = spans.get(1);
        SpanData innerLog = spans.get(2);
        SpanData beanTo = spans.get(3);
        SpanData beanProcessor = spans.get(4);
        SpanData beanMethod = spans.get(5);
        SpanData log = spans.get(6);
        SpanData innerToLog = spans.get(7);

        // Validate span completion
        assertTrue(testProducer.hasEnded());
        assertTrue(direct.hasEnded());
        assertTrue(innerLog.hasEnded());
        assertTrue(beanTo.hasEnded());
        assertTrue(beanProcessor.hasEnded());
        assertTrue(beanMethod.hasEnded());
        assertTrue(log.hasEnded());
        assertTrue(innerToLog.hasEnded());

        // Validate same trace
        assertEquals(testProducer.getSpanContext().getTraceId(), direct.getSpanContext().getTraceId());
        assertEquals(testProducer.getSpanContext().getTraceId(), innerLog.getSpanContext().getTraceId());
        assertEquals(testProducer.getSpanContext().getTraceId(), beanTo.getSpanContext().getTraceId());
        assertEquals(testProducer.getSpanContext().getTraceId(), beanProcessor.getSpanContext().getTraceId());
        assertEquals(testProducer.getSpanContext().getTraceId(), beanMethod.getSpanContext().getTraceId());
        assertEquals(testProducer.getSpanContext().getTraceId(), log.getSpanContext().getTraceId());
        assertEquals(testProducer.getSpanContext().getTraceId(), innerToLog.getSpanContext().getTraceId());

        // Validate hierarchy
        assertFalse(testProducer.getParentSpanContext().isValid());

        assertEquals(testProducer.getSpanContext().getSpanId(), direct.getParentSpanContext().getSpanId());
        assertEquals(direct.getSpanContext().getSpanId(), innerLog.getParentSpanContext().getSpanId());
        assertEquals(direct.getSpanContext().getSpanId(), beanTo.getParentSpanContext().getSpanId());
        assertEquals(beanTo.getSpanContext().getSpanId(), beanProcessor.getParentSpanContext().getSpanId());
        // NOTE: the bean method belongs to the component ("to") node.
        assertEquals(beanTo.getSpanContext().getSpanId(), beanMethod.getParentSpanContext().getSpanId());
        assertEquals(direct.getSpanContext().getSpanId(), log.getParentSpanContext().getSpanId());
        assertEquals(log.getSpanContext().getSpanId(), innerToLog.getParentSpanContext().getSpanId());
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
                        .to("bean:myBean")
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

    class MyBean {
        // NOTE: the commented annotation below would work only when an agent or a runtime framework (quarkus or spring)
        // is available. We simulate it creating the Span by hand instead.
        //@WithSpan
        public void helloWorld() {
            Span mySpan = tracer.spanBuilder("helloworld").startSpan();
            // Do the work here
            mySpan.end();
        }
    }
}
