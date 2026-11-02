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

import java.util.List;
import java.util.Map;

import io.opentelemetry.sdk.trace.data.SpanData;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.opentelemetry2.CamelOpenTelemetryExtension.OtelTrace;
import org.apache.camel.telemetry.Op;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With the OpenTelemetry SDK: the span of a step that completes on another thread (an asynchronous delay, the threads
 * EIP) ends before the next step of the route starts, and the next steps are children of the route span.
 */
class AsyncProcessorSpanTest extends OpenTelemetryTracerTestSupport {

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
    void stepsAfterAsyncDelayAreChildrenOfTheRoute() {
        check("direct:delay", "async-delay", "delay");
    }

    @Test
    void stepsAfterThreadsAreChildrenOfTheRoute() {
        check("direct:threads", "threads-threads", "threads");
    }

    @Test
    void stepsAfterSyncDelayAreChildrenOfTheRoute() {
        // control: a delay that completes on the same thread
        check("direct:syncdelay", "sync-delay", "sync");
    }

    private void check(String uri, String asyncStep, String prefix) {
        template.sendBody(uri, "my-body");
        Map<String, OtelTrace> traces = otelExtension.getTraces();
        assertEquals(1, traces.size());
        List<SpanData> spans = traces.values().iterator().next().getSpans();
        SpanData route = getSpan(spans, uri.replace(":", "://"), Op.EVENT_RECEIVED);
        SpanData async = byName(spans, asyncStep);
        SpanData step2 = byName(spans, prefix + "Step2-setHeader");
        SpanData step3 = byName(spans, prefix + "Step3-setHeader");
        for (SpanData span : spans) {
            assertTrue(span.hasEnded(), "Span not ended: " + span.getName());
        }
        assertEquals(route.getSpanId(), async.getParentSpanId());
        assertEquals(route.getSpanId(), step2.getParentSpanId());
        assertEquals(route.getSpanId(), step3.getParentSpanId());
        assertTrue(async.getEndEpochNanos() <= step2.getStartEpochNanos(),
                "The span " + asyncStep + " ends after the next step starts");
    }

    private static SpanData byName(List<SpanData> spans, String name) {
        for (SpanData span : spans) {
            if (name.equals(span.getName())) {
                return span;
            }
        }
        throw new IllegalArgumentException("No span " + name + " in " + spans);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // a delay is asynchronous by default
                from("direct:delay").routeId("delayRoute")
                        .delay(10).id("async")
                        .setHeader("step2", constant("2")).id("delayStep2")
                        .setHeader("step3", constant("3")).id("delayStep3");

                from("direct:threads").routeId("threadsRoute")
                        .threads(1).id("threads")
                        .setHeader("step2", constant("2")).id("threadsStep2")
                        .setHeader("step3", constant("3")).id("threadsStep3");

                from("direct:syncdelay").routeId("syncDelayRoute")
                        .delay(10).syncDelayed().id("sync")
                        .setHeader("step2", constant("2")).id("syncStep2")
                        .setHeader("step3", constant("3")).id("syncStep3");
            }
        };
    }

}
