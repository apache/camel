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
import java.util.ArrayList;
import java.util.List;

import io.atomix.catalyst.transport.Address;
import io.atomix.copycat.server.storage.StorageLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.ha.CamelClusterService;
import org.apache.camel.ha.CamelClusterView;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.ha.ClusteredRoutePolicy;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AtomixRoutePolicyMain {
    private static final Logger LOGGER = LoggerFactory.getLogger(AtomixRoutePolicyMain.class);

    public static void main(final String[] args) throws Exception {
        String[] addresses = System.getProperty("atomix.cluster").split(",");

        List<Address> cluster = new ArrayList<>();
        for (int i = 0; i < addresses.length; i++) {
            String[] parts = addresses[i].split(":");
            cluster.add(new Address(parts[0], Integer.valueOf(parts[1])));
        }

        final String id = String.format("atomix-%d", cluster.get(0).port());
        final File path = new File("target", id);

        // Cleanup
        FileUtil.removeDir(path);

        AtomixClusterService service = new AtomixClusterService();
        service.setStoragePath(path.getAbsolutePath());
        service.setStorageLevel(StorageLevel.DISK);
        service.setAddress(cluster.get(0));
        service.setNodes(cluster);

        DefaultCamelContext context = new DefaultCamelContext();
        context.addService(service);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                CamelClusterService cluster = getContext().hasService(AtomixClusterService.class);
                CamelClusterView view = cluster.createView("my-view");
                RoutePolicy policy = ClusteredRoutePolicy.forView(view);

                fromF("timer:%s-1?period=2s", id)
                    .routeId(id + "-1")
                    .routePolicy(policy)
                    .log("${routeId} (1)");
                fromF("timer:%s-2?period=5s", id)
                    .routeId(id + "-2")
                    .routePolicy(policy)
                    .log("${routeId} (2)");
            }
        });

        context.start();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    context.stop();
                } catch (Exception e) {
                    LOGGER.warn("", e);
                }
            }
        });

        for (int i = 0; i < Integer.MAX_VALUE; i++) {
            Thread.sleep(1000);
        }
    }
}
