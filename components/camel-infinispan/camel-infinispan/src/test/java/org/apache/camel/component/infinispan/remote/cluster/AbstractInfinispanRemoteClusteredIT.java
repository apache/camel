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
package org.apache.camel.component.infinispan.remote.cluster;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.IntStream;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.cluster.ClusteredRoutePolicy;
import org.apache.camel.impl.cluster.ClusteredRoutePolicyFactory;
import org.apache.camel.test.infra.infinispan.services.InfinispanService;
import org.apache.camel.test.infra.infinispan.services.InfinispanServiceFactory;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.infinispan.remote.cluster.InfinispanRemoteClusteredTestSupport.createCache;
import static org.apache.camel.component.infinispan.remote.cluster.InfinispanRemoteClusteredTestSupport.createConfiguration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AbstractInfinispanRemoteClusteredIT {
    @RegisterExtension
    public static InfinispanService service = InfinispanServiceFactory.createService();

    private RemoteCacheManager cacheContainer;

    private final String viewName = "myView";

    class RunnerEnv {
        int events;
        CountDownLatch latch;
        String id;
    }

    @BeforeEach
    public void setUpManager() {
        Configuration configuration = createConfiguration(service);
        cacheContainer = new RemoteCacheManager(configuration);
        createCache(cacheContainer, viewName);
    }

    public void runTest(Function<RunnerEnv, RouteBuilder> routeBuilderFunction) throws Exception {
        final List<String> clients = IntStream.range(0, 3).mapToObj(Integer::toString).toList();
        final List<String> results = new ArrayList<>();

        final CountDownLatch latch = new CountDownLatch(clients.size());
        final Logger logger = LoggerFactory.getLogger(getClass());

        final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(clients.size() * 2);

        for (String id : clients) {

            scheduler.submit(() -> {
                try {
                    run(id, routeBuilderFunction);

                    logger.debug("Node {} is shutting down", id);
                    results.add(id);
                } catch (Exception e) {
                    logger.warn("Failed to run job: {}", e.getMessage(), e);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        scheduler.shutdownNow();

        assertThat(results).hasSameSizeAs(clients);
        assertThat(results).containsAll(clients);
    }

    //Set up a single node cluster.
    private InfinispanRemoteClusterService getInfinispanRemoteClusterService(String id) {
        InfinispanRemoteClusterService clusterService = new InfinispanRemoteClusterService();

        clusterService.setCacheContainer(cacheContainer);
        clusterService.setId("node-" + id);

        return clusterService;
    }

    public void run(String id, Function<RunnerEnv, RouteBuilder> routeBuilderFunction) {
        int events = ThreadLocalRandom.current().nextInt(2, 6);
        CountDownLatch contextLatch = new CountDownLatch(events);

        InfinispanRemoteClusterService clusterService = getInfinispanRemoteClusterService(id);

        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.disableJMX();
            context.getCamelContextExtension().setName("context-" + id);
            context.addService(clusterService);

            RunnerEnv contextEnv = new RunnerEnv();

            contextEnv.id = id;
            contextEnv.latch = contextLatch;
            contextEnv.events = events;

            RouteBuilder routeBuilder = routeBuilderFunction.apply(contextEnv);

            context.addRoutes(routeBuilder);

            // Start the context after some random time so the startup order
            // changes for each test.
            Thread.sleep(ThreadLocalRandom.current().nextInt(500));
            context.start();

            contextLatch.await();
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Nested
    class InfinispanRemoteClusteredMasterTestNested {
        public RouteBuilder getRouteBuilder(RunnerEnv runnerEnv) {
            return new RouteBuilder() {
                @Override
                public void configure() {
                    fromF("timer:%s?delay=1000&period=1000&repeatCount=%d", runnerEnv.id, runnerEnv.events)
                            .routeId("route-" + runnerEnv.id)
                            .log("From id=${routeId} counter=${header.CamelTimerCounter}")
                            .process(e -> runnerEnv.latch.countDown());
                }
            };
        }

        @Timeout(value = 1, unit = TimeUnit.MINUTES)
        @Test
        public void test() throws Exception {
            runTest(this::getRouteBuilder);
        }
    }

    @Nested
    class InfinispanRemoteClusteredRoutePolicyFactoryTestNested {
        public RouteBuilder getRouteBuilder(RunnerEnv runnerEnv) {
            return new RouteBuilder() {
                @Override
                public void configure() {
                    this.getContext().addRoutePolicyFactory(ClusteredRoutePolicyFactory.forNamespace(viewName));

                    fromF("timer:%s?delay=1000&period=1000&repeatCount=%d", runnerEnv.id, runnerEnv.events)
                            .routeId("route-" + runnerEnv.id)
                            .log("From id=${routeId} counter=${header.CamelTimerCounter}")
                            .process(e -> runnerEnv.latch.countDown());
                }
            };
        }

        @Timeout(value = 1, unit = TimeUnit.MINUTES)
        @Test
        public void test() throws Exception {
            runTest(this::getRouteBuilder);
        }
    }

    @Nested
    class InfinispanRemoteClusteredRoutePolicyTestNested {
        public RouteBuilder getRouteBuilder(RunnerEnv runnerEnv) {
            return new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    this.getContext().addRoutePolicyFactory(ClusteredRoutePolicyFactory.forNamespace(viewName));

                    fromF("timer:%s?delay=1000&period=1000&repeatCount=%d", runnerEnv.id, runnerEnv.events)
                            .routeId("route-" + runnerEnv.id)
                            .routePolicy(ClusteredRoutePolicy.forNamespace(viewName))
                            .log("From id=${routeId} counter=${header.CamelTimerCounter}")
                            .process(e -> runnerEnv.latch.countDown());
                }
            };
        }

        @Timeout(value = 1, unit = TimeUnit.MINUTES)
        @Test
        public void test() throws Exception {
            runTest(this::getRouteBuilder);
        }
    }
}
