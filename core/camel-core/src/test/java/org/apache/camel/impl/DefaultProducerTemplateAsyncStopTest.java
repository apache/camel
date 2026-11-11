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
package org.apache.camel.impl;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The future of an async send or request must complete when the template is stopped before the task ran.
 */
public class DefaultProducerTemplateAsyncStopTest extends ContextTestSupport {

    @Test
    public void testQueuedRequestCompletedWhenTemplateStops() throws Exception {
        ProducerTemplate producer = context.createProducerTemplate();
        ExecutorService single = context.getExecutorServiceManager().newSingleThreadExecutor(this, "AsyncStopTest");
        producer.setExecutorService(single);
        CountDownLatch release = occupy(single);

        Future<Object> future = producer.asyncRequestBody("direct:start", "Hello World");
        // shuts down the executor which drops the queued request
        producer.stop();
        release.countDown();

        assertRejected(future);
    }

    @Test
    public void testQueuedFluentRequestCompletedWhenTemplateStops() throws Exception {
        ExecutorService single = context.getExecutorServiceManager().newSingleThreadExecutor(this, "AsyncStopTest");
        FluentProducerTemplate fluent = context.createFluentProducerTemplate()
                .withTemplateCustomizer(t -> t.setExecutorService(single));
        fluent.start();
        CountDownLatch release = occupy(single);

        Future<Object> future = fluent.withBody("Hello World").to("direct:start").asyncRequest();
        fluent.stop();
        release.countDown();

        assertRejected(future);
    }

    @Test
    public void testRequestNotSentWhenExecutorRunsItAfterStop() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        ProducerTemplate producer = context.createProducerTemplate();
        // an executor which only runs the task once the template is stopped, and does not drop it on shutdown
        GatedExecutorService gated = new GatedExecutorService();
        producer.setExecutorService(gated);

        Future<Object> future = producer.asyncRequestBody("direct:start", "Hello World");
        producer.stop();
        gated.open();

        assertRejected(future);
        gated.thread.join(TimeUnit.SECONDS.toMillis(10));
        assertFalse(gated.thread.isAlive());
        mock.assertIsSatisfied();
    }

    @Test
    public void testFailureBeforeSendingCompletesFuture() throws Exception {
        Endpoint broken = new DefaultEndpoint("broken:test", null) {
            @Override
            public Producer createProducer() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Consumer createConsumer(Processor processor) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Exchange createExchange(ExchangePattern pattern) {
                throw new IllegalStateException("Forced");
            }
        };
        broken.setCamelContext(context);

        Future<Object> future = template.asyncRequestBody(broken, "Hello World");

        ExecutionException e = assertThrows(ExecutionException.class, () -> future.get(10, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, e.getCause());
        assertEquals("Forced", e.getCause().getMessage());
    }

    @Test
    public void testRequestCompletesNormallyAfterRestart() throws Exception {
        ProducerTemplate producer = context.createProducerTemplate();
        assertEquals("Bye World", producer.asyncRequestBody("direct:start", "Hello World").get(10, TimeUnit.SECONDS));

        producer.stop();
        producer.start();

        assertEquals("Bye World", producer.asyncRequestBody("direct:start", "Hello World").get(10, TimeUnit.SECONDS));
    }

    private static CountDownLatch occupy(ExecutorService single) throws InterruptedException {
        CountDownLatch busy = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        // keep the only thread busy so the next task stays queued
        single.execute(() -> {
            busy.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(busy.await(10, TimeUnit.SECONDS));
        return release;
    }

    private static void assertRejected(Future<?> future) {
        ExecutionException e = assertThrows(ExecutionException.class, () -> future.get(10, TimeUnit.SECONDS));
        assertInstanceOf(RejectedExecutionException.class, e.getCause());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("mock:result").transform(constant("Bye World"));
            }
        };
    }

    private static final class GatedExecutorService extends AbstractExecutorService {
        private final CountDownLatch gate = new CountDownLatch(1);
        private volatile Thread thread;

        void open() {
            gate.countDown();
        }

        @Override
        public void execute(Runnable command) {
            thread = new Thread(() -> {
                try {
                    gate.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                command.run();
            });
            thread.start();
        }

        @Override
        public void shutdown() {
            // keeps running the task
        }

        @Override
        public List<Runnable> shutdownNow() {
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }
}
