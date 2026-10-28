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
package org.apache.camel.dsl.xml.io;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.RoutesLoader;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Calling updateRoutes from several threads on the same CamelContext (CAMEL-25093).
 */
class XmlConcurrentUpdateRoutesTest {

    private static final AtomicInteger GATED_CALLS = new AtomicInteger();
    private static final AtomicInteger OTHER_CALLS = new AtomicInteger();
    private static volatile CountDownLatch gatedEntered;
    private static volatile CountDownLatch gatedRelease;

    @BeforeEach
    void reset() {
        GATED_CALLS.set(0);
        OTHER_CALLS.set(0);
        gatedEntered = new CountDownLatch(1);
        gatedRelease = new CountDownLatch(1);
    }

    /**
     * Fails the first time (so it is registered again when the routes are configured), and the second time waits for
     * the test.
     */
    public static class GatedBean {
        public GatedBean() throws InterruptedException {
            int call = GATED_CALLS.incrementAndGet();
            if (call == 1) {
                throw new IllegalStateException("Not yet");
            } else if (call == 2) {
                gatedEntered.countDown();
                gatedRelease.await(20, TimeUnit.SECONDS);
            }
        }
    }

    /**
     * Fails the first time (so it is registered again when the routes are configured).
     */
    public static class OtherBean {
        public OtherBean() {
            if (OTHER_CALLS.incrementAndGet() == 1) {
                throw new IllegalStateException("Not yet");
            }
        }
    }

    @Test
    void testConcurrentUpdateRoutes() throws Exception {
        Resource first = ResourceHelper.fromString("first.xml", camel("gated", GatedBean.class, "first"));
        Resource second = ResourceHelper.fromString("second.xml", camel("other", OtherBean.class, "second"));

        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            RoutesLoader loader = PluginHelper.getRoutesLoader(context);

            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                // the first update waits while it registers the beans that failed when the resource was pre-parsed
                Future<?> one = executor.submit(() -> loader.updateRoutes(first));
                assertTrue(gatedEntered.await(20, TimeUnit.SECONDS));

                // the second update runs meanwhile (or waits for the first)
                AtomicReference<Thread> thread = new AtomicReference<>();
                Future<?> two = executor.submit(() -> {
                    thread.set(Thread.currentThread());
                    return loader.updateRoutes(second);
                });
                await().atMost(20, TimeUnit.SECONDS).until(() -> two.isDone()
                        || OTHER_CALLS.get() == 0 && thread.get() != null
                                && thread.get().getState() == Thread.State.WAITING);
                gatedRelease.countDown();

                assertDoesNotThrow(() -> one.get(20, TimeUnit.SECONDS), "The first updateRoutes should not fail");
                assertDoesNotThrow(() -> two.get(20, TimeUnit.SECONDS), "The second updateRoutes should not fail");
            } finally {
                gatedRelease.countDown();
                executor.shutdownNow();
            }

            assertNotNull(context.getRoute("first"), "Route first should be added");
            assertNotNull(context.getRoute("second"), "Route second should be added");
            assertNotNull(context.getRegistry().lookupByName("gated"), "Bean gated should be registered");
            assertNotNull(context.getRegistry().lookupByName("other"), "Bean other should be registered");
        }
    }

    private static String camel(String bean, Class<?> type, String route) {
        return """
                <camel>
                    <bean name="%s" type="%s"/>
                    <route id="%s">
                        <from uri="direct:%s"/>
                        <to uri="mock:%s"/>
                    </route>
                </camel>
                """.formatted(bean, type.getName(), route, route, route);
    }
}
