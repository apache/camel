/**
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
package org.apache.camel.component.master;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.cluster.FileLockClusterService;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class MasterComponentTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(MasterComponentTest.class);
    private static final List<String> INSTANCES = IntStream.range(0, 3).mapToObj(Integer::toString).collect(Collectors.toList());
    private static final List<String> RESULTS = new ArrayList<>();
    private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(INSTANCES.size());
    private static final CountDownLatch LATCH = new CountDownLatch(INSTANCES.size());

    @Test
    public void test()  throws Exception {
        for (String instance: INSTANCES) {
            SCHEDULER.submit(() -> run(instance));
        }

        LATCH.await(1, TimeUnit.MINUTES);
        SCHEDULER.shutdownNow();

        Assert.assertEquals(INSTANCES.size(), RESULTS.size());
        Assert.assertTrue(RESULTS.containsAll(INSTANCES));
    }

    // ************************************
    // Run a Camel node
    // ************************************

    private static void run(String id) {
        try {
            int events = ThreadLocalRandom.current().nextInt(2, 6);
            CountDownLatch contextLatch = new CountDownLatch(events);

            FileLockClusterService service = new FileLockClusterService();
            service.setId(id);
            service.setRoot("target/cluster");
            service.setAcquireLockDelay(1, TimeUnit.SECONDS);
            service.setAcquireLockInterval(1, TimeUnit.SECONDS);

            DefaultCamelContext context = new DefaultCamelContext();
            context.disableJMX();
            context.setName("context-" + id);
            context.addService(service);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("master:ns:timer:test?delay=1s&period=1s")
                        .routeId("route-" + id)
                        .log("From ${routeId}")
                        .process(e -> contextLatch.countDown());
                }
            });

            // Start the context after some random time so the startup order
            // changes for each test.
            Thread.sleep(ThreadLocalRandom.current().nextInt(500));
            context.start();

            contextLatch.await();

            LOGGER.debug("Shutting down node {}", id);
            RESULTS.add(id);

            context.stop();

            LATCH.countDown();
        } catch (Exception e) {
            LOGGER.warn("", e);
        }
    }
}
