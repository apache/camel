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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The onCompletion tasks running in the thread pool of a parallel onCompletion are pending exchanges, so a graceful
 * shutdown waits for them.
 */
class OnCompletionParallelProcessingShutdownTest extends ContextTestSupport {

    private final CountDownLatch onCompletionStarted = new CountDownLatch(1);
    private final CountDownLatch camelStopping = new CountDownLatch(1);
    private final AtomicInteger onCompletionDone = new AtomicInteger();
    private final ExecutorService stopper = Executors.newSingleThreadExecutor();

    @AfterEach
    void shutdownStopper() {
        stopper.shutdownNow();
    }

    @Test
    void testGracefulShutdownWaitsForOnCompletion() throws Exception {
        template.sendBody("direct:start", "Hello World");
        assertTrue(onCompletionStarted.await(10, TimeUnit.SECONDS));
        OnCompletionProcessor onCompletion = context.getProcessor("oc", OnCompletionProcessor.class);
        assertEquals(1, onCompletion.getPendingExchangesSize(), "The running onCompletion task should be pending");

        // the exchange is done, and Camel is stopped while its onCompletion is still running
        Future<?> stop = stopper.submit(() -> {
            context.stop();
            return null;
        });
        await().atMost(10, TimeUnit.SECONDS).until(() -> context.isStopping() || context.isStopped());
        camelStopping.countDown();
        stop.get(30, TimeUnit.SECONDS);

        assertEquals(1, onCompletionDone.get(), "The onCompletion should be done");
        assertEquals(0, onCompletion.getPendingExchangesSize(), "No onCompletion task should be pending");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .onCompletion().id("oc").parallelProcessing()
                        .process(e -> {
                            onCompletionStarted.countDown();
                            // the onCompletion takes until Camel is stopping
                            if (camelStopping.await(10, TimeUnit.SECONDS)) {
                                onCompletionDone.incrementAndGet();
                            }
                        })
                        .end()
                        .to("mock:result");
            }
        };
    }
}
