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
package org.apache.camel.opentracing;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
import io.opentracing.util.ThreadLocalScopeManager;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.util.function.ThrowingRunnable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Validates that {@link OpenTracingTracer} is compatible with third-party tracing solutions, i.e. integrates into
 * active traces and allows others to integrate into Camel-initialized traces.
 */
public class ThirdPartyInstrumentationIT extends CamelTestSupport {

    private MockTracer tracer;

    @Override
    protected void doPostSetup() {
        tracer.reset();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        tracer = new MockTracer(new ThreadLocalScopeManager(), MockTracer.Propagator.TEXT_MAP);

        OpenTracingTracer openTracingTracer = new OpenTracingTracer();
        openTracingTracer.setTracer(tracer);
        openTracingTracer.setTracingStrategy(new OpenTracingTracingStrategy(openTracingTracer));
        openTracingTracer.init(context);
        return context;
    }

    @Test
    public void testBasicIntegrationUseCase() {
        template.requestBody("direct:DirectProcessor", "");

        List<MockSpan> actualSpans = tracer.finishedSpans();
        assertEquals(3, actualSpans.size(), "Unexpected spans registered");

        Set<Long> traceIds = getTraceIds(actualSpans);
        assertEquals(1, traceIds.size(), () -> "Expected all spans belonging to the same trace, but got: " + traceIds);
    }

    @Test
    public void testTracingContextIsCleanedUp() {
        // execute the route multiple times on the same thread to ensure active trace is reset upon route completion
        template.requestBody("direct:DirectProcessor", "");
        template.requestBody("direct:DirectProcessor", "");

        List<MockSpan> actualSpans = tracer.finishedSpans();
        assertEquals(6, actualSpans.size(), "Unexpected spans registered");

        Set<Long> traceIds = getTraceIds(actualSpans);
        assertEquals(2, traceIds.size(), () -> "Expected all spans belonging to two traces, but got: " + traceIds);
    }

    @Test
    public void testCamelIntegratesIntoActiveTrace() {
        executeInThirdPartySpan(() -> template.requestBody("direct:DirectProcessor", ""));

        List<MockSpan> actualSpans = tracer.finishedSpans();
        assertEquals(4, actualSpans.size(), "Unexpected spans registered");

        Set<Long> traceIds = getTraceIds(actualSpans);
        assertEquals(1, traceIds.size(), "Expected all spans belonging to the same trace, but got: " + traceIds);
    }

    private Set<Long> getTraceIds(List<MockSpan> actualSpans) {
        return actualSpans.stream()
                .map(span -> span.context().traceId())
                .collect(Collectors.toSet());
    }

    /**
     * Simulates third-party instrumentation
     */
    private void tracingProcessor(Exchange exchange) {
        executeInThirdPartySpan(() -> Thread.sleep(1000));
    }

    private void executeInThirdPartySpan(ThrowingRunnable<Exception> closure) {
        Tracer.SpanBuilder spanBuilder = tracer.buildSpan("third-party-span")
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER);
        Span span = spanBuilder.start();
        try (Scope ignored = tracer.scopeManager().activate(span)) {
            closure.run();
        } catch (Exception e) {
            Tags.ERROR.set(span, Boolean.TRUE);
        } finally {
            span.finish();
        }
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:DirectProcessor")
                        .process(ThirdPartyInstrumentationIT.this::tracingProcessor);
            }
        };
    }
}
