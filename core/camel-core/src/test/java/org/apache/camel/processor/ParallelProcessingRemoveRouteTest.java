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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.ThreadPoolProfileBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A parallel Multicast, Split or Recipient List with its own thread pool, whose route is stopped after the shutdown
 * timeout and then removed while a part is still queued in that pool, must complete (fail) the exchange, not leave it
 * waiting for the part that the shut down pool never runs.
 */
class ParallelProcessingRemoveRouteTest extends ContextTestSupport {

    private final CountDownLatch latch = new CountDownLatch(1);
    private final CountDownLatch blocked = new CountDownLatch(1);

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        // one thread in the thread pool of the EIP, so the second part waits in its queue
        context.getExecutorServiceManager()
                .registerThreadPoolProfile(new ThreadPoolProfileBuilder("one").poolSize(1).maxPoolSize(1).build());
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:block").routeId("block").process(e -> {
                    blocked.countDown();
                    latch.await(20, TimeUnit.SECONDS);
                });
            }
        });
    }

    @Test
    void testMulticastRemoveRoute() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("parallel")
                        .multicast().parallelProcessing().executorService("one")
                        .to("direct:block", "mock:b");
            }
        });
        context.start();
        assertCompletedOnRemove();
    }

    @Test
    void testSplitRemoveRoute() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("parallel")
                        .split(body()).parallelProcessing().executorService("one")
                        .toD("${body}");
            }
        });
        context.start();
        assertCompletedOnRemove();
    }

    @Test
    void testRecipientListRemoveRoute() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("parallel")
                        .recipientList(body()).parallelProcessing().executorService("one");
            }
        });
        context.start();
        assertCompletedOnRemove();
    }

    private void assertCompletedOnRemove() throws Exception {
        CompletableFuture<Exchange> future = template.asyncSend("direct:start",
                e -> e.getMessage().setBody(List.of("direct:block", "mock:b")));
        assertEquals(true, blocked.await(5, TimeUnit.SECONDS));

        try {
            // the shutdown timeout runs out, the route is stopped by force, then removed
            context.getRouteController().stopRoute("parallel", 100, TimeUnit.MILLISECONDS);
            context.removeRoute("parallel");

            Exchange out = future.get(10, TimeUnit.SECONDS);
            assertNotNull(out.getException());
        } finally {
            latch.countDown();
        }
        await().atMost(5, TimeUnit.SECONDS).until(() -> context.getInflightRepository().size() == 0);
    }
}
