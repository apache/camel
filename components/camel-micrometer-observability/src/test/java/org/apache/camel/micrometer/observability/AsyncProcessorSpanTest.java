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

import java.util.List;
import java.util.Map;

import io.opentelemetry.sdk.trace.data.SpanData;
import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.micrometer.observability.CamelOpenTelemetryExtension.OtelTrace;
import org.apache.camel.telemetry.Op;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With Micrometer Observation on OpenTelemetry: the steps after an asynchronous delay are children of the route span,
 * and the delay span is ended before them.
 */
class AsyncProcessorSpanTest extends MicrometerObservabilityTracerPropagationTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        // the spans of the core processors (the support class disables them)
        tst.setDisableCoreProcessors(false);
        return context;
    }

    @Test
    void stepsAfterAsyncDelayAreChildrenOfTheRoute() {
        template.sendBody("direct:start", "my-body");
        Map<String, OtelTrace> traces = otelExtension.getTraces();
        assertEquals(1, traces.size());
        List<SpanData> spans = traces.values().iterator().next().getSpans();
        SpanData route = getSpan(spans, "direct://start", Op.EVENT_RECEIVED);
        SpanData delay = byName(spans, "delay-delay");
        SpanData step2 = byName(spans, "step2-setHeader");
        SpanData step3 = byName(spans, "step3-setHeader");
        for (SpanData span : spans) {
            assertTrue(span.hasEnded(), "Span not ended: " + span.getName());
        }
        assertEquals(route.getSpanId(), delay.getParentSpanId());
        assertEquals(route.getSpanId(), step2.getParentSpanId());
        assertEquals(route.getSpanId(), step3.getParentSpanId());
        assertTrue(delay.getEndEpochNanos() <= step2.getStartEpochNanos(), "The delay span ends after the next step starts");
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
                from("direct:start").routeId("start")
                        .delay(10).id("delay")
                        .setHeader("step2", constant("2")).id("step2")
                        .setHeader("step3", constant("3")).id("step3");
            }
        };
    }

}
