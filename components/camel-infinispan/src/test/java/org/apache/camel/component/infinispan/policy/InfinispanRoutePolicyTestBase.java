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
package org.apache.camel.component.infinispan.policy;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.util.ServiceHelper;
import org.infinispan.commons.api.BasicCacheContainer;
import org.junit.Assert;
import org.junit.Test;

abstract class InfinispanRoutePolicyTestBase {
    private static final String CACHE_NAME = "camel-route-policy";
    private static final String CACHE_KEY = "route-policy";

    protected abstract BasicCacheContainer createCacheManager() throws Exception;

    // *******************************************
    //
    // *******************************************

    @Test
    public void testLeadership()throws Exception {
        BasicCacheContainer cacheManager = createCacheManager();

        InfinispanRoutePolicy policy1 = InfinispanRoutePolicy.withManager(cacheManager);
        policy1.setLockMapName(CACHE_NAME);
        policy1.setLockKey(CACHE_KEY);
        policy1.setLockValue("route1");

        InfinispanRoutePolicy policy2 = InfinispanRoutePolicy.withManager(cacheManager);
        policy2.setLockMapName(CACHE_NAME);
        policy2.setLockKey(CACHE_KEY);
        policy2.setLockValue("route2");

        CamelContext context = new DefaultCamelContext();

        try {
            context = new DefaultCamelContext();
            context.start();

            context.addRouteDefinition(RouteDefinition.fromUri("direct:r1").routePolicy(policy1).to("mock:p1"));

            for (int i = 0; i < 10 && !policy1.isLeader(); i++) {
                Thread.sleep(250);
            }

            context.addRouteDefinition(RouteDefinition.fromUri("direct:r2").routePolicy(policy2).to("mock:p2"));

            Assert.assertTrue(policy1.isLeader());
            Assert.assertFalse(policy2.isLeader());

            policy1.shutdown();

            for (int i = 0; i < 10 && !policy2.isLeader(); i++) {
                Thread.sleep(250);
            }

            Assert.assertFalse(policy1.isLeader());
            Assert.assertTrue(policy2.isLeader());

        } finally {
            ServiceHelper.stopService(context);

            if (cacheManager != null) {
                cacheManager.stop();
            }
        }
    }
}
