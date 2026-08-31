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
package org.apache.camel.component.zookeeper.cluster.integration;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cluster.CamelClusterEventListener;
import org.apache.camel.cluster.CamelClusterView;
import org.apache.camel.component.zookeeper.cluster.ZooKeeperClusterService;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.cluster.ClusteredRoutePolicy;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.zookeeper.services.ZooKeeperService;
import org.apache.camel.test.infra.zookeeper.services.ZooKeeperServiceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ZooKeeperClusterViewLeadershipLostIT {

    private static final String NAMESPACE = "my-ns";
    private static final String ROUTE_ID = "clustered-route";
    private static final int EXPECTED_PUSHED_EVENTS_NUM = 3;

    @RegisterExtension
    static ZooKeeperService service = ZooKeeperServiceFactory.createService();

    @Test
    void leadershipIsReleasedAndReacquiredAroundAZooKeeperOutage() throws Exception {
        AtomicInteger numberOfLeadershipChangedPushed = new AtomicInteger();
        GenericContainer<?> zooKeeper = zooKeeperContainer();

        try (DefaultCamelContext context = new DefaultCamelContext()) {
            ZooKeeperClusterService clusterService = new ZooKeeperClusterService();
            clusterService.setId("node-1");
            clusterService.setNodes(service.serverUrls());
            clusterService.setBasePath("/camel");

            clusterService.setSessionTimeout(5000);

            context.disableJMX();
            context.addService(clusterService);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("timer:zookeeper?period=1000")
                            .routeId(ROUTE_ID)
                            .routePolicy(ClusteredRoutePolicy.forNamespace(NAMESPACE))
                            .to("log:zookeeper?level=DEBUG");
                }
            });

            CamelClusterView clusterView = clusterService.getView(NAMESPACE);
            clusterView.addEventListener((CamelClusterEventListener.Leadership) (
                    view, leader) -> numberOfLeadershipChangedPushed.incrementAndGet());

            context.start();

            await().atMost(1, TimeUnit.MINUTES)
                    .untilAsserted(() -> {
                        assertEquals(true,
                                clusterView.getLocalMember().isLeader(),
                                "the only node of the cluster must be the leader");
                        assertEquals(true,
                                context.getRouteController().getRouteStatus(ROUTE_ID).isStarted(),
                                "the leader must have started the clustered route");
                    });

            zooKeeper.getDockerClient().pauseContainerCmd(zooKeeper.getContainerId()).exec();

            try {
                await().atMost(1, TimeUnit.MINUTES)
                        .untilAsserted(() -> {
                            assertEquals(false,
                                    clusterView.getLocalMember().isLeader(),
                                    "the leadership must be given up once ZooKeeper is no longer reachable");
                            assertEquals(false,
                                    context.getRouteController().getRouteStatus(ROUTE_ID).isStarted(),
                                    "the clustered route must be stopped once the leadership is lost");
                        });
            } finally {
                zooKeeper.getDockerClient().unpauseContainerCmd(zooKeeper.getContainerId()).exec();
            }

            await().atMost(1, TimeUnit.MINUTES)
                    .untilAsserted(() -> {
                        assertEquals(true,
                                clusterView.getLocalMember().isLeader(),
                                "the node must re-enter the election once ZooKeeper is reachable again");
                        assertEquals(true,
                                context.getRouteController().getRouteStatus(ROUTE_ID).isStarted(),
                                "the clustered route must be restarted once the leadership is taken back");
                    });
            clusterView.stop();

            /*
            Give some time so the event can be consumed
            (the correct behavior is that an event shouldn't be pushed)
            this is just a safeguard so that if an event is pushed it has some time to be consumed
            */
            await()
                    .pollDelay(1, TimeUnit.SECONDS)
                    .atLeast(1, TimeUnit.SECONDS)
                    .atMost(2, TimeUnit.SECONDS)
                    .until(() -> true);

            assertEquals(EXPECTED_PUSHED_EVENTS_NUM,
                    numberOfLeadershipChangedPushed.get(),
                    "the pushed Leadership Changed event must be %d otherwise a push happened on stop view"
                            .formatted(EXPECTED_PUSHED_EVENTS_NUM));
        }
    }

    private static GenericContainer<?> zooKeeperContainer() {
        assumeTrue(service instanceof ContainerService<?>,
                "This test requires the local ZooKeeper container infra service");

        return ((ContainerService<?>) service).getContainer();
    }
}
