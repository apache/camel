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
package org.apache.camel.component.atomix.cluster;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.atomix.AtomixReplica;
import io.atomix.catalyst.transport.Address;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cluster.CamelClusterService;
import org.apache.camel.component.atomix.client.AtomixFactory;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.cluster.ClusteredRoutePolicy;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class AtomixClientRoutePolicyTestSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(AtomixClientRoutePolicyTestSupport.class);

    private final Address address = new Address("127.0.0.1", AvailablePortFinder.getNextAvailable());
    private final List<String> clients = IntStream.range(0, 3).mapToObj(Integer::toString).collect(Collectors.toList());
    private final List<String> results = new ArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(clients.size() * 2);
    private final CountDownLatch latch = new CountDownLatch(clients.size());

    // ************************************
    // Test
    // ************************************

    @Test
    void test() throws Exception {
        AtomixReplica boot = null;

        try {
            boot = AtomixFactory.replica(address);

            for (String id : clients) {
                scheduler.submit(() -> run(id));
            }

            latch.await(1, TimeUnit.MINUTES);
            scheduler.shutdownNow();

            assertEquals(clients.size(), results.size());
            assertTrue(results.containsAll(clients));
        } finally {
            if (boot != null) {
                boot.shutdown();
            }
        }
    }

    // ************************************
    // Run a Camel node
    // ************************************

    private void run(String id) {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            int events = ThreadLocalRandom.current().nextInt(2, 6);
            CountDownLatch contextLatch = new CountDownLatch(events);

            context.disableJMX();
            context.setName("context-" + id);
            context.addService(createClusterService(id, address));
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("timer:atomix?delay=1000&period=1000")
                        .routeId("route-" + id)
                        .routePolicy(ClusteredRoutePolicy.forNamespace("my-ns"))
                        .log("From ${routeId}")
                        .process(e -> contextLatch.countDown());
                }
            });

            // Start the context after some random time so the startup order
            // changes for each test.
            Thread.sleep(ThreadLocalRandom.current().nextInt(500));
            context.start();

            contextLatch.await();

            LOGGER.debug("Shutting down client node {}", id);
            results.add(id);

            context.stop();

            latch.countDown();
        } catch (Exception e) {
            LOGGER.warn("", e);
        }
    }

    protected abstract CamelClusterService createClusterService(String id, Address bootstrapNode);
}
