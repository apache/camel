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
package org.apache.camel.component.atomix.ha;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.atomix.Atomix;
import io.atomix.AtomixReplica;
import io.atomix.catalyst.transport.Address;
import io.atomix.catalyst.transport.netty.NettyTransport;
import io.atomix.copycat.server.storage.Storage;
import io.atomix.copycat.server.storage.StorageLevel;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.ha.CamelCluster;
import org.apache.camel.ha.CamelClusterView;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.ha.ClusteredRoutePolicy;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AtomixRoutePolicyMain {
    private static final Logger LOGGER = LoggerFactory.getLogger(AtomixRoutePolicyMain.class);


    private static final List<Address> ADDRESSES =  Arrays.asList(
        new Address("127.0.0.1", AvailablePortFinder.getNextAvailable()),
        new Address("127.0.0.1", AvailablePortFinder.getNextAvailable()),
        new Address("127.0.0.1", AvailablePortFinder.getNextAvailable())
    );

    private static final CountDownLatch LATCH = new CountDownLatch(ADDRESSES.size());
    private static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(ADDRESSES.size() * 2);

    public static void main(final String[] args) throws Exception {
        for (Address address :  ADDRESSES) {
            EXECUTOR.submit(() -> run(address));
        }

        LATCH.await();

        System.exit(0);
    }

    static void run(Address address) {
        try {
            final String id = String.format("atomix-%d", address.port());
            final File path = new File("target", id);

            // Cleanup
            FileUtil.removeDir(path);

            Atomix atomix = AtomixReplica.builder(address)
                .withTransport(new NettyTransport())
                .withStorage(
                    Storage.builder()
                        .withDirectory(path)
                        .withStorageLevel(StorageLevel.MEMORY)
                        .build())
                .build()
                .bootstrap(ADDRESSES)
                .join();

            CountDownLatch latch = new CountDownLatch(1);
            CamelContext context = new DefaultCamelContext();
            CamelCluster cluster = new AtomixCluster(atomix);
            CamelClusterView view = cluster.createView("my-view");

            view.addEventListener((e, p) -> {
                if (view.getLocalMember().isMaster()) {
                    LOGGER.info("Member {} ({}), is now master", view.getLocalMember().getId(), address);

                    // Shutdown the context later on so the next one should take
                    // the leadership
                    EXECUTOR.schedule(latch::countDown, 10, TimeUnit.SECONDS);
                }
            });

            context.addService(cluster);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    RoutePolicy policy = ClusteredRoutePolicy.forView(view);

                    fromF("timer:%s-1?period=2s", id)
                        .routeId(id + "-1")
                        .routePolicy(policy)
                        .setHeader("ClusterMaster")
                            .body(b -> view.getMaster().getId())
                        .log("${routeId} (1) - master is: ${header.ClusterMaster}");
                    fromF("timer:%s-2?period=5s", id)
                        .routeId(id + "-2")
                        .routePolicy(policy)
                        .setHeader("ClusterMaster")
                            .body(b -> view.getMaster().getId())
                        .log("${routeId} (2) - master is: ${header.ClusterMaster}");
                }
            });

            context.start();
            latch.await();
            context.stop();

            LATCH.countDown();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        LOGGER.info("Done {}", address);
    }
}
