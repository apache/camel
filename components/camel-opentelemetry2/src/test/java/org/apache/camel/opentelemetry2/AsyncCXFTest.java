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

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.opentelemetry2.CamelOpenTelemetryExtension.OtelTrace;
import org.apache.camel.telemetry.Op;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * AsyncCXFTest tests the execution of CXF async which was reported as a potential candidate to
 * inconsistent Span creation in async mode.
 */
public class AsyncCXFTest extends OpenTelemetryTracerTestSupport {

    private static int cxfPort = AvailablePortFinder.getNextRandomAvailable();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        OpenTelemetryTracer tst = new OpenTelemetryTracer();
        tst.setTracer(otelExtension.getOpenTelemetry().getTracer("traceTest"));
        tst.setContextPropagators(otelExtension.getOpenTelemetry().getPropagators());
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
        Map<String, OtelTrace> traces = otelExtension.getTraces();
        // Each trace should have a unique trace id. It is enough to assert that
        // the number of elements in the map is the same of the requests to prove
        // all traces have been generated uniquely.
        assertEquals(j, traces.size());
        // Each trace should have the same structure
        for (OtelTrace trace : traces.values()) {
            checkTrace(trace);
        }

    }

    private void checkTrace(OtelTrace trace) {
        List<SpanData> spans = trace.getSpans();
        assertEquals(8, spans.size());
        SpanData testProducer = OpenTelemetryTracerTestSupport.getSpan(spans, "direct://start", Op.EVENT_SENT);
        SpanData direct = OpenTelemetryTracerTestSupport.getSpan(spans, "direct://start", Op.EVENT_RECEIVED);
        SpanData directSendTo = OpenTelemetryTracerTestSupport.getSpan(spans, "direct://send", Op.EVENT_SENT);
        SpanData directSendFrom = OpenTelemetryTracerTestSupport.getSpan(spans, "direct://send", Op.EVENT_RECEIVED);
        SpanData cxfRs = OpenTelemetryTracerTestSupport.getSpan(
                spans,
                "cxfrs://http://localhost:" + cxfPort + "/rest/helloservice/sayHello?synchronous=false",
                Op.EVENT_SENT);
        SpanData rest = OpenTelemetryTracerTestSupport.getSpan(
                spans,
                "rest://post:/rest/helloservice:/sayHello?routeId=direct-hi",
                Op.EVENT_RECEIVED);
        SpanData log = OpenTelemetryTracerTestSupport.getSpan(spans, "log://hi", Op.EVENT_SENT);
        SpanData mock = OpenTelemetryTracerTestSupport.getSpan(spans, "mock://end", Op.EVENT_SENT);

        // Validate span completion
        assertTrue(testProducer.hasEnded());
        assertTrue(direct.hasEnded());
        assertTrue(directSendTo.hasEnded());
        assertTrue(directSendFrom.hasEnded());
        assertTrue(cxfRs.hasEnded());
        assertTrue(rest.hasEnded());
        assertTrue(log.hasEnded());
        assertTrue(mock.hasEnded());

        // Validate same trace
        assertEquals(testProducer.getSpanContext().getTraceId(), direct.getSpanContext().getTraceId());
        assertEquals(testProducer.getSpanContext().getTraceId(), directSendTo.getSpanContext().getTraceId());
        assertEquals(testProducer.getSpanContext().getTraceId(), directSendFrom.getSpanContext().getTraceId());
        assertEquals(testProducer.getSpanContext().getTraceId(), cxfRs.getSpanContext().getTraceId());
        assertEquals(testProducer.getSpanContext().getTraceId(), rest.getSpanContext().getTraceId());
        assertEquals(testProducer.getSpanContext().getTraceId(), log.getSpanContext().getTraceId());
        assertEquals(testProducer.getSpanContext().getTraceId(), mock.getSpanContext().getTraceId());

        // Validate different Exchange ID
        assertNotEquals(testProducer.getAttributes().get(AttributeKey.stringKey("exchangeId")),
                rest.getAttributes().get(AttributeKey.stringKey("exchangeId")));
        assertEquals(testProducer.getAttributes().get(AttributeKey.stringKey("exchangeId")),
                direct.getAttributes().get(AttributeKey.stringKey("exchangeId")));
        assertEquals(testProducer.getAttributes().get(AttributeKey.stringKey("exchangeId")),
                directSendTo.getAttributes().get(AttributeKey.stringKey("exchangeId")));
        assertEquals(testProducer.getAttributes().get(AttributeKey.stringKey("exchangeId")),
                directSendFrom.getAttributes().get(AttributeKey.stringKey("exchangeId")));
        assertEquals(testProducer.getAttributes().get(AttributeKey.stringKey("exchangeId")),
                cxfRs.getAttributes().get(AttributeKey.stringKey("exchangeId")));
        assertEquals(rest.getAttributes().get(AttributeKey.stringKey("exchangeId")),
                log.getAttributes().get(AttributeKey.stringKey("exchangeId")));
        assertEquals(rest.getAttributes().get(AttributeKey.stringKey("exchangeId")),
                mock.getAttributes().get(AttributeKey.stringKey("exchangeId")));

        // Validate hierarchy
        assertFalse(testProducer.getParentSpanContext().isValid());
        assertEquals(testProducer.getSpanContext().getSpanId(), direct.getParentSpanContext().getSpanId());
        assertEquals(direct.getSpanContext().getSpanId(), directSendTo.getParentSpanContext().getSpanId());
        assertEquals(directSendTo.getSpanContext().getSpanId(), directSendFrom.getParentSpanContext().getSpanId());
        assertEquals(directSendFrom.getSpanContext().getSpanId(), cxfRs.getParentSpanContext().getSpanId());
        assertEquals(cxfRs.getSpanContext().getSpanId(), rest.getParentSpanContext().getSpanId());
        assertEquals(rest.getSpanContext().getSpanId(), log.getParentSpanContext().getSpanId());
        assertEquals(rest.getSpanContext().getSpanId(), mock.getParentSpanContext().getSpanId());

        // Validate message logging
        assertEquals("A direct message", directSendFrom.getEvents().get(0).getAttributes().get(
                AttributeKey.stringKey("message")));
        assertEquals("say-hi", rest.getEvents().get(0).getAttributes().get(
                AttributeKey.stringKey("message")));
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
