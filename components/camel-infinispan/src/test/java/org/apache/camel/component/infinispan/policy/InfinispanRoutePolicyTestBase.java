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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.infinispan.commons.api.BasicCacheContainer;
import org.junit.Assert;
import org.junit.Test;

abstract class InfinispanRoutePolicyTestBase extends CamelTestSupport {
    protected BasicCacheContainer cacheManager;
    protected InfinispanRoutePolicy policy1;
    protected InfinispanRoutePolicy policy2;

    @Override
    protected void doPreSetup() throws Exception {
        this.cacheManager = createCacheManager();

        this.policy1 = InfinispanRoutePolicy.withManager(cacheManager);
        this.policy1.setLockMapName("camel-route-policy");
        this.policy1.setLockKey("route-policy");
        this.policy1.setLockValue("route1");

        this.policy2 = InfinispanRoutePolicy.withManager(cacheManager);
        this.policy2.setLockMapName("camel-route-policy");
        this.policy2.setLockKey("route-policy");
        this.policy2.setLockValue("route2");
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        if (this.cacheManager != null) {
            this.cacheManager.stop();
        }
    }

    protected abstract BasicCacheContainer createCacheManager() throws Exception;

    // *******************************************
    //
    // *******************************************

    @Test
    public void testLeadership()throws Exception {
        context.startRoute("route1");
        while(!policy1.isLeader()) {
            Thread.sleep(250);
        }

        context.startRoute("route2");
        Thread.sleep(500);

        Assert.assertTrue(policy1.isLeader());
        Assert.assertFalse(policy2.isLeader());

        context.stopRoute("route1");
        while(!policy2.isLeader()) {
            Thread.sleep(250);
        }

        Assert.assertFalse(policy1.isLeader());
        Assert.assertTrue(policy2.isLeader());

        context.startRoute("route1");
        Thread.sleep(500);

        Assert.assertFalse(policy1.isLeader());
        Assert.assertTrue(policy2.isLeader());

        context.stopRoute("route2");
        while(!policy1.isLeader()) {
            Thread.sleep(250);
        }

        Assert.assertTrue(policy1.isLeader());
        Assert.assertFalse(policy2.isLeader());
    }

    // *******************************************
    //
    // *******************************************

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:route1")
                    .routeId("route1")
                    .autoStartup(false)
                    .routePolicy(policy1)
                    .to("log:org.apache.camel.component.infinispan.policy.1?level=INFO&showAll=true");
                from("direct:route2")
                    .routeId("route2")
                    .autoStartup(false)
                    .routePolicy(policy2)
                    .to("log:org.apache.camel.component.infinispan.policy.2?level=INFO&showAll=true");
            }
        };
    }
}
