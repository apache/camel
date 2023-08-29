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

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Scope;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.InterceptStrategy;
import org.junit.jupiter.api.Test;

class OpenTelemetryTracingStrategyTest extends CamelOpenTelemetryTestSupport {

    private static SpanTestData[] testdata = {
            new SpanTestData().setLabel("camel-process").setOperation("third-party-span")
                    .setParentId(1),
            new SpanTestData().setLabel("camel-process").setOperation("third-party-processor")
                    .setParentId(6),
            new SpanTestData().setLabel("camel-process").setOperation("direct-processor")
                    .setParentId(3),
            new SpanTestData().setLabel("direct:serviceB").setOperation("serviceB")
                    .setParentId(4),
            new SpanTestData().setLabel("direct:serviceB").setOperation("serviceB")
                    .setKind(SpanKind.CLIENT)
                    .setParentId(5),
            new SpanTestData().setLabel("to:serviceB").setOperation("to-serviceB")
                    .setParentId(6),
            new SpanTestData().setLabel("direct:serviceA").setUri("direct://start").setOperation("serviceA")
                    .setParentId(7),
            new SpanTestData().setLabel("direct:serviceA").setUri("direct://start").setOperation("serviceA")
                    .setKind(SpanKind.CLIENT)
    };

    OpenTelemetryTracingStrategyTest() {
        super(testdata);
    }

    @Test
    void testTracingOfProcessors() {
        template.requestBody("direct:serviceA", "Hello");

        verify();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:serviceA").routeId("serviceA")
                        .process(exchange -> {
                            callThirdPartyInstrumentation();
                        }).id("third-party-processor")
                        .to("direct:serviceB").id("to-serviceB");

                from("direct:serviceB").routeId("serviceB")
                        .process(exchange -> {
                            // noop
                        }).id("direct-processor");
            }

            private void callThirdPartyInstrumentation() throws InterruptedException {
                Span span = getTracer().spanBuilder("third-party-span").startSpan();
                try (Scope ignored = span.makeCurrent()) {
                    span.setAttribute(COMPONENT_KEY, "third-party-component");
                } finally {
                    span.end();
                }
            }
        };
    }

    @Override
    protected Function<OpenTelemetryTracer, InterceptStrategy> getTracingStrategy() {
        return OpenTelemetryTracingStrategy::new;
    }
}
