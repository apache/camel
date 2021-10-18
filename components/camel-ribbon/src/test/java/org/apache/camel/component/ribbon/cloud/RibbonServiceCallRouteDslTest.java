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
package org.apache.camel.component.ribbon.cloud;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RibbonServiceCallRouteDslTest extends CamelTestSupport {
    private static final int PORT1 = AvailablePortFinder.getNextAvailable();
    private static final int PORT2 = AvailablePortFinder.getNextAvailable();

    @Test
    public void testServiceCall() throws Exception {
        getMockEndpoint("mock:" + PORT1).expectedMessageCount(1);
        getMockEndpoint("mock:" + PORT2).expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(2);

        String out = template.requestBody("direct:start", null, String.class);
        String out2 = template.requestBody("direct:start", null, String.class);
        assertEquals("9091", out);
        assertEquals("9090", out2);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .serviceCall()
                        .name("myService")
                        .component("http")
                        .ribbonLoadBalancer()
                        .staticServiceDiscovery()
                        .servers("localhost:" + PORT1)
                        .servers("localhost:" + PORT2)
                        .endParent()
                        .to("mock:result");
                fromF("jetty:http://localhost:%d", PORT1)
                        .to("mock:" + PORT1)
                        .transform().constant("9090");
                fromF("jetty:http://localhost:%d", PORT2)
                        .to("mock:" + PORT2)
                        .transform().constant("9091");
            }
        };
    }
}
