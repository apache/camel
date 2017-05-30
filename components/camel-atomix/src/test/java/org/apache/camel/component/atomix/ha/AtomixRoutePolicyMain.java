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

    private static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(ADDRESSES.size() * 3);

    public static void main(final String[] args) throws Exception {
        for (Address address :  ADDRESSES) {
            EXECUTOR.submit(() -> setupContext(address));
        }

        EXECUTOR.awaitTermination(5, TimeUnit.MINUTES);
    }

    static void setupContext(Address address) {
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
                        .withStorageLevel(StorageLevel.DISK)
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
                    LOGGER.info("{}, is now master", address);
                    try {
                        EXECUTOR.schedule(latch::countDown, 10, TimeUnit.SECONDS);
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                }
            });

            context.disableJMX();
            context.addService(cluster, true, true);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    fromF("timer:%s?period=1s", id)
                        .routeId(id)
                        .routePolicy(new ClusteredRoutePolicy(view))
                        .setHeader("ClusterMaster")
                            .body(b -> view.getMaster().getId())
                        .log("${routeId} - master is: ${header.ClusterMaster}");
                }
            });

            context.start();
            latch.await();
            context.stop();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
