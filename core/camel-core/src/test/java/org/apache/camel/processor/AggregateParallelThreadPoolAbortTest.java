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

import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.ThreadPoolBuilder;
import org.apache.camel.util.concurrent.ThreadPoolRejectedPolicy;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * Tests that the EIP does not hang-threads due to thread-pools being exhausted and rejects new tasks
 */
@Isolated
@Timeout(30)
public class AggregateParallelThreadPoolAbortTest extends ContextTestSupport {

    @Test
    public void testAggregateParallel() throws Exception {
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();

        Runnable r = () -> {
            try {
                template.sendBody("direct:start", "Body");
                ok.incrementAndGet();
            } catch (Exception e) {
                fail.incrementAndGet();
            }
        };
        var pool = Executors.newFixedThreadPool(10);
        for (int i = 0; i < 10; i++) {
            pool.submit(r);
        }

        Awaitility.await().untilAsserted(() -> {
            Assertions.assertTrue(ok.get() > 0, "Some should ok");
            Assertions.assertTrue(fail.get() > 0, "Some should fail");
        });

        log.info("Errors: {}", fail.get());
        log.info("OK: {}", ok.get());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                final var executorService = new ThreadPoolBuilder(getContext())
                        .poolSize(1)
                        .maxPoolSize(1)
                        .maxQueueSize(0)
                        .rejectedPolicy(ThreadPoolRejectedPolicy.Abort)
                        .build("inner");

                from("direct:start").to("direct:inner?synchronous=true");

                from("direct:inner")
                        .aggregate(constant(true), new BodyInAggregatingStrategy())
                        .executorService(executorService)
                        .completionSize(2)
                        // force delay so threads are not free in the pool causing tasks to be rejected
                        .delay(1000)
                        .log("${body}");
            }
        };
    }
}
