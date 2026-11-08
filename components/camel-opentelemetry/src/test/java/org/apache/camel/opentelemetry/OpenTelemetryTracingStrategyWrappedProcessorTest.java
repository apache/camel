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
package org.apache.camel.opentelemetry;

import java.util.function.Function;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.support.processor.DelegateAsyncProcessor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Another intercept strategy wraps the processors before the OpenTelemetry strategy does. The OpenTelemetry strategy
 * must still recognise the correlation context processors, as otherwise the baggage is set on a context of its own and
 * lost.
 */
@Deprecated(since = "4.19.0")
class OpenTelemetryTracingStrategyWrappedProcessorTest extends CamelOpenTelemetryTestSupport {

    OpenTelemetryTracingStrategyWrappedProcessorTest() {
        super(new SpanTestData[0]);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        // added after the OpenTelemetry strategy, so it is applied first and the OpenTelemetry strategy gets its wrapper
        InterceptStrategy wrapping = (camelContext, definition, target, nextTarget) -> new DelegateAsyncProcessor(target);
        context.getCamelContextExtension().addInterceptStrategy(wrapping);
        return context;
    }

    @Override
    protected String getExcludePatterns() {
        return "propagate-*";
    }

    @Override
    protected Function<OpenTelemetryTracer, InterceptStrategy> getTracingStrategy() {
        return tracer -> {
            OpenTelemetryTracingStrategy strategy = new OpenTelemetryTracingStrategy(tracer);
            strategy.setPropagateContext(true);
            return strategy;
        };
    }

    @Test
    void correlationContextProcessorsWithProcessorSpans() {
        Exchange result = template.request("direct:traced", exchange -> exchange.getIn().setBody("Hello"));

        assertEquals("baggage-value", result.getMessage().getHeader("baggage-header", String.class));
    }

    @Test
    void correlationContextProcessorsWithPropagatedContext() {
        Exchange result = template.request("direct:propagated", exchange -> exchange.getIn().setBody("Hello"));

        assertEquals("baggage-value", result.getMessage().getHeader("baggage-header", String.class));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:traced").routeId("traced")
                        .process(new SetCorrelationContextProcessor("a-baggage", constant("baggage-value"))).id("set")
                        .process(new GetCorrelationContextProcessor("a-baggage", "baggage-header")).id("get");

                // excluded from tracing, so the strategy only propagates the context to these processors
                from("direct:propagated").routeId("propagated")
                        .process(new SetCorrelationContextProcessor("a-baggage", constant("baggage-value")))
                        .id("propagate-set")
                        .process(new GetCorrelationContextProcessor("a-baggage", "baggage-header")).id("propagate-get");
            }
        };
    }
}
