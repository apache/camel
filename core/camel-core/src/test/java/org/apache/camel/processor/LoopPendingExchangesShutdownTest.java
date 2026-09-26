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

import java.util.concurrent.Future;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A loop with a zero or negative count runs no iterations and must not leave anything behind in the pending task count,
 * which the shutdown strategy uses to decide whether it should keep waiting for inflight exchanges.
 */
class LoopPendingExchangesShutdownTest extends ContextTestSupport {

    @Test
    void testNegativeCountLeavesNoPendingTasks() throws Exception {
        MockEndpoint loop = getMockEndpoint("mock:loop");
        loop.expectedMessageCount(3);
        MockEndpoint done = getMockEndpoint("mock:done");
        done.expectedBodiesReceived("a", "b", "c");

        template.sendBodyAndHeader("direct:start", "a", "n", -1);
        template.sendBodyAndHeader("direct:start", "b", "n", 0);
        template.sendBodyAndHeader("direct:start", "c", "n", 3);

        assertMockEndpointsSatisfied();
        assertEquals(0, context.getProcessor("myLoop", LoopProcessor.class).getPendingExchangesSize());
    }

    @Test
    void testGracefulShutdownWaitsForInflightAfterNegativeCount() throws Exception {
        MockEndpoint done = getMockEndpoint("mock:done");
        done.expectedBodiesReceived("first", "slow");

        // a single message with a negative loop count before the shutdown
        template.sendBodyAndHeader("direct:start", "first", "n", -1);

        // this message is inflight when the context is stopped, and only continues once the shutdown has begun
        Future<Exchange> slow = template.asyncSend("direct:start", e -> {
            e.getMessage().setBody("slow");
            e.getMessage().setHeader("n", 1);
        });
        await().atMost(10, SECONDS).until(() -> context.getInflightRepository().size("myRoute") == 1);

        // graceful shutdown must wait for the inflight exchange to complete
        context.stop();

        assertNull(slow.get(10, SECONDS).getException());
        done.assertIsSatisfied();
    }

    @Test
    void testGracefulShutdownWaitsForInflightWithHugeCount() throws Exception {
        MockEndpoint done = getMockEndpoint("mock:hugeDone");
        done.expectedBodiesReceived("huge");

        // the loop breaks on shutdown after its first iteration, which only completes once the shutdown has begun
        Future<Exchange> huge = template.asyncSend("direct:huge", e -> {
            e.getMessage().setBody("huge");
            e.getMessage().setHeader("n", Integer.MAX_VALUE);
        });
        await().atMost(10, SECONDS).until(() -> context.getInflightRepository().size("hugeRoute") == 1);
        LoopProcessor hugeLoop = context.getProcessor("hugeLoop", LoopProcessor.class);

        // graceful shutdown must wait for the inflight exchange to complete
        context.stop();

        assertNull(huge.get(10, SECONDS).getException());
        done.assertIsSatisfied();
        // the loop broke out on shutdown, so the iterations left must no longer be pending
        assertEquals(0, hugeLoop.getPendingExchangesSize());
    }

    private static boolean isStoppingOrStopped(Exchange exchange) {
        return exchange.getContext().isStopping() || exchange.getContext().isStopped();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("myRoute")
                        .loop(header("n")).id("myLoop")
                            .to("mock:loop")
                        .end()
                        .process(e -> {
                            if ("slow".equals(e.getMessage().getBody())) {
                                await().atMost(10, SECONDS).until(() -> isStoppingOrStopped(e));
                            }
                        })
                        .to("mock:done");

                from("direct:huge").routeId("hugeRoute")
                        .loop(header("n")).breakOnShutdown().id("hugeLoop")
                            .process(e -> await().atMost(10, SECONDS).until(() -> isStoppingOrStopped(e)))
                        .end()
                        .to("mock:hugeDone");
            }
        };
    }
}
