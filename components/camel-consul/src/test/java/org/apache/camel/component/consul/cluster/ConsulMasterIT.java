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
package org.apache.camel.component.consul.cluster;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.infra.consul.services.ConsulService;
import org.apache.camel.test.infra.consul.services.ConsulServiceFactory;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsulMasterIT {
    @RegisterExtension
    public static ConsulService service = ConsulServiceFactory.createSingletonService();

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsulMasterIT.class);
    // own namespace: the Consul service is shared with the other integration tests of this module, a lock left by one
    // of them on the same key would keep these nodes from taking the leadership
    private static final String NAMESPACE = "consul-master-it";
    private static final List<String> CLIENTS = IntStream.range(0, 3).mapToObj(Integer::toString).toList();
    private static final List<String> RESULTS = new CopyOnWriteArrayList<>();
    private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(CLIENTS.size() * 2);
    private static final CountDownLatch LATCH = new CountDownLatch(CLIENTS.size());

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

        Assertions.assertEquals(CLIENTS.size(), RESULTS.size());
        Assertions.assertTrue(RESULTS.containsAll(CLIENTS));
    }

    // ************************************
    // Run a Camel node
    // ************************************

    private static void run(String id) {
        DefaultCamelContext context = new DefaultCamelContext();
        try {
            int events = ThreadLocalRandom.current().nextInt(2, 6);
            CountDownLatch contextLatch = new CountDownLatch(events);

            ConsulClusterService consulClusterService = new ConsulClusterService();
            consulClusterService.setId("node-" + id);
            consulClusterService.setUrl(service.getConsulUrl());

            LOGGER.info("Consul URL {}", consulClusterService.getUrl());

            context.disableJMX();
            context.getCamelContextExtension().setName("context-" + id);
            context.addService(consulClusterService);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("master:" + NAMESPACE + ":timer:consul?delay=1000&period=1000").routeId("route-" + id)
                            .log("From ${routeId}")
                            .process(e -> contextLatch.countDown());
                }
            });

            // Start the context after some random time so the startup order
            // changes for each test.
            // The start itself must not run inside Awaitility: its timeout interrupts the thread, which aborts a
            // start that is slow on a loaded machine and leaves the node without ever taking part in the election
            Awaitility.await().pollDelay(ThreadLocalRandom.current().nextInt(500), TimeUnit.MILLISECONDS)
                    .until(() -> true);
            context.start();

            contextLatch.await();

            LOGGER.debug("Shutting down node {}", id);
            RESULTS.add(id);

            context.stop();

            LATCH.countDown();
        } catch (Exception e) {
            LOGGER.warn("{}", e.getMessage(), e);
        } finally {
            // also when the test gave up waiting, so that this node does not keep running and holding the lock
            context.stop();
        }
    }
}
