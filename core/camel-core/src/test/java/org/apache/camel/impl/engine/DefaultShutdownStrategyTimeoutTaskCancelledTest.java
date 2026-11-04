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
package org.apache.camel.impl.engine;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.ShutdownRoute;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When the graceful shutdown of a route times out, the thread that stops the route forces it to shutdown. The shutdown
 * task, cancelled (interrupted) by the timeout, must not stop the deferred consumer as well: at the same time, and on
 * an interrupted thread where a consumer can fail to stop.
 */
class DefaultShutdownStrategyTimeoutTaskCancelledTest extends ContextTestSupport {

    private final CountDownLatch release = new CountDownLatch(1);
    // the consumers stopped by the shutdown strategy: the thread, and whether it was interrupted
    private final List<String> stops = new CopyOnWriteArrayList<>();

    @AfterEach
    void releaseExchange() {
        release.countDown();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.setShutdownStrategy(new RecordingShutdownStrategy(context, stops));
        return context;
    }

    @Test
    void testCancelledTaskDoesNotStopDeferredConsumer() throws Exception {
        template.sendBody("seda:a", "A");
        await().atMost(10, TimeUnit.SECONDS).until(() -> context.getInflightRepository().size("a") == 1);

        // the exchange is still in flight, so the graceful shutdown times out and the route is forced to shutdown
        String thread = Thread.currentThread().getName();
        context.getRouteController().stopRoute("a", 1, TimeUnit.SECONDS);
        assertTrue(context.getShutdownStrategy().isTimeoutOccurred());
        assertTrue(context.getRouteController().getRouteStatus("a").isStopped());

        // the cancelled task would stop the deferred consumer right after the interrupt
        await().during(1, TimeUnit.SECONDS).atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(List.of(thread), stops,
                        "the consumer must be stopped once, by the thread that stopped the route"));
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:a").routeId("a").shutdownRoute(ShutdownRoute.Defer)
                        .process(exchange -> release.await(30, TimeUnit.SECONDS));
            }
        };
    }

    // a named class, as the shutdown strategy names its thread pool after its class
    private static class RecordingShutdownStrategy extends DefaultShutdownStrategy {

        private final List<String> stops;

        RecordingShutdownStrategy(CamelContext camelContext, List<String> stops) {
            super(camelContext);
            this.stops = stops;
        }

        @Override
        protected void shutdownNow(String routeId, Consumer consumer) {
            stops.add(Thread.currentThread().getName() + (Thread.currentThread().isInterrupted() ? " interrupted" : ""));
            super.shutdownNow(routeId, consumer);
        }
    }
}
