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

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.telemetry.mock.MockTrace;
import org.apache.camel.telemetry.mock.MockTracer;
import org.apache.camel.test.junit6.ExchangeTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class HeadersTraceTest extends ExchangeTestSupport {

    MockTracer mockTracer;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        this.mockTracer = new MockTracer();
        mockTracer.setTraceHeadersInclusion(true);
        CamelContextAware.trySetCamelContext(mockTracer, context);
        mockTracer.init(context);
        return context;
    }

    @Test
    void testProcessorsTraceRequest() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        template.sendBody("direct:start", "my-body");
        Map<String, MockTrace> traces = mockTracer.traces();
        assertEquals(1, traces.size());
        mock.assertIsSatisfied();
        Map<String, Object> headers = mock.getExchanges().get(0).getIn().getHeaders();

        // NOTE: the check on TRACE_ID and SPAN_ID instead of the related constant is on purpose
        // We want to fail if there is any change in the constant by any chance and report into the
        // documentation.
        assertNotNull(headers.get("CAMEL_TRACE_ID"));
        assertNotNull(headers.get("CAMEL_SPAN_ID"));
        assertNotEquals("", headers.get("CAMEL_TRACE_ID"));
        assertNotEquals("", headers.get("CAMEL_SPAN_ID"));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("mock:result");
            }
        };
    }

}
