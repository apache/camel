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
package org.apache.camel.component.resilience4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * In asynchronous mode the timeouts run on a scheduled thread pool of the circuit breaker, with 10 threads (the default
 * thread pool profile). A fallback that blocks, such as a call to a backup service, must not hold one of these threads:
 * with 10 fallbacks blocked, the next timeout must still fire.
 */
class ResilienceAsyncTimeoutBlockingFallbackTest extends CamelTestSupport {

    private static final int TIMEOUT_THREADS = 10;

    private final CountDownLatch releaseCalls = new CountDownLatch(1);
    private final CountDownLatch releaseFallbacks = new CountDownLatch(1);
    private final CountDownLatch blockedFallbacks = new CountDownLatch(TIMEOUT_THREADS);
    private final AtomicInteger fallbacks = new AtomicInteger();

    @AfterEach
    void releaseBlockedThreads() {
        releaseFallbacks.countDown();
        releaseCalls.countDown();
    }

    @Test
    void testTimeoutFiresWhileFallbacksBlock() throws Exception {
        List<Future<Object>> blocked = new ArrayList<>();
        for (int i = 0; i < TIMEOUT_THREADS; i++) {
            blocked.add(template.asyncRequestBody("direct:start", "Hello"));
        }
        // the calls block, time out after 200 millis, and their fallbacks block
        assertTrue(blockedFallbacks.await(10, TimeUnit.SECONDS), "ten fallbacks must block");

        // this call blocks too: its timeout must fire, and its fallback (which does not block) must answer
        Future<Object> reply = template.asyncRequestBody("direct:start", "Hello");
        assertEquals("Fallback", reply.get(5, TimeUnit.SECONDS));

        releaseFallbacks.countDown();
        for (Future<Object> future : blocked) {
            assertEquals("Fallback", future.get(5, TimeUnit.SECONDS));
        }
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .circuitBreaker()
                            .resilience4jConfiguration()
                                .asynchronous(true).timeoutEnabled(true).timeoutDuration("200")
                            .end()
                            .process(e -> releaseCalls.await(20, TimeUnit.SECONDS))
                            .setBody(constant("Bye World"))
                        .onFallback()
                            .process(e -> blockFirstFallbacks())
                            .transform().constant("Fallback")
                        .end();
            }
        };
    }

    private void blockFirstFallbacks() throws InterruptedException {
        // the fallbacks of the first 10 exchanges block, as a call to a backup service that does not answer does
        if (fallbacks.incrementAndGet() <= TIMEOUT_THREADS) {
            blockedFallbacks.countDown();
            releaseFallbacks.await(20, TimeUnit.SECONDS);
        }
    }
}
