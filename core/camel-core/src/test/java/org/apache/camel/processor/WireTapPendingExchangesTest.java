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
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The tapped exchanges waiting in the thread pool are pending exchanges, so a graceful shutdown waits for them.
 */
public class WireTapPendingExchangesTest extends ContextTestSupport {

    private final ExecutorService pool = Executors.newSingleThreadExecutor();

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        pool.shutdownNow();
    }

    @Test
    public void testQueuedTapsArePending() throws Exception {
        getMockEndpoint("mock:tap").expectedMessageCount(3);

        // occupy the single thread of the pool, so the tapped exchanges wait in its queue
        CountDownLatch latch = new CountDownLatch(1);
        pool.submit(() -> {
            latch.await(10, TimeUnit.SECONDS);
            return null;
        });

        template.sendBody("direct:start", "A");
        template.sendBody("direct:start", "B");
        template.sendBody("direct:start", "C");

        WireTapProcessor tap = context.getProcessor("tap", WireTapProcessor.class);
        assertEquals(3, tap.getPendingExchangesSize(), "the queued tapped exchanges should be pending");

        latch.countDown();
        assertMockEndpointsSatisfied();
        // the task is done just after the tapped exchange has been received
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(0, tap.getPendingExchangesSize()));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").wireTap("direct:tap").executorService(pool).id("tap").to("mock:result");

                from("direct:tap").to("mock:tap");
            }
        };
    }
}
