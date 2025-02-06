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
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.telemetry.Op;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/*
 * AsyncCXFTest tests the execution of CXF async which was reported as a potential candidate to
 * inconsistent Span creation in async mode.
 */
public class AsyncCXFTest extends TelemetryDevTracerTestSupport {

    private static int cxfPort = AvailablePortFinder.getNextRandomAvailable();

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
        DevSpanAdapter directSendTo = TelemetryDevTracerTestSupport.getSpan(spans, "direct://send", Op.EVENT_SENT);
        DevSpanAdapter directSendFrom = TelemetryDevTracerTestSupport.getSpan(spans, "direct://send", Op.EVENT_RECEIVED);
        DevSpanAdapter cxfRs = TelemetryDevTracerTestSupport.getSpan(
                spans,
                "cxfrs://http://localhost:" + cxfPort + "/rest/helloservice/sayHello?synchronous=false",
                Op.EVENT_SENT);
        DevSpanAdapter rest = TelemetryDevTracerTestSupport.getSpan(
                spans,
                "rest://post:/rest/helloservice:/sayHello?routeId=direct-hi",
                Op.EVENT_RECEIVED);
        DevSpanAdapter log = TelemetryDevTracerTestSupport.getSpan(spans, "log://hi", Op.EVENT_SENT);
        DevSpanAdapter mock = TelemetryDevTracerTestSupport.getSpan(spans, "mock://end", Op.EVENT_SENT);

        // Validate span completion
        assertEquals("true", testProducer.getTag("isDone"));
        assertEquals("true", direct.getTag("isDone"));
        assertEquals("true", directSendTo.getTag("isDone"));
        assertEquals("true", directSendFrom.getTag("isDone"));
        assertEquals("true", cxfRs.getTag("isDone"));
        assertEquals("true", rest.getTag("isDone"));
        assertEquals("true", log.getTag("isDone"));
        assertEquals("true", mock.getTag("isDone"));

        // Validate same trace
        assertEquals(testProducer.getTag("traceid"), direct.getTag("traceid"));
        assertEquals(testProducer.getTag("traceid"), directSendTo.getTag("traceid"));
        assertEquals(testProducer.getTag("traceid"), directSendFrom.getTag("traceid"));
        assertEquals(testProducer.getTag("traceid"), cxfRs.getTag("traceid"));
        assertEquals(testProducer.getTag("traceid"), rest.getTag("traceid"));
        assertEquals(testProducer.getTag("traceid"), log.getTag("traceid"));
        assertEquals(testProducer.getTag("traceid"), mock.getTag("traceid"));

        // Validate different Exchange ID
        assertNotEquals(testProducer.getTag("exchangeId"), rest.getTag("exchangeId"));
        assertEquals(testProducer.getTag("exchangeId"), direct.getTag("exchangeId"));
        assertEquals(testProducer.getTag("exchangeId"), directSendTo.getTag("exchangeId"));
        assertEquals(testProducer.getTag("exchangeId"), directSendFrom.getTag("exchangeId"));
        assertEquals(testProducer.getTag("exchangeId"), cxfRs.getTag("exchangeId"));
        assertEquals(rest.getTag("exchangeId"), log.getTag("exchangeId"));
        assertEquals(rest.getTag("exchangeId"), mock.getTag("exchangeId"));

        // Validate hierarchy
        assertNull(testProducer.getTag("parentSpan"));
        assertEquals(testProducer.getTag("spanid"), direct.getTag("parentSpan"));
        assertEquals(direct.getTag("spanid"), directSendTo.getTag("parentSpan"));
        assertEquals(directSendTo.getTag("spanid"), directSendFrom.getTag("parentSpan"));
        assertEquals(directSendFrom.getTag("spanid"), cxfRs.getTag("parentSpan"));
        assertEquals(cxfRs.getTag("spanid"), rest.getTag("parentSpan"));
        assertEquals(rest.getTag("spanid"), log.getTag("parentSpan"));
        assertEquals(rest.getTag("spanid"), mock.getTag("parentSpan"));

        // Validate message logging
        assertEquals("A direct message", directSendFrom.getLogEntries().get(0).getFields().get("message"));
        assertEquals("say-hi", rest.getLogEntries().get(0).getFields().get("message"));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .routeId("myRoute")
                        .to("direct:send");

                from("direct:send")
                        .log("A direct message")
                        .to("cxfrs:http://localhost:" + cxfPort
                            + "/rest/helloservice/sayHello?synchronous=false");

                restConfiguration()
                        .port(cxfPort);

                rest("/rest/helloservice")
                    .post("/sayHello")
                    .routeId("rest-GET-say-hi")
                    .to("direct:hi");

                from("direct:hi")
                        .routeId("direct-hi")
                        .delay(2000)
                        .log("say-hi")
                        .to("log:hi")
                        .to("mock:end");
            }
        };
    }

}
