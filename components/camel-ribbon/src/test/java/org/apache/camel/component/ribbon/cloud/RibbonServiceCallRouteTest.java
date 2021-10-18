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
import org.apache.camel.component.ribbon.RibbonConfiguration;
import org.apache.camel.impl.cloud.StaticServiceDiscovery;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RibbonServiceCallRouteTest extends CamelTestSupport {
    protected static final int PORT1 = AvailablePortFinder.getNextAvailable();
    protected static final int PORT2 = AvailablePortFinder.getNextAvailable();

    protected static final String ROUTE_1_ID = "srv1";
    protected static final String ROUTE_2_ID = "srv2";

    @Test
    public void testServiceCall() throws Exception {
        getMockEndpoint("mock:" + ROUTE_1_ID).expectedMessageCount(1);
        getMockEndpoint("mock:" + ROUTE_2_ID).expectedMessageCount(1);
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
                // setup a static ribbon server list with these 2 servers to start with
                StaticServiceDiscovery servers = new StaticServiceDiscovery();
                servers.addServer("myService@localhost:" + PORT1);
                servers.addServer("myService@localhost:" + PORT2);

                RibbonConfiguration configuration = new RibbonConfiguration();
                RibbonServiceLoadBalancer loadBalancer = new RibbonServiceLoadBalancer(configuration);

                from("direct:start")
                        .serviceCall()
                        .name("myService")
                        .component("http")
                        .loadBalancer(loadBalancer)
                        .serviceDiscovery(servers)
                        .end()
                        .to("mock:result");
                fromF("jetty:http://localhost:%d", PORT1)
                        .to("mock:" + ROUTE_1_ID)
                        .transform().constant("9090");
                fromF("jetty:http://localhost:%d", PORT2)
                        .to("mock:" + ROUTE_2_ID)
                        .transform().constant("9091");
            }
        };
    }
}
