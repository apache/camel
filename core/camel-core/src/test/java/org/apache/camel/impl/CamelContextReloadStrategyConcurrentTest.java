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
package org.apache.camel.impl;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultContextReloadStrategy;
import org.apache.camel.support.DefaultEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two reloads requested at the same time (for example a secret refresh task and JMX) run one after the other, and the
 * context ends with one copy of each route.
 */
class CamelContextReloadStrategyConcurrentTest {

    // the next creation of the pause endpoint waits, so that a reload is held after it removed the routes
    private final AtomicBoolean pauseNext = new AtomicBoolean();
    private final CountDownLatch firstPaused = new CountDownLatch(1);
    // counted down when another reload creates the pause endpoint, that is after it removed the routes too
    private final CountDownLatch otherCreating = new CountDownLatch(1);

    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private DefaultCamelContext context;

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
        if (context != null) {
            context.stop();
        }
    }

    @Test
    void concurrentReloadsKeepOneCopyOfEachRoute() throws Exception {
        context = new DefaultCamelContext();
        context.addComponent("pause", new PauseComponent());
        DefaultContextReloadStrategy reload = new DefaultContextReloadStrategy();
        context.addService(reload);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("pause:a").routeId("a").log("${body}");
                from("direct:b").routeId("b").log("${body}");
                from("direct:c").routeId("c").log("${body}");
            }
        });
        context.start();
        assertEquals(3, context.getRoutes().size());

        pauseNext.set(true);
        Future<?> first = pool.submit(() -> reload.onReload("first"));
        assertTrue(firstPaused.await(20, TimeUnit.SECONDS), "the first reload creates the routes again");
        // the first reload removed the routes and waits while it creates them again
        Future<?> second = pool.submit(() -> reload.onReload("second"));
        first.get(20, TimeUnit.SECONDS);
        second.get(20, TimeUnit.SECONDS);

        List<String> ids = context.getRoutes().stream().map(Route::getRouteId).sorted().toList();
        assertEquals(List.of("a", "b", "c"), ids, "one copy of each route");
        // a second consumer of direct:b would fail the start of a copy of route b
        assertEquals(0, reload.getFailedCounter(), "no reload fails");
        assertEquals(2, reload.getReloadCounter(), "both reloads run");
        for (String id : ids) {
            assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus(id));
        }
    }

    private final class PauseComponent extends DefaultComponent {
        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters)
                throws Exception {
            if (pauseNext.compareAndSet(true, false)) {
                firstPaused.countDown();
                // with reloads one at a time, the other reload waits for this one, and this wait times out
                otherCreating.await(2, TimeUnit.SECONDS);
            } else if (firstPaused.getCount() == 0) {
                otherCreating.countDown();
            }
            return new DefaultEndpoint(uri, this) {
                @Override
                public Producer createProducer() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public Consumer createConsumer(Processor processor) {
                    return new DefaultConsumer(this, processor);
                }
            };
        }
    }
}
