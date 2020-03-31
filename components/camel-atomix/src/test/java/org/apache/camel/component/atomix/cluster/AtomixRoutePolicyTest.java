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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import io.atomix.catalyst.transport.Address;
import io.atomix.copycat.server.storage.StorageLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.cluster.ClusteredRoutePolicy;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class AtomixRoutePolicyTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(AtomixRoutePolicyTest.class);

    private final List<Address> addresses = Arrays.asList(
        new Address("127.0.0.1", AvailablePortFinder.getNextAvailable()),
        new Address("127.0.0.1", AvailablePortFinder.getNextAvailable()),
        new Address("127.0.0.1", AvailablePortFinder.getNextAvailable())
    );

    private final Set<Address> results = new HashSet<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(addresses.size());
    private final CountDownLatch latch = new CountDownLatch(addresses.size());

    // ************************************
    // Test
    // ************************************

    @Test
    void test() throws Exception {
        for (Address address: addresses) {
            scheduler.submit(() -> run(address));
        }

        latch.await(1, TimeUnit.MINUTES);
        scheduler.shutdownNow();

        assertEquals(addresses.size(), results.size());
        assertTrue(results.containsAll(addresses));
    }

    // ************************************
    // Run a Camel node
    // ************************************

    private void run(Address address) {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            int events = ThreadLocalRandom.current().nextInt(2, 6);
            CountDownLatch contextLatch = new CountDownLatch(events);

            AtomixClusterService service = new AtomixClusterService();
            service.setId("node-" + address.port());
            service.setStorageLevel(StorageLevel.MEMORY);
            service.setAddress(address);
            service.setNodes(addresses);

            context.disableJMX();
            context.setName("context-" + address.port());
            context.addService(service);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("timer:atomix?delay=1000&period=1000")
                        .routeId("route-" + address.port())
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

            LOGGER.debug("Shutting down node {}", address);
            results.add(address);

            context.stop();

            latch.countDown();
        } catch (Exception e) {
            LOGGER.warn("", e);
        }
    }
}
