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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.infra.hazelcast.services.HazelcastService;
import org.apache.camel.test.infra.hazelcast.services.HazelcastServiceFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration test for {@link HazelcastRoutePolicy} that verifies leader election and route management using Hazelcast
 * distributed locks.
 */
public class HazelcastRoutePolicyIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(HazelcastRoutePolicyIT.class);

    @RegisterExtension
    public static HazelcastService hazelcastService = HazelcastServiceFactory.createService();

    private static final List<String> CLIENTS = IntStream.range(0, 3).mapToObj(Integer::toString).toList();
    private static final List<String> RESULTS = new ArrayList<>();
    private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(CLIENTS.size() * 2);
    private static final CountDownLatch LATCH = new CountDownLatch(CLIENTS.size());

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

    private static void run(String id) {
        try {
            int events = ThreadLocalRandom.current().nextInt(2, 6);
            CountDownLatch contextLatch = new CountDownLatch(events);

            Config config = hazelcastService.createConfiguration(null, 0, "node-" + id, "set");
            HazelcastInstance instance = Hazelcast.newHazelcastInstance(config);

            HazelcastRoutePolicy policy = new HazelcastRoutePolicy(instance);
            policy.setLockMapName("camel-route-policy");
            policy.setLockKey("my-lock");
            policy.setLockValue("node-" + id);
            policy.setTryLockTimeout(5, TimeUnit.SECONDS);

            DefaultCamelContext context = new DefaultCamelContext();
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
            RESULTS.add(id);
            context.stop();
            instance.shutdown();
            LATCH.countDown();
        } catch (Exception e) {
            LOGGER.warn("{}", e.getMessage(), e);
        }
    }
}
