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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class DoubleLoadBalancerMisconfigurationTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testMisconfiguration() throws Exception {
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:start")
                        .loadBalance().failover().roundRobin()
                        .to("mock:a", "mock:b");
                }
            });
            fail("Should have thrown an exception");
        } catch (IllegalArgumentException e) {
            assertEquals("Loadbalancer already configured to: FailoverLoadBalancer. Cannot set it to: RoundRobinLoadBalancer", e.getMessage());
        }
    }

    public void testMisconfiguration2() throws Exception {
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:start")
                        .loadBalance().failover().random()
                        .to("mock:a", "mock:b");
                }
            });
            fail("Should have thrown an exception");
        } catch (IllegalArgumentException e) {
            assertEquals("Loadbalancer already configured to: FailoverLoadBalancer. Cannot set it to: RandomLoadBalancer", e.getMessage());
        }
    }

    public void testMisconfiguration3() throws Exception {
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:start")
                        .loadBalance().random().failover()
                        .to("mock:a", "mock:b");
                }
            });
            fail("Should have thrown an exception");
        } catch (IllegalArgumentException e) {
            assertEquals("Loadbalancer already configured to: RandomLoadBalancer. Cannot set it to: FailoverLoadBalancer", e.getMessage());
        }
    }
}
