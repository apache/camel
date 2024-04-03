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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.InterceptStrategy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.apache.camel.test.junit5.TestSupport.fileUri;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OpenTelemetryTracingStrategyPropagateContextTest extends CamelOpenTelemetryTestSupport {

    @TempDir
    private static Path tempDirectory;

    private final static SpanTestData[] testdata = {
            new SpanTestData().setLabel("camel-process").setOperation("delayed")
                    .setParentId(2),
            new SpanTestData().setLabel("camel-process").setOperation("WithSpan.secondMethod")
                    .setParentId(2),
            new SpanTestData().setLabel("camel-process").setOperation("file").setKind(SpanKind.SERVER)
    };

    OpenTelemetryTracingStrategyPropagateContextTest() {
        super(testdata);
    }

    @BeforeAll
    public static void createFile() throws IOException {
        Files.createFile(tempDirectory.resolve("file.txt"));
    }

    @Test
    void testTracingOfProcessors() throws IOException, InterruptedException {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1).create();
        assertTrue(notify.matches(30, TimeUnit.SECONDS));
        verify(true);
    }

    @Override
    protected String getExcludePatterns() {
        return "longRunningProcess";
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri(tempDirectory)).routeId("serviceA")
                        .process(exchange -> {
                            longRunningProcess();
                        }).id("longRunningProcess")
                        .delay(simple("${random(0,500)}")).id("delayed");
            }

            private void longRunningProcess() {
                firstMethod();
                secondMethod();
            }

            private void firstMethod() {
                // no Span created by Camel
            }

            // Simulate io.opentelemetry.instrumentation.annotations.@WithSpan
            // in order to avoid having to start an HTTP sever just to collect the Spans
            // see https://github.com/open-telemetry/opentelemetry-java-examples/tree/main/telemetry-testing
            //@WithSpan
            public void secondMethod() {
                // The Context should be propagated
                Assertions.assertNotSame(Context.root(), Context.current(), "OpenTelemetry was not propagated !");
                // build and start a custom Span similar to what @WithSpan would do
                SpanBuilder builder = getOttracer().getTracer().spanBuilder("WithSpan.secondMethod");
                Span span = builder.setParent(Context.current())
                        .setAttribute(COMPONENT_KEY, "custom")
                        .startSpan();
                //noinspection EmptyTryBlock
                try (Scope ignored = span.makeCurrent()) {
                    // do work
                } finally {
                    span.end();
                }

            }
        };
    }

    @Override
    protected Function<OpenTelemetryTracer, InterceptStrategy> getTracingStrategy() {
        return (tracer) -> {
            OpenTelemetryTracingStrategy strategy = new OpenTelemetryTracingStrategy(tracer);
            strategy.setPropagateContext(true);
            return strategy;
        };
    }
}
