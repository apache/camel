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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
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
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrentSpanTest extends CamelOpenTelemetryTestSupport {
    CurrentSpanTest() {
        super(new SpanTestData[0]);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.addComponent("asyncmock", new AsyncMockComponent());
        context.addComponent("asyncmock1", new AsyncMockComponent());
        context.addComponent("asyncmock2", new AsyncMockComponent());
        context.addComponent("asyncmock3", new AsyncMockComponent());
        context.addComponent("syncmock", new SyncMockComponent());

        return context;
    }

    @Test
    void testSync() {
        SpanTestData[] expectedSpans = {
                new SpanTestData().setLabel("syncmock:result").setUri("syncmock://result").setOperation("syncmock")
                        .setKind(SpanKind.CLIENT),
                new SpanTestData().setLabel("direct:bar").setUri("direct://bar").setOperation("bar"),
                new SpanTestData().setLabel("direct:bar").setUri("direct://bar").setOperation("bar").setKind(SpanKind.CLIENT)
        };

        // sync pipeline
        template.sendBody("direct:bar", "Hello World");

        awaitInvalidSpanContext();

        List<SpanData> spans = verify(expectedSpans, false);
        assertEquals(spans.get(0).getParentSpanId(), spans.get(1).getSpanId());
    }

    @Test
    void testSyncToAsync() {
        SpanTestData[] expectedSpans = {
                new SpanTestData().setLabel("asyncmock1:result").setUri("asyncmock1://result").setOperation("asyncmock1")
                        .setKind(SpanKind.CLIENT),
                new SpanTestData().setLabel("direct:foo").setUri("direct://foo").setOperation("foo"),
                new SpanTestData().setLabel("direct:foo").setUri("direct://foo").setOperation("foo").setKind(SpanKind.CLIENT)
        };

        // sync to async pipeline
        template.sendBody("direct:foo", "Hello World");
        awaitInvalidSpanContext();

        List<SpanData> spans = verify(expectedSpans, false);
        assertEquals(spans.get(0).getParentSpanId(), spans.get(1).getSpanId());

    }

    @Test
    void testAsyncToSync() {
        // direct client spans (event spans) are not created, so we saw only two spans in previous tests
        SpanTestData[] expectedSpans = {
                new SpanTestData().setLabel("syncmock:result").setUri("syncmock://result").setOperation("syncmock")
                        .setKind(SpanKind.CLIENT),
                new SpanTestData().setLabel("asyncmock1:start").setUri("asyncmock1://start").setOperation("asyncmock1"),
                new SpanTestData().setLabel("asyncmock1:start").setUri("asyncmock1://start").setOperation("asyncmock1")
                        .setKind(SpanKind.CLIENT),
        };

        // sync pipeline
        template.sendBody("asyncmock1:start", "Hello World");
        awaitInvalidSpanContext();

        List<SpanData> spans = verify(expectedSpans, false);
        assertEquals(spans.get(0).getParentSpanId(), spans.get(1).getSpanId());
    }

    @Test
    void testAsyncToAsync() {
        SpanTestData[] expectedSpans = {
                new SpanTestData().setLabel("asyncmock2:result").setUri("asyncmock2://result").setOperation("asyncmock2")
                        .setKind(SpanKind.CLIENT),
                new SpanTestData().setLabel("asyncmock2:start").setUri("asyncmock2://start").setOperation("asyncmock2"),
                new SpanTestData().setLabel("asyncmock2:start").setUri("asyncmock2://start").setOperation("asyncmock2")
                        .setKind(SpanKind.CLIENT),
        };

        // async pipeline
        template.sendBody("asyncmock2:start", "Hello World");
        awaitInvalidSpanContext();

        List<SpanData> spans = verify(expectedSpans, false);
        assertEquals(spans.get(0).getParentSpanId(), spans.get(1).getSpanId());
    }

    @Test
    void testAsyncFailure() {
        SpanTestData[] expectedSpans = {
                new SpanTestData().setLabel("asyncmock:fail").setUri("asyncmock://fail").setOperation("asyncmock"),
                new SpanTestData().setLabel("asyncmock:fail").setUri("asyncmock://fail").setOperation("asyncmock")
                        .setKind(SpanKind.CLIENT),
        };

        assertThrows(CamelExecutionException.class, () -> template.sendBody("asyncmock:fail", "Hello World"));
        awaitInvalidSpanContext();

        List<SpanData> spans = verify(expectedSpans, false);
        assertEquals(spans.get(0).getParentSpanId(), spans.get(1).getSpanId());

        assertTrue(spans.get(0).getAttributes().get(AttributeKey.booleanKey("error")));
        assertTrue(spans.get(1).getAttributes().get(AttributeKey.booleanKey("error")));

    }

    @Test
    void testMulticastAsync() {
        SpanTestData[] expectedSpans = {
                new SpanTestData().setLabel("asyncmock1:result").setUri("asyncmock1://result").setOperation("asyncmock1")
                        .setKind(SpanKind.CLIENT),
                new SpanTestData().setLabel("asyncmock2:result").setUri("asyncmock2://result").setOperation("asyncmock2")
                        .setKind(SpanKind.CLIENT),
                new SpanTestData().setLabel("syncmock:result").setUri("syncmock://result").setOperation("syncmock")
                        .setKind(SpanKind.CLIENT),
                new SpanTestData().setLabel("direct:start").setUri("direct://start").setOperation("start"),
                new SpanTestData().setLabel("direct:start").setUri("direct://start").setOperation("start")
                        .setKind(SpanKind.CLIENT)
        };

        // sync pipeline
        template.sendBody("direct:start", "Hello World");
        awaitInvalidSpanContext();

        List<SpanData> spans = verify(expectedSpans, false);
        assertEquals(spans.get(0).getParentSpanId(), spans.get(3).getSpanId());
        assertEquals(spans.get(1).getParentSpanId(), spans.get(3).getSpanId());
        assertEquals(spans.get(2).getParentSpanId(), spans.get(3).getSpanId());
    }

    @Test
    void testContextDoesNotLeak() {
        for (int i = 0; i < 30; i++) {
            template.sendBody("asyncmock3:start", String.valueOf(i));
            awaitInvalidSpanContext();
        }

        verifyTraceSpanNumbers(30, 11);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // sync pipeline
                from("direct:bar").to("syncmock:result");

                // sync to async pipeline
                from("direct:foo").to("asyncmock1:result");

                // async to sync pipeline
                from("asyncmock1:start").to("syncmock:result");

                // async pipeline
                from("asyncmock2:start").to("asyncmock2:result");

                // async fail
                from("asyncmock:fail").process(i -> {
                    throw new IOException("error");
                });

                // multicast pipeline
                from("direct:start").multicast()
                        .to("asyncmock1:result")
                        .to("asyncmock2:result")
                        .to("syncmock:result");

                // stress pipeline
                from("asyncmock3:start").multicast()
                        .aggregationStrategy((oldExchange, newExchange) -> {
                            checkCurrentSpan(newExchange);
                            return newExchange;
                        })
                        .executorService(context.getExecutorServiceManager().newFixedThreadPool(this, "CurrentSpanTest", 10))
                        .streaming()
                        .delay(10)
                        .to("log:line", "asyncmock1:start")
                        .to("log:line", "asyncmock2:start")
                        .to("log:line", "direct:bar")
                        .process(ex -> checkCurrentSpan(ex));
            }
        };
    }

    private static void checkCurrentSpan(Exchange exc) {
        String errorMessage = null;
        if (Span.current() instanceof ReadableSpan) {
            ReadableSpan readable = (ReadableSpan) Span.current();
            errorMessage = String.format(
                    "Current span: name - '%s', kind - '%s', ended - `%s', id - '%s-%s', exchange id - '%s-%s', thread - '%s'\n",
                    readable.getName(), readable.getKind(), readable.hasEnded(),
                    readable.getSpanContext().getTraceId(), readable.getSpanContext().getSpanId(),
                    ActiveSpanManager.getSpan(exc).traceId(), ActiveSpanManager.getSpan(exc).spanId(),
                    Thread.currentThread().getName());

        }

        Awaitility.await()
                .alias(errorMessage)
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(10, TimeUnit.MILLISECONDS)
                .pollDelay(0, TimeUnit.MILLISECONDS)
                .until(() -> !Span.current().getSpanContext().isValid());
    }

    private static class AsyncMockComponent extends MockComponent {

        @Override
        protected Endpoint createEndpoint(String uri, String key, Map<String, Object> parameters) {
            return new AsyncMockEndpoint(this, uri, key);
        }
    }

    private static class AsyncMockEndpoint extends MockEndpoint {
        private static final Executor DELAYED
                = CompletableFuture.delayedExecutor(10L, TimeUnit.MILLISECONDS, new ForkJoinPool(3));

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
                    CompletableFuture.runAsync(() -> {
                    }, DELAYED)
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

    private static class SyncMockComponent extends MockComponent {

        @Override
        protected Endpoint createEndpoint(String uri, String key, Map<String, Object> parameters) {
            return new SyncMockEndpoint(this, uri, key);
        }
    }

    private static class SyncMockEndpoint extends MockEndpoint {
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

    private void awaitInvalidSpanContext() {
        Awaitility.await()
                .alias("Span.current().getSpanContext().isValid() should eventually return false")
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(10, TimeUnit.MILLISECONDS)
                .pollDelay(0, TimeUnit.MILLISECONDS)
                .until(() -> !Span.current().getSpanContext().isValid());
    }

}
