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

import java.util.ArrayList;
import java.util.List;

import io.atomix.catalyst.transport.Address;
import io.atomix.copycat.server.storage.StorageLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.ha.ClusteredRoutePolicyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AtomixRoutePolicyMain {
    private static final Logger LOGGER = LoggerFactory.getLogger(AtomixRoutePolicyMain.class);

    public static void main(final String[] args) throws Exception {
        final Integer index = Integer.getInteger("atomix.index");
        final String[] addresses = System.getProperty("atomix.cluster").split(",");

        List<Address> nodes = new ArrayList<>();
        for (int i = 0; i < addresses.length; i++) {
            String[] parts = addresses[i].split(":");
            nodes.add(new Address(parts[0], Integer.valueOf(parts[1])));
        }

        AtomixCluster cluster = new AtomixCluster();
        cluster.setStorageLevel(StorageLevel.MEMORY);
        cluster.setAddress(nodes.get(index));
        cluster.setNodes(nodes);

        DefaultCamelContext context = new DefaultCamelContext();
        context.addService(cluster);
        context.addRoutePolicyFactory(ClusteredRoutePolicyFactory.forNamespace("my-ns"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                fromF("timer:atomix-%d-1?period=2s", nodes.get(index).port())
                    .log("${routeId} (1)");
                fromF("timer:atomix-%d-2?period=5s", nodes.get(index).port())
                    .log("${routeId} (2)");
            }
        });

        context.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                context.stop();
            } catch (Exception e) {
                LOGGER.warn("", e);
            }
        }));

        for (int i = 0; i < Integer.MAX_VALUE; i++) {
            Thread.sleep(1000);
        }
    }
}
