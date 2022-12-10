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

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.tracing.ActiveSpanManager;
import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CurrentSpanTests extends CamelOpenTelemetryTestSupport {
    private final static Executor DELAYED = CompletableFuture.delayedExecutor(100L, TimeUnit.MILLISECONDS);

    CurrentSpanTests() {
        super(new SpanTestData[0]);
    }

    @Test
    void testSync() {
        SpanTestData[] expectedSpans = {
                new SpanTestData().setLabel("syncmock:result").setUri("syncmock://result").setOperation("syncmock").setKind(SpanKind.CLIENT),
                new SpanTestData().setLabel("direct:bar").setUri("direct://bar").setOperation("bar").setKind(SpanKind.INTERNAL),
        };

        // sync pipeline
        template.sendBody("direct:bar", "Hello World");

        List<SpanData> spans = verify(expectedSpans, false);
        assertEquals(spans.get(0).getParentSpanId(), spans.get(1).getSpanId());

        // validates that span was active in async producer's processor
        assertFalse(Span.current().getSpanContext().isValid());
    }

    @Test
    void testSyncToAsync() {
        SpanTestData[] expectedSpans = {
                new SpanTestData().setLabel("asyncmock1:result").setUri("asyncmock1://result").setOperation("asyncmock1").setKind(SpanKind.CLIENT),
                new SpanTestData().setLabel("direct:foo").setUri("direct://foo").setOperation("foo").setKind(SpanKind.INTERNAL),
        };

        // sync to async pipeline
        template.sendBody("direct:foo", "Hello World");

        List<SpanData> spans = verify(expectedSpans, false);
        assertEquals(spans.get(0).getParentSpanId(), spans.get(1).getSpanId());

        // context is cleaned up
        assertFalse(Span.current().getSpanContext().isValid());
    }

    @Test
    void testAsyncToSync() {
        // direct client spans (event spans) are not created, so we saw only two spans in previous tests
        SpanTestData[] expectedSpans = {
                new SpanTestData().setLabel("syncmock:result").setUri("syncmock://result").setOperation("syncmock").setKind(SpanKind.CLIENT),
                new SpanTestData().setLabel("asyncmock1:start").setUri("asyncmock1://start").setOperation("asyncmock1").setKind(SpanKind.INTERNAL),
                new SpanTestData().setLabel("asyncmock1:start").setUri("asyncmock1://start").setOperation("asyncmock1").setKind(SpanKind.CLIENT),
        };

        // sync pipeline
        template.sendBody("asyncmock1:start", "Hello World");

        List<SpanData> spans = verify(expectedSpans, false);
        assertEquals(spans.get(0).getParentSpanId(), spans.get(1).getSpanId());
        assertFalse(Span.current().getSpanContext().isValid());
    }

    @Test
    void testAsyncToAsync() {
        SpanTestData[] expectedSpans = {
                new SpanTestData().setLabel("asyncmock2:result").setUri("asyncmock2://result").setOperation("asyncmock2").setKind(SpanKind.CLIENT),
                new SpanTestData().setLabel("asyncmock2:start").setUri("asyncmock2://start").setOperation("asyncmock2").setKind(SpanKind.INTERNAL),
                new SpanTestData().setLabel("asyncmock2:start").setUri("asyncmock2://start").setOperation("asyncmock2").setKind(SpanKind.CLIENT),
        };

        // sync pipeline
        template.sendBody("asyncmock2:start", "Hello World");

        List<SpanData> spans = verify(expectedSpans, false);
        assertEquals(spans.get(0).getParentSpanId(), spans.get(1).getSpanId());
        assertFalse(Span.current().getSpanContext().isValid());
    }

    @Test
    void testContextDoesNotLeak() {
        for (int i = 0; i < 30; i ++) {
            template.sendBody("asyncmock3:start", String.valueOf(i));
            assertFalse(Span.current().getSpanContext().isValid());
        }

        verifyTraceSpanNumbers(30, 3);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.addComponent("asyncmock1", new AsyncMockComponent());
                context.addComponent("asyncmock2", new AsyncMockComponent());
                context.addComponent("asyncmock3", new AsyncMockComponent());
                context.addComponent("syncmock", new SyncMockComponent());

                // sync pipeline
                from("direct:bar").to("syncmock:result");

                // sync to async pipeline
                from("direct:foo").to("asyncmock1:result");

                // async to sync pipeline
                from("asyncmock1:start").to("syncmock:result");

                // async pipeline
                from("asyncmock2:start").to("asyncmock2:result");

                // stress pipeline
                from("asyncmock3:start").multicast()
                        .aggregationStrategy((oldExchange, newExchange) -> {
                            // context is cleaned up
                            assertFalse(Span.current().getSpanContext().isValid());
                            return newExchange;
                        })
                        .executorService(Executors.newFixedThreadPool(10))
                        .parallelProcessing()
                        .streaming()
                        .delay(10)
                        .to("log:line", "asyncmock3:result")
                        .process((ignored) -> assertFalse(Span.current().getSpanContext().isValid()));
            }
        };
    }

    private class AsyncMockComponent extends MockComponent {

        @Override
        protected Endpoint createEndpoint(String uri, String key, Map<String, Object> parameters) {
            return new AsyncMockEndpoint(this, uri, key);
        }
    }

    private static class AsyncMockEndpoint extends MockEndpoint {
        private Consumer consumer;
        private final String key;

        public AsyncMockEndpoint(AsyncMockComponent component, String uri, String key) {
            super(uri, component);
            this.key = key;
        }

        @Override
        public Consumer createConsumer(Processor processor) {
            consumer = new DefaultConsumer(this, exchange -> {
                        assertCurrentSpan(exchange);
                        processor.process(exchange);
                    });
            try {
                configureConsumer(consumer);
            } catch (Exception e) {
                // ignore
            }
            return consumer;
        }

        @Override
        public Producer createProducer() {
            return new DefaultAsyncProducer(this) {
                @Override
                public boolean process(Exchange exchange, AsyncCallback callback) {
                    assertCurrentSpan(exchange);
                    if (!key.equals("result")) {
                        try {
                            getConsumer(1000).getProcessor().process(exchange);
                        } catch (Exception e) {
                            fail(e);
                        }
                    }
                    CompletableFuture.runAsync(() -> {}, DELAYED)
                            .thenRun(() -> callback.run());

                    return false;
                }
            };
        }

        private Consumer getConsumer(long timeout) throws InterruptedException {
            StopWatch watch = new StopWatch();
            while (consumer == null) {
                long rem = timeout - watch.taken();
                if (rem <= 0) {
                    break;
                }
                consumer.wait(rem);
            }
            return consumer;
        }
    }

    private class SyncMockComponent extends MockComponent {

        @Override
        protected Endpoint createEndpoint(String uri, String key, Map<String, Object> parameters) {
            return new SyncMockEndpoint(this, uri, key);
        }
    }

    private class SyncMockEndpoint extends MockEndpoint {
        public SyncMockEndpoint(SyncMockComponent component, String uri, String key) {
            super(uri, component);
        }

        @Override
        public Producer createProducer() {
            return new DefaultProducer(this) {
                @Override
                public void process(Exchange exchange) {
                    assertCurrentSpan(exchange);
                }
            };
        }
    }

    private static void assertCurrentSpan(Exchange exchange) {
        assertEquals(Span.current().getSpanContext().getSpanId(), ActiveSpanManager.getSpan(exchange).spanId());
    }
}
