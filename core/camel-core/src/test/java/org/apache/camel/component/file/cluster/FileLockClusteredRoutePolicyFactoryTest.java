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

import java.io.IOException;
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
import org.apache.camel.impl.cluster.ClusteredRoutePolicyFactory;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class FileLockClusteredRoutePolicyFactoryTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileLockClusteredRoutePolicyFactoryTest.class);

    private List<String> CLIENTS;
    private List<String> RESULTS;
    private ScheduledExecutorService SCHEDULER;
    private CountDownLatch LATCH;

    @BeforeEach
    public void init() {
        CLIENTS = List.of("0", "1", "2");
        RESULTS = new ArrayList<>();
        SCHEDULER = Executors.newScheduledThreadPool(CLIENTS.size());
        LATCH = new CountDownLatch(CLIENTS.size());
    }

    @TempDir
    private Path tempDir;

    // ************************************
    // Test
    // ************************************

    // Repeating the test more than once is required to understand if the file locking
    // is managed properly and the locks are released upon context shutdown.
    @RepeatedTest(5)
    public void test() throws Exception {
        for (String id : CLIENTS) {
            SCHEDULER.submit(() -> run(id));
        }

        LATCH.await(20, TimeUnit.SECONDS);
        List<Runnable> waitingTasks = SCHEDULER.shutdownNow();
        assertEquals(0, waitingTasks.size(), "All scheduled tasks should have been completed!");

        assertEquals(CLIENTS.size(), RESULTS.size());
        assertTrue(RESULTS.containsAll(CLIENTS));
    }

    // ************************************
    // Run a Camel node
    // ************************************

    private void run(String id) {
        LOGGER.info("Starting task using file lock cluster service {}/node-{}", tempDir.toString(), id);
        DefaultCamelContext context = new DefaultCamelContext();
        try {
            int events = ThreadLocalRandom.current().nextInt(2, 6);
            CountDownLatch contextLatch = new CountDownLatch(events);

            FileLockClusterService service = new FileLockClusterService();
            service.setId("node-" + id);
            service.setRoot(tempDir.toString());
            service.setAcquireLockDelay(100, TimeUnit.MILLISECONDS);
            service.setAcquireLockInterval(100, TimeUnit.MILLISECONDS);

            context.disableJMX();
            context.getCamelContextExtension().setName("context-" + id);
            context.addService(service);
            context.addRoutePolicyFactory(ClusteredRoutePolicyFactory.forNamespace("my-ns"));
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("timer:file-lock?delay=10&period=100").routeId("route-" + id).log("From ${routeId}")
                            .process(e -> contextLatch.countDown());
                }
            });

            // Start the context after some random time so the startup order
            // changes for each test.
            Awaitility.await().pollDelay(ThreadLocalRandom.current().nextInt(500), TimeUnit.MILLISECONDS)
                    .untilAsserted(() -> Assertions.assertDoesNotThrow(context::start));

            context.start();

            contextLatch.await(10, TimeUnit.SECONDS);

            LOGGER.debug("Shutting down node {}", id);
            RESULTS.add(id);

            context.stop();

            LATCH.countDown();
        } catch (Exception e) {
            LOGGER.warn("{}", e.getMessage(), e);
        } finally {
            try {
                context.close();
            } catch (IOException e) {
                LOGGER.warn("{}", e.getMessage(), e);
            }
        }
    }
}
