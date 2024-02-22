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
package org.apache.camel.component.infinispan.embedded.cluster;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.cluster.ClusteredRoutePolicyFactory;
import org.infinispan.manager.DefaultCacheManager;

public class InfinispanEmbeddedClusteredRoutePolicyFactoryTest extends AbstractInfinispanEmbeddedClusteredTest {
    @Override
    protected void run(DefaultCacheManager cacheContainer, String namespace, String id) throws Exception {
        int events = ThreadLocalRandom.current().nextInt(2, 6);
        CountDownLatch contextLatch = new CountDownLatch(events);

        //Set up a single node cluster.
        InfinispanEmbeddedClusterService clusterService = new InfinispanEmbeddedClusterService();
        clusterService.setCacheContainer(cacheContainer);
        clusterService.setId("node-" + id);

        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.disableJMX();
            context.getCamelContextExtension().setName("context-" + id);
            context.addService(clusterService);
            context.addRoutePolicyFactory(ClusteredRoutePolicyFactory.forNamespace(namespace));
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    fromF("timer:%s?delay=1000&period=1000", id)
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
        }
    }
}
