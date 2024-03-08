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
package org.apache.camel.component.file.cluster;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.cluster.ClusteredRoutePolicy;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class FileLockClusteredRoutePolicyTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileLockClusteredRoutePolicyTest.class);
    private static final List<String> CLIENTS = List.of("0", "1", "2");
    private static final List<String> RESULTS = new ArrayList<>();
    private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(CLIENTS.size());
    private static final CountDownLatch LATCH = new CountDownLatch(CLIENTS.size());

    @TempDir
    private static Path tempDir;

    // ************************************
    // Test
    // ************************************

    @Test
    public void test() throws Exception {
        for (String id : CLIENTS) {
            SCHEDULER.submit(() -> run(id));
        }

        LATCH.await(1, TimeUnit.MINUTES);
        SCHEDULER.shutdownNow();

        assertEquals(CLIENTS.size(), RESULTS.size());
        assertTrue(RESULTS.containsAll(CLIENTS));
    }

    // ************************************
    // Run a Camel node
    // ************************************

    private static void run(String id) {
        try {
            int events = ThreadLocalRandom.current().nextInt(2, 6);
            CountDownLatch contextLatch = new CountDownLatch(events);

            FileLockClusterService service = new FileLockClusterService();
            service.setId("node-" + id);
            service.setRoot(tempDir.toString());
            service.setAcquireLockDelay(1, TimeUnit.SECONDS);
            service.setAcquireLockInterval(1, TimeUnit.SECONDS);

            DefaultCamelContext context = new DefaultCamelContext();
            context.disableJMX();
            context.getCamelContextExtension().setName("context-" + id);
            context.addService(service);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("timer:file-lock?delay=1000&period=1000").routeId("route-" + id)
                            .routePolicy(ClusteredRoutePolicy.forNamespace("my-ns")).log("From ${routeId}")
                            .process(e -> contextLatch.countDown());
                }
            });

            // Start the context after some random time so the startup order
            // changes for each test.
            Awaitility.await().pollDelay(ThreadLocalRandom.current().nextInt(500), TimeUnit.MILLISECONDS)
                    .untilAsserted(() -> Assertions.assertDoesNotThrow(context::start));

            context.start();

            contextLatch.await();

            LOGGER.debug("Shutting down node {}", id);
            RESULTS.add(id);

            context.stop();

            LATCH.countDown();
        } catch (Exception e) {
            LOGGER.warn("{}", e.getMessage(), e);
        }
    }
}
