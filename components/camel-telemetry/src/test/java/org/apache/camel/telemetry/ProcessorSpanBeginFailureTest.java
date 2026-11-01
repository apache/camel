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
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.telemetry.mock.MockSpanAdapter;
import org.apache.camel.telemetry.mock.MockTrace;
import org.apache.camel.telemetry.mock.MockTracer;
import org.apache.camel.test.junit6.ExchangeTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * When the span of a step cannot be begun, the end of that step does not end another span (the span on top of the
 * exchange's span stack is then the span of the route).
 */
class ProcessorSpanBeginFailureTest extends ExchangeTestSupport {

    MockTracer mockTracer;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        this.mockTracer = new FailingBeginTracer();
        CamelContextAware.trySetCamelContext(mockTracer, context);
        mockTracer.init(context);
        return context;
    }

    @Test
    void stepAfterAFailedBeginIsAChildOfTheRoute() {
        template.sendBody("direct:start", "my-body");
        Map<String, MockTrace> traces = mockTracer.traces();
        assertEquals(1, traces.size());
        List<Span> spans = traces.values().iterator().next().spans();
        // the template's send, the route, and the step after the failed one (the failed one has no span)
        assertEquals(3, spans.size(), "Spans: " + spans);
        MockSpanAdapter route = SpanTestSupport.getSpan(spans, "direct://start", Op.EVENT_RECEIVED);
        MockSpanAdapter after = (MockSpanAdapter) spans.get(2);
        assertEquals("after-setHeader", after.getTag(TagConstants.COMPONENT));
        assertEquals(route.getTag("spanid"), after.getTag("parentSpan"));
        for (Span s : spans) {
            assertEquals("true", ((MockSpanAdapter) s).getTag("isDone"), "Span not ended: " + s);
        }
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // the failure to begin the span is set on the exchange, and continued here
                onException(IllegalStateException.class).continued(true);

                from("direct:start").routeId("start")
                        .setHeader("a", constant("1")).id("boom")
                        .setHeader("b", constant("2")).id("after");
            }
        };
    }

    /**
     * A tracer whose span creation fails for the step with the id boom.
     */
    private static class FailingBeginTracer extends MockTracer {
        @Override
        protected void initTracer() {
            super.initTracer();
            SpanLifecycleManager delegate = getSpanLifecycleManager();
            setSpanLifecycleManager(new SpanLifecycleManager() {
                @Override
                public Span create(
                        String spanName, String spanKind, Span parent, SpanContextPropagationExtractor extractor) {
                    if (spanName.startsWith("boom-")) {
                        throw new IllegalStateException("Cannot create the span " + spanName);
                    }
                    return delegate.create(spanName, spanKind, parent, extractor);
                }

                @Override
                public void activate(Span span) {
                    delegate.activate(span);
                }

                @Override
                public void deactivate(Span span) {
                    delegate.deactivate(span);
                }

                @Override
                public void close(Span span) {
                    delegate.close(span);
                }

                @Override
                public void inject(Span span, SpanContextPropagationInjector injector, boolean includeTracing) {
                    delegate.inject(span, injector, includeTracing);
                }
            });
        }
    }
}
