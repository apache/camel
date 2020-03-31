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
package org.apache.camel.component.zookeeper.cluster;

import java.util.UUID;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.cluster.ClusteredRoutePolicyFactory;
import org.apache.camel.impl.engine.ExplicitCamelContextNameStrategy;
import org.apache.camel.main.Main;
import org.apache.camel.main.MainListenerSupport;

public final class ZooKeeperClusteredRoutePolicyFactoryMain {
    private ZooKeeperClusteredRoutePolicyFactoryMain() {
    }

    public static void main(String[] args) throws Exception {
        final String id = UUID.randomUUID().toString();

        Main main = new Main();
        main.addMainListener(new MainListenerSupport() {
            @Override
            public void configure(CamelContext context) {
                try {
                    ZooKeeperClusterService service = new ZooKeeperClusterService();
                    service.setId("node-" + id);
                    service.setNodes(args[0]);
                    service.setBasePath("/camel");

                    context.setNameStrategy(new ExplicitCamelContextNameStrategy("camel-" + id));
                    context.addService(service);
                    context.addRoutePolicyFactory(ClusteredRoutePolicyFactory.forNamespace("my-ns"));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });

        main.addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("timer:clustered?delay=1000&period=1000")
                    .routeId("route-" + id)
                    .log("Route ${routeId} is running ...");
            }
        });

        main.run();
    }
}
