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
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.telemetry.mock.MockSpanAdapter;
import org.apache.camel.telemetry.mock.MockTrace;
import org.apache.camel.telemetry.mock.MockTracer;
import org.apache.camel.test.junit6.ExchangeTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SpanToBeanTest extends ExchangeTestSupport {

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
    void testProcessorsTraceRequest() {
        template.sendBody("direct:start", "my-body");
        Map<String, MockTrace> traces = mockTracer.traces();
        assertEquals(1, traces.size());
        checkTrace(traces.values().iterator().next());
    }

    private void checkTrace(MockTrace trace) {
        List<Span> spans = trace.spans();
        assertEquals(8, spans.size());
        // Cast to implementation object to be able to
        // inspect the status of the Span.
        MockSpanAdapter testProducer = (MockSpanAdapter) spans.get(0);
        MockSpanAdapter direct = (MockSpanAdapter) spans.get(1);
        MockSpanAdapter innerLog = (MockSpanAdapter) spans.get(2);
        MockSpanAdapter toBean = (MockSpanAdapter) spans.get(3);
        MockSpanAdapter bean = (MockSpanAdapter) spans.get(4);
        MockSpanAdapter beanMethod = (MockSpanAdapter) spans.get(5);
        MockSpanAdapter log = (MockSpanAdapter) spans.get(6);
        MockSpanAdapter innerToLog = (MockSpanAdapter) spans.get(7);

        // Validate span completion
        assertEquals("true", testProducer.getTag("isDone"));
        assertEquals("true", direct.getTag("isDone"));
        assertEquals("true", innerLog.getTag("isDone"));
        assertEquals("true", toBean.getTag("isDone"));
        assertEquals("true", bean.getTag("isDone"));
        assertEquals("true", beanMethod.getTag("isDone"));
        assertEquals("true", log.getTag("isDone"));
        assertEquals("true", innerToLog.getTag("isDone"));

        // Validate same trace
        assertEquals(testProducer.getTag("traceid"), direct.getTag("traceid"));
        assertEquals(testProducer.getTag("traceid"), innerLog.getTag("traceid"));
        assertEquals(testProducer.getTag("traceid"), log.getTag("traceid"));
        assertEquals(testProducer.getTag("traceid"), toBean.getTag("traceid"));
        assertEquals(testProducer.getTag("traceid"), bean.getTag("traceid"));
        assertEquals(testProducer.getTag("traceid"), beanMethod.getTag("traceid"));
        assertEquals(testProducer.getTag("traceid"), innerToLog.getTag("traceid"));

        // Validate op
        assertEquals(Op.EVENT_RECEIVED.toString(), direct.getTag("op"));

        // Validate hierarchy
        assertNull(testProducer.getTag("parentSpan"));
        assertEquals(testProducer.getTag("spanid"), direct.getTag("parentSpan"));
        assertEquals(direct.getTag("spanid"), innerLog.getTag("parentSpan"));
        assertEquals(direct.getTag("spanid"), log.getTag("parentSpan"));
        assertEquals(direct.getTag("spanid"), toBean.getTag("parentSpan"));
        assertEquals(toBean.getTag("spanid"), bean.getTag("parentSpan"));
        assertEquals(bean.getTag("spanid"), beanMethod.getTag("parentSpan"));
        assertEquals(log.getTag("spanid"), innerToLog.getTag("parentSpan"));

        // Validate operations
        assertEquals(Op.EVENT_SENT.toString(), testProducer.getTag("op"));
        assertEquals(Op.EVENT_RECEIVED.toString(), direct.getTag("op"));

        // Validate message logging
        assertEquals("A message", innerLog.logEntries().get(0).fields().get("message"));
        assertEquals(
                "Exchange[ExchangePattern: InOnly, BodyType: String, Body: my-body]",
                innerToLog.logEntries().get(0).fields().get("message"));
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
        // We simulate the creation of a Span by hand.
        public void helloWorld(Exchange exchange) {
            // We just simulate the creation of a span and proper nesting. In a real implementation
            // it is up to the telemetry technology to do so (for example, via method annotations)
            Span parentSpan = new SpanStorageManagerExchange().peek(exchange);
            Span span = mockTracer.getSpanLifecycleManager().create("mySpan", "bo", parentSpan, null);
            mockTracer.getSpanLifecycleManager().activate(span);
            mockTracer.getSpanLifecycleManager().deactivate(span);
            mockTracer.getSpanLifecycleManager().close(span);
        }
    }

}
