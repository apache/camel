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
package org.apache.camel.component.hazelcast.policy;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.infra.hazelcast.services.HazelcastService;
import org.apache.camel.test.infra.hazelcast.services.HazelcastServiceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for {@link HazelcastRoutePolicy} that verifies leader election and route management using Hazelcast
 * distributed locks.
 */
public class HazelcastRoutePolicyIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(HazelcastRoutePolicyIT.class);
    private static final List<String> CLIENTS = List.of("0", "1", "2");

    @RegisterExtension
    public static HazelcastService hazelcastService = HazelcastServiceFactory.createService();

    @Test
    public void test() throws Exception {
        List<String> results = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(CLIENTS.size());
        ExecutorService executor = Executors.newFixedThreadPool(CLIENTS.size());

        for (String id : CLIENTS) {
            executor.submit(() -> run(id, results, latch));
        }

        assertTrue(latch.await(1, TimeUnit.MINUTES), "All nodes should complete within timeout");
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "Executor should terminate cleanly");

        assertThat(results).containsExactlyInAnyOrderElementsOf(CLIENTS);
    }

    private static void run(String id, List<String> results, CountDownLatch latch) {
        DefaultCamelContext context = null;
        HazelcastInstance instance = null;
        try {
            int events = ThreadLocalRandom.current().nextInt(2, 6);
            CountDownLatch contextLatch = new CountDownLatch(events);

            Config config = hazelcastService.createConfiguration(null, 0, "node-" + id, "set");
            instance = Hazelcast.newHazelcastInstance(config);

            HazelcastRoutePolicy policy = new HazelcastRoutePolicy(instance);
            policy.setLockMapName("camel-route-policy");
            policy.setLockKey("my-lock");
            policy.setLockValue("node-" + id);
            policy.setTryLockTimeout(5, TimeUnit.SECONDS);

            context = new DefaultCamelContext();
            context.disableJMX();
            context.getCamelContextExtension().setName("context-" + id);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("timer:hazelcast?delay=1000&period=1000")
                            .routeId("route-" + id)
                            .routePolicy(policy)
                            .log("From ${routeId}")
                            .process(e -> contextLatch.countDown());
                }
            });

            // Staggered startup
            Thread.sleep(ThreadLocalRandom.current().nextInt(500));

            LOGGER.info("Starting CamelContext on node: {}", id);
            context.start();
            LOGGER.info("Started CamelContext on node: {}", id);

            contextLatch.await(30, TimeUnit.SECONDS);

            LOGGER.info("Shutting down node {}", id);
            results.add(id);
        } catch (Exception e) {
            LOGGER.warn("Node {} failed: {}", id, e.getMessage(), e);
        } finally {
            if (context != null) {
                context.stop();
            }
            if (instance != null) {
                instance.shutdown();
            }
            latch.countDown();
        }
    }
}
