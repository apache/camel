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
package org.apache.camel.processor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.ThreadPoolProfileBuilder;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When a graceful shutdown times out, the thread pool of a parallel onCompletion is shut down, which drops the
 * onCompletion tasks still queued in it. They must no longer be counted as pending exchanges.
 */
class OnCompletionParallelProcessingForcedShutdownTest extends ContextTestSupport {

    private final CountDownLatch release = new CountDownLatch(1);
    private final AtomicInteger started = new AtomicInteger();

    @Test
    void testForcedShutdownDropsQueuedOnCompletion() throws Exception {
        // the first onCompletion runs (and waits), the second is queued in the pool with one thread
        template.sendBody("direct:start", "A");
        template.sendBody("direct:start", "B");
        await().atMost(10, TimeUnit.SECONDS).until(() -> started.get() == 1);
        OnCompletionProcessor onCompletion = context.getProcessor("oc", OnCompletionProcessor.class);
        assertEquals(2, onCompletion.getPendingExchangesSize());

        // the graceful shutdown times out, and the pool is shut down: the running task is interrupted, and the queued
        // task is dropped
        context.getShutdownStrategy().setTimeout(1);
        context.stop();
        assertTrue(context.getShutdownStrategy().hasTimeoutOccurred());

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(0, onCompletion.getPendingExchangesSize(),
                        "No onCompletion task should be pending after the thread pool is shut down"));
        assertEquals(1, started.get(), "The queued onCompletion should not have run");
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getExecutorServiceManager()
                .registerThreadPoolProfile(new ThreadPoolProfileBuilder("oneThread").poolSize(1).maxPoolSize(1).build());
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .onCompletion().id("oc").parallelProcessing().executorService("oneThread")
                        .process(e -> {
                            started.incrementAndGet();
                            release.await(20, TimeUnit.SECONDS);
                        })
                        .end()
                        .to("mock:result");
            }
        };
    }
}
