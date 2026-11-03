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
package org.apache.camel.component.once;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The once consumer releases the thread that fires it when its route stops, and fires again when the route is started
 * again.
 */
class OnceConsumerLifecycleTest {

    @Test
    void theThreadEndsWhenTheCamelContextStops() throws Exception {
        String name = "onceContextStop" + System.nanoTime();
        CountDownLatch fired = new CountDownLatch(1);
        try (CamelContext context = new DefaultCamelContext()) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("once:" + name + "?delay=0").process(e -> fired.countDown());
                }
            });
            context.start();
            assertTrue(fired.await(10, TimeUnit.SECONDS), "the route did not fire");

            context.stop();

            assertEquals(List.of(), threadsStillAliveOf(name), "threads of the stopped once consumer");
        }
    }

    @Test
    void theThreadEndsWhenTheRouteIsRemovedBeforeItFired() throws Exception {
        String name = "onceRemoved" + System.nanoTime();
        try (CamelContext context = new DefaultCamelContext()) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("once:" + name + "?delay=3600000").routeId("r").to("log:never");
                }
            });
            context.start();

            context.getRouteController().stopRoute("r");
            context.removeRoute("r");

            assertEquals(List.of(), threadsStillAliveOf(name), "threads of the removed once consumer");
        }
    }

    @Test
    void noThreadIsLeftPerRouteThatWasAddedAndRemoved() throws Exception {
        String prefix = "onceMany" + System.nanoTime() + "x";
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            for (int i = 0; i < 20; i++) {
                String routeId = "r" + i;
                CountDownLatch fired = new CountDownLatch(1);
                int n = i;
                context.addRoutes(new RouteBuilder() {
                    @Override
                    public void configure() {
                        from("once:" + prefix + n + "?delay=0").routeId(routeId).process(e -> fired.countDown());
                    }
                });
                assertTrue(fired.await(10, TimeUnit.SECONDS), "route " + routeId + " did not fire");
                context.getRouteController().stopRoute(routeId);
                context.removeRoute(routeId);
            }

            assertEquals(List.of(), threadsStillAliveOf(prefix), "threads of the removed once consumers");
        }
    }

    @Test
    void aRouteThatIsStartedAgainFiresAgain() throws Exception {
        String name = "onceRestart" + System.nanoTime();
        AtomicInteger count = new AtomicInteger();
        Semaphore fired = new Semaphore(0);
        try (CamelContext context = new DefaultCamelContext()) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("once:" + name + "?delay=0").routeId("r").process(e -> {
                        count.incrementAndGet();
                        fired.release();
                    });
                }
            });
            context.start();
            assertTrue(fired.tryAcquire(10, TimeUnit.SECONDS), "the route did not fire");

            context.getRouteController().stopRoute("r");
            context.getRouteController().startRoute("r");

            assertTrue(fired.tryAcquire(10, TimeUnit.SECONDS), "the started again route did not fire");
            assertEquals(2, count.get());
        }
    }

    @Test
    void aRouteFiresAgainWhenTheCamelContextIsStartedAgain() throws Exception {
        String name = "onceContextRestart" + System.nanoTime();
        Semaphore fired = new Semaphore(0);
        try (CamelContext context = new DefaultCamelContext()) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("once:" + name + "?delay=0").process(e -> fired.release());
                }
            });
            context.start();
            assertTrue(fired.tryAcquire(10, TimeUnit.SECONDS), "the route did not fire");

            context.stop();
            context.start();

            assertTrue(fired.tryAcquire(10, TimeUnit.SECONDS), "the route did not fire after the CamelContext restart");
        }
    }

    /**
     * The threads whose name contains the given text and that are still alive once they had up to 10 seconds in all to
     * end (a thread that is shut down ends at once, a leaked timer thread never does).
     */
    private static List<String> threadsStillAliveOf(String text) throws InterruptedException {
        List<String> alive = new ArrayList<>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.getName().contains(text)) {
                t.join(Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())));
                if (t.isAlive()) {
                    alive.add(t.getName() + (t.isDaemon() ? " (daemon)" : " (non-daemon)"));
                }
            }
        }
        return alive;
    }
}
