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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * In asynchronous mode the timeout must fire also when every thread of the pool that runs the calls is blocked in a
 * call: that is the case the timeout is for.
 */
public class ResilienceAsyncTimeoutBlockedPoolTest extends CamelTestSupport {

    private final CountDownLatch release = new CountDownLatch(1);
    private final ScheduledExecutorService pool = Executors.newScheduledThreadPool(1);

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext ctx = super.createCamelContext();
        // a pool with one thread, so a single blocked call takes every thread of the pool
        ctx.getRegistry().bind("cbPool", pool);
        return ctx;
    }

    @AfterEach
    void releaseBlockedCalls() {
        release.countDown();
        pool.shutdownNow();
    }

    @Test
    public void testTimeoutWhenThePoolIsBlocked() throws Exception {
        Future<Object> reply = template.asyncRequestBody("direct:start", "Hello");

        // the call blocks its thread (as a synchronous http call to a server that does not answer does), and the
        // timeout of 200 millis must give the fallback, without waiting for the call
        assertEquals("Fallback", reply.get(5, TimeUnit.SECONDS));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .circuitBreaker()
                            .resilience4jConfiguration()
                                .asynchronous(true).timeoutEnabled(true).timeoutDuration(200)
                                .timeoutExecutorService("cbPool")
                            .end()
                            .process(e -> release.await(10, TimeUnit.SECONDS))
                            .setBody(constant("Bye World"))
                        .onFallback()
                            .transform().constant("Fallback")
                        .end();
            }
        };
    }
}
