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
package org.apache.camel.component.atomix.cluster;

import java.util.UUID;

import io.atomix.catalyst.transport.Address;
import io.atomix.copycat.server.storage.StorageLevel;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.ExplicitCamelContextNameStrategy;
import org.apache.camel.impl.cluster.ClusteredRoutePolicyFactory;
import org.apache.camel.main.Main;
import org.apache.camel.main.MainListenerSupport;

public final class AtomixClientClusteredRoutePolicyFactoryMain {
    private AtomixClientClusteredRoutePolicyFactoryMain() {
    }

    public static void main(String[] args) throws Exception {
        final String id = UUID.randomUUID().toString();

        Main main = new Main();
        main.addMainListener(new MainListenerSupport() {
            @Override
            public void configure(CamelContext context) {
                try {
                    AtomixClusterService service = new AtomixClusterService();
                    service.setId("node-" + id);
                    service.setStorageLevel(StorageLevel.MEMORY);
                    service.setAddress(new Address(args[0]));
                    service.setNodes(args.length > 1 ? args[1] : args[0]);

                    context.setNameStrategy(new ExplicitCamelContextNameStrategy("camel-" + id));
                    context.addService(service);
                    context.addRoutePolicyFactory(ClusteredRoutePolicyFactory.forNamespace("my-ns"));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });

        main.addRouteBuilder(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("timer:clustered?delay=1s&period=1s")
                    .routeId("route-" + id)
                    .log("Route ${routeId} is running ...");
            }
        });

        main.run();
    }
}
