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
 * When a graceful shutdown times out, the thread pool of the wire tap is shut down, which drops the tapped exchanges
 * still queued in it. They must no longer be counted as pending exchanges.
 */
class WireTapForcedShutdownTest extends ContextTestSupport {

    private final CountDownLatch release = new CountDownLatch(1);
    private final AtomicInteger started = new AtomicInteger();

    @Test
    void testForcedShutdownDropsQueuedTap() throws Exception {
        try {
            // the first tapped exchange runs (and waits), the second is queued in the pool with one thread
            template.sendBody("direct:start", "A");
            template.sendBody("direct:start", "B");
            await().atMost(10, TimeUnit.SECONDS).until(() -> started.get() == 1);
            WireTapProcessor tap = context.getProcessor("tap", WireTapProcessor.class);
            assertEquals(2, tap.getPendingExchangesSize());

            // the graceful shutdown times out, and the pool is shut down: the running task is interrupted, and the
            // queued task is dropped
            context.getShutdownStrategy().setTimeout(1);
            context.stop();
            assertTrue(context.getShutdownStrategy().hasTimeoutOccurred());

            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertEquals(0, tap.getPendingExchangesSize(),
                            "No tapped exchange should be pending after the thread pool is shut down"));
            assertEquals(1, started.get(), "The queued tapped exchange should not have been sent");
        } finally {
            release.countDown();
        }
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
                from("direct:start").wireTap("direct:tap").executorService("oneThread").id("tap").to("mock:result");

                from("direct:tap")
                        .process(e -> {
                            started.incrementAndGet();
                            release.await(20, TimeUnit.SECONDS);
                        });
            }
        };
    }
}
