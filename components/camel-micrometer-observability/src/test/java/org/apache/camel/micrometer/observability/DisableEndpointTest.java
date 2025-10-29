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
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class DisableEndpointTest extends MicrometerObservabilityTracerTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        tst.setTraceProcessors(true);
        tst.setExcludePatterns("log*,to*,setVariable*");
        return super.createCamelContext();
    }

    @Test
    void testProcessorsTraceRequest() throws IOException {
        template.sendBody("direct:start", "my-body");
        Map<String, MicrometerObservabilityTrace> traces = traces();
        assertEquals(1, traces.size());
        checkTrace(traces.values().iterator().next());
    }

    @Test
    void testExcludedVariableIsPresent() throws InterruptedException {
        MockEndpoint endpoint = context().getEndpoint("mock:variable", MockEndpoint.class);

        endpoint.expectedMessageCount(1);
        template.sendBody("direct:variable", "Test Message");
        endpoint.assertIsSatisfied();
        Exchange first = endpoint.getReceivedExchanges().get(0);
        String myVar = first.getVariable("myVar", String.class);
        Assertions.assertEquals("testValue", myVar);
    }

    private void checkTrace(MicrometerObservabilityTrace trace) {
        List<SimpleSpan> spans = trace.getSpans();
        assertEquals(2, spans.size());
        SimpleSpan testProducer = spans.get(0);
        SimpleSpan direct = spans.get(1);

        // Validate span completion
        assertNotEquals(Instant.EPOCH, testProducer.getEndTimestamp());
        assertNotEquals(Instant.EPOCH, direct.getEndTimestamp());

        // Validate same trace
        assertEquals(testProducer.getTraceId(), direct.getTraceId());

        // Validate hierarchy
        assertEquals("", testProducer.getParentId());
        assertEquals(testProducer.getSpanId(), direct.getParentId());
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

                from("direct:variable")
                        .setVariable("myVar", constant("testValue"))
                        .to("mock:variable");
            }
        };
    }

}
