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
package org.apache.camel.component.seda;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultThreadPoolFactory;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A graceful stop of a virtualThreadPerTask seda consumer must wait for an exchange that has been polled and
 * dispatched, but whose task has not started running yet.
 */
class ThreadPerTaskSedaConsumerStopTest extends ContextTestSupport {

    private final CountDownLatch dispatched = new CountDownLatch(1);
    private final CountDownLatch startTask = new CountDownLatch(1);
    private final CountDownLatch awaitingTermination = new CountDownLatch(1);

    /**
     * Task executor which delays the start of the tasks, until the test lets them start.
     */
    private final class DelayedStartExecutorService extends AbstractExecutorService {
        private final ExecutorService delegate;

        DelayedStartExecutorService(ExecutorService delegate) {
            this.delegate = delegate;
        }

        @Override
        public void execute(Runnable task) {
            delegate.execute(() -> {
                try {
                    startTask.await(20, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                task.run();
            });
            dispatched.countDown();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            awaitingTermination.countDown();
            return delegate.awaitTermination(timeout, unit);
        }

        @Override
        public void shutdown() {
            delegate.shutdown();
        }

        @Override
        public List<Runnable> shutdownNow() {
            return delegate.shutdownNow();
        }

        @Override
        public boolean isShutdown() {
            return delegate.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return delegate.isTerminated();
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getExecutorServiceManager().setThreadPoolFactory(new DefaultThreadPoolFactory() {
            @Override
            public ExecutorService newCachedThreadPool(ThreadFactory threadFactory) {
                ExecutorService answer = super.newCachedThreadPool(threadFactory);
                // the task executor of the thread-per-task seda consumer (its coordinator is a single thread executor)
                if (threadFactory.newThread(() -> {
                }).getName().endsWith("seda://v")) {
                    answer = new DelayedStartExecutorService(answer);
                }
                return answer;
            }
        });
        return context;
    }

    @Test
    void testStopWaitsForDispatchedExchange() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello");

        template.sendBody("seda:v", "Hello");
        // the exchange has been polled and dispatched, but its task has not started
        assertTrue(dispatched.await(10, TimeUnit.SECONDS));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> stop = executor.submit(() -> {
                context.getRouteController().stopRoute("v");
                return null;
            });
            // let the task start when the stop waits for the tasks to complete (or when the stop completed)
            await().atMost(20, TimeUnit.SECONDS).until(() -> stop.isDone() || awaitingTermination.getCount() == 0);
            startTask.countDown();
            stop.get(20, TimeUnit.SECONDS);
        } finally {
            startTask.countDown();
            executor.shutdownNow();
        }

        // the exchange was processed by the route before it was stopped
        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:v?virtualThreadPerTask=true&pollTimeout=100").routeId("v").to("mock:result");
            }
        };
    }
}
