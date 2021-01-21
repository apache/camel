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
package org.apache.camel.component.infinispan.remote;

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.infinispan.InfinispanRoutePolicy;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.infra.infinispan.services.InfinispanService;
import org.apache.camel.test.infra.infinispan.services.InfinispanServiceFactory;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.configuration.cache.CacheMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InfinispanRemoteRoutePolicyTest {
    public static final String CACHE_NAME = "_route_policy";

    @RegisterExtension
    public static InfinispanService service = InfinispanServiceFactory.createService();

    @Test
    public void testLeadership() throws Exception {
        try (RemoteCacheManager cacheContainer = createCacheContainer(service)) {

            cacheContainer.start();

            final InfinispanRoutePolicy policy1 = createRoutePolicy(cacheContainer, "route1");
            final InfinispanRoutePolicy policy2 = createRoutePolicy(cacheContainer, "route2");

            try (CamelContext context = new DefaultCamelContext()) {
                context.start();

                RouteBuilder.addRoutes(context, b -> b.from("direct:r1").routePolicy(policy1).to("mock:p1"));

                await().atMost(10, TimeUnit.SECONDS).until(policy1::isLeader);

                RouteBuilder.addRoutes(context, b -> b.from("direct:r2").routePolicy(policy2).to("mock:p2"));

                assertTrue(policy1.isLeader());
                assertFalse(policy2.isLeader());

                policy1.shutdown();

                await().atMost(10, TimeUnit.SECONDS).until(policy2::isLeader);

                assertFalse(policy1.isLeader());
                assertTrue(policy2.isLeader());
            }
        }
    }

    // *****************************
    //
    // *****************************

    private static RemoteCacheManager createCacheContainer(InfinispanService service) {
        ConfigurationBuilder clientBuilder = new ConfigurationBuilder();

        // for default tests, we force return value for all the
        // operations
        clientBuilder
                .forceReturnValues(true);

        // add server from the test infra service
        clientBuilder
                .addServer()
                .host(service.host())
                .port(service.port());

        // add security info
        clientBuilder
                .security()
                .authentication()
                .username(service.username())
                .password(service.password())
                .serverName("infinispan")
                .saslMechanism("DIGEST-MD5")
                .realm("default");

        RemoteCacheManager cacheContainer = new RemoteCacheManager(clientBuilder.build());
        cacheContainer.administration()
                .getOrCreateCache(
                        CACHE_NAME,
                        new org.infinispan.configuration.cache.ConfigurationBuilder()
                                .clustering()
                                .cacheMode(CacheMode.DIST_SYNC).build());

        return cacheContainer;
    }

    private static InfinispanRoutePolicy createRoutePolicy(RemoteCacheManager cacheContainer, String lockValue) {
        InfinispanRemoteConfiguration configuration = new InfinispanRemoteConfiguration();
        configuration.setCacheContainer(cacheContainer);

        InfinispanRemoteRoutePolicy policy = new InfinispanRemoteRoutePolicy(configuration);
        policy.setLockMapName(CACHE_NAME);
        policy.setLockKey("lock-key");
        policy.setLockValue(lockValue);

        return policy;
    }
}
