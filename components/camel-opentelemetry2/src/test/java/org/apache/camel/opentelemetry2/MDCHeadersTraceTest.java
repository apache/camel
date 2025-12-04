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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.opentelemetry2.CamelOpenTelemetryExtension.OtelTrace;
import org.junit.jupiter.api.Test;

public class MDCHeadersTraceTest extends OpenTelemetryTracerTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        OpenTelemetryTracer tst = new OpenTelemetryTracer();
        tst.setTraceHeadersInclusion(true);
        tst.setTracer(otelExtension.getOpenTelemetry().getTracer("traceTest"));
        tst.setContextPropagators(otelExtension.getOpenTelemetry().getPropagators());
        CamelContextAware.trySetCamelContext(tst, context);
        tst.init(context);
        return context;
    }

    @Test
    void testProcessorsTraceRequest() throws InterruptedException, IOException {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        template.sendBody("direct:start", "my-body");
        Map<String, OtelTrace> traces = otelExtension.getTraces();
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
                from("direct:start").to("mock:result");
            }
        };
    }
}
