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

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.ThreadPoolBuilder;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * Tests that the EIP does not hang-threads due to thread-pools being exhausted and uses caller runs to use current
 * thread to process the task
 */
@Isolated
@Timeout(30)
public class SplitParallelThreadPoolCallerRunsTest extends ContextTestSupport {

    @Test
    public void testSplitParallel() throws Exception {
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();

        Runnable r = () -> {
            try {
                template.sendBody("direct:start", List.of(List.of("0-0", "0-1"), List.of("1-0", "1-1")));
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
            Assertions.assertEquals(10, ok.get(), "All should be okay");
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
                        .build("inner");

                from("direct:start").split(body()).parallelProcessing().to("direct:inner?synchronous=true");

                from("direct:inner")
                        .split(body())
                        .executorService(executorService)
                        .log("${body}");
            }
        };
    }
}
