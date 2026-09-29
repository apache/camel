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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.loadbalancer.LoadBalancerSupport;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.RouteIdAware;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class LoadBalanceIdTest extends ContextTestSupport {

    @Test
    public void testLoadBalancerAndChildrenIds() {
        LoadBalancerSupport lb = context.getProcessor("myBalancer", LoadBalancerSupport.class);
        assertNotNull(lb, "the load balancer should be found by its id");
        assertEquals("myRoute", lb.getRouteId());

        for (String id : new String[] { "toA", "toB", "setC" }) {
            Processor child = context.getProcessor(id);
            assertNotNull(child, "the load balancer output " + id + " should be found by its id");
            assertEquals(id, assertInstanceOf(IdAware.class, child).getId());
            assertEquals("myRoute", assertInstanceOf(RouteIdAware.class, child).getRouteId());
        }
    }

    @Test
    public void testLoadBalancerRouting() throws Exception {
        getMockEndpoint("mock:a").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:b").expectedBodiesReceived("World");

        template.sendBody("direct:start", "Hello");
        template.sendBody("direct:start", "World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("myRoute")
                        .loadBalance().roundRobin().id("myBalancer")
                            .to("mock:a").id("toA")
                            .to("mock:b").id("toB")
                            .setHeader("c", constant("C")).id("setC")
                        .end();
            }
        };
    }
}
