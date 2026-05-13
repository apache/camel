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

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.telemetry.Op;
import org.apache.camel.telemetry.Span;
import org.apache.camel.telemetry.SpanStorageManagerExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SpanBeanTest extends TelemetryDevTracerTestSupport {

    TelemetryDevTracer tracer;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        tracer = new TelemetryDevTracer();
        tracer.setTraceFormat("json");
        CamelContext context = super.createCamelContext();
        CamelContextAware.trySetCamelContext(tracer, context);
        tracer.init(context);
        return context;
    }

    @Test
    void testRouteSingleRequest() throws IOException {
        Exchange result = template.request("direct:start", null);
        // Make sure the trace is propagated downstream
        assertNotNull(result.getIn().getHeader("traceparent"));
        Map<String, DevTrace> traces = tracesFromLog();
        assertEquals(1, traces.size());
        checkTrace(traces.values().iterator().next(), null);
    }

    private void checkTrace(DevTrace trace, String expectedBody) {
        List<DevSpanAdapter> spans = trace.getSpans();
        assertEquals(7, spans.size());
        DevSpanAdapter testProducer = spans.get(0);
        DevSpanAdapter direct = spans.get(1);
        DevSpanAdapter logProcessor = spans.get(2);
        DevSpanAdapter beanProcessor = spans.get(3);
        DevSpanAdapter beanMySpan = spans.get(4);
        DevSpanAdapter to = spans.get(5);
        DevSpanAdapter toProcessor = spans.get(6);

        // Validate span completion
        assertEquals("true", testProducer.getTag("isDone"));
        assertEquals("true", direct.getTag("isDone"));
        assertEquals("true", logProcessor.getTag("isDone"));
        assertEquals("true", beanProcessor.getTag("isDone"));
        assertEquals("true", beanMySpan.getTag("isDone"));
        assertEquals("true", to.getTag("isDone"));
        assertEquals("true", toProcessor.getTag("isDone"));

        // Validate same trace
        assertEquals(testProducer.getTag("traceid"), direct.getTag("traceid"));
        assertEquals(direct.getTag("traceid"), to.getTag("traceid"));
        assertEquals(testProducer.getTag("traceid"), logProcessor.getTag("traceid"));
        assertEquals(testProducer.getTag("traceid"), toProcessor.getTag("traceid"));
        assertEquals(testProducer.getTag("traceid"), beanProcessor.getTag("traceid"));
        assertEquals(testProducer.getTag("traceid"), beanMySpan.getTag("traceid"));

        // Validate hierarchy
        assertNull(testProducer.getTag("parentSpan"));
        assertEquals(testProducer.getTag("spanid"), direct.getTag("parentSpan"));
        assertEquals(direct.getTag("spanid"), logProcessor.getTag("parentSpan"));
        assertEquals(direct.getTag("spanid"), beanProcessor.getTag("parentSpan"));
        assertEquals(beanProcessor.getTag("spanid"), beanMySpan.getTag("parentSpan"));
        assertEquals(direct.getTag("spanid"), to.getTag("parentSpan"));
        assertEquals(to.getTag("spanid"), toProcessor.getTag("parentSpan"));

        // Validate operations
        assertEquals(Op.EVENT_SENT.toString(), testProducer.getTag("op"));
        assertEquals(Op.EVENT_RECEIVED.toString(), direct.getTag("op"));

        // Validate message logging
        assertEquals("A message", logProcessor.getLogEntries().get(0).getFields().get("message"));
        assertEquals(
                "Exchange[ExchangePattern: InOut, BodyType: null, Body: [Body is null]]",
                toProcessor.getLogEntries().get(0).getFields().get("message"));

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
                        .to("log:info");
            }
        };
    }

    class MyBean {
        // We simulate the creation of a Span by hand.
        public void helloWorld(Exchange exchange) {
            // We just simulate the creation of a span and proper nesting. In a real implementation
            // it is up to the telemetry technology to do so (for example, via method annotations)
            Span parentSpan = new SpanStorageManagerExchange().peek(exchange);
            Span span = tracer.getSpanLifecycleManager().create("mySpan", "empty", parentSpan, null);
            tracer.getSpanLifecycleManager().activate(span);
            tracer.getSpanLifecycleManager().deactivate(span);
            tracer.getSpanLifecycleManager().close(span);
        }
    }

}
