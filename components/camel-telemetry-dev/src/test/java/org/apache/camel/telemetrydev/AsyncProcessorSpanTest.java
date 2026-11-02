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
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.telemetry.Op;
import org.apache.camel.telemetry.TagConstants;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The steps after an asynchronous delay are children of the route span (and not of the delay span).
 */
class AsyncProcessorSpanTest extends TelemetryDevTracerTestSupport {

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
    void stepsAfterAsyncDelayAreChildrenOfTheRoute() throws IOException {
        template.sendBody("direct:start", "my-body");
        // the trace is written to the log when all its spans are ended
        await().atMost(10, TimeUnit.SECONDS).until(() -> tracesFromLog().size() == 1);
        Map<String, DevTrace> traces = tracesFromLog();
        List<DevSpanAdapter> spans = traces.values().iterator().next().getSpans();
        // the template's send, the route, the delay and two steps
        assertEquals(5, spans.size());
        DevSpanAdapter route = getSpan(spans, "direct://start", Op.EVENT_RECEIVED);
        DevSpanAdapter delay = processorSpan(spans, "delay-delay");
        DevSpanAdapter step2 = processorSpan(spans, "step2-setHeader");
        DevSpanAdapter step3 = processorSpan(spans, "step3-setHeader");
        assertEquals(route.getTag("spanid"), delay.getTag("parentSpan"));
        assertEquals(route.getTag("spanid"), step2.getTag("parentSpan"));
        assertEquals(route.getTag("spanid"), step3.getTag("parentSpan"));
    }

    private static DevSpanAdapter processorSpan(List<DevSpanAdapter> spans, String component) {
        for (DevSpanAdapter span : spans) {
            if (component.equals(span.getTag(TagConstants.COMPONENT))
                    && Op.EVENT_PROCESS.toString().equals(span.getTag(TagConstants.OP))) {
                return span;
            }
        }
        throw new IllegalArgumentException("No processor span " + component);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // a delay is asynchronous by default
                from("direct:start").routeId("start")
                        .delay(10).id("delay")
                        .setHeader("step2", constant("2")).id("step2")
                        .setHeader("step3", constant("3")).id("step3");
            }
        };
    }

}
