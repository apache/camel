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

package org.apache.camel.component.ribbon.cloud;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ribbon.RibbonConfiguration;
import org.apache.camel.impl.cloud.StaticServiceDiscovery;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class RibbonServiceCallUpdateRouteTest extends CamelTestSupport {
    private final StaticServiceDiscovery servers = new StaticServiceDiscovery();

    @Override
    public void setUp() throws Exception {
        // setup a static ribbon server list with these 2 servers to start with
        servers.addServer("localhost", 9090);
        servers.addServer("localhost", 9091);

        super.setUp();
    }

    @Test
    public void testServiceCall() throws Exception {
        getMockEndpoint("mock:9090").expectedMessageCount(1);
        getMockEndpoint("mock:9091").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(2);

        String out = template.requestBody("direct:start", null, String.class);
        String out2 = template.requestBody("direct:start", null, String.class);
        assertEquals("9091", out);
        assertEquals("9090", out2);

        assertMockEndpointsSatisfied();

        // stop the first server and remove it from the known list of servers
        context.stopRoute("9090");
        servers.removeServer("localhost", 9090);

        // call the other active server
        String out3 = template.requestBody("direct:start", null, String.class);
        assertEquals("9091", out3);

        // sleep a bit to make the server updated run and detect that a server is no longer in the list
        log.debug("Sleeping to all the server list updated to run");
        Thread.sleep(1000);
        log.debug("Calling the service now");

        // call again and it should call 9091 as its the only active server
        String out4 = template.requestBody("direct:start", null, String.class);
        assertEquals("9091", out4);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                RibbonConfiguration configuration = new RibbonConfiguration();
                // lets update quick so we do not have to sleep so much in the tests
                configuration.addProperty("ServerListRefreshInterval", "250");
                RibbonServiceLoadBalancer loadBalancer = new RibbonServiceLoadBalancer(configuration);

                from("direct:start")
                    .serviceCall()
                        .name("myService")
                        .component("jetty")
                        .loadBalancer(loadBalancer)
                        .serviceDiscovery(servers)
                        .end()
                    .to("mock:result");
                from("jetty:http://localhost:9090").routeId("9090")
                    .to("mock:9090")
                    .transform().constant("9090");
                from("jetty:http://localhost:9091").routeId("9091")
                    .to("mock:9091")
                    .transform().constant("9091");
            }
        };
    }
}

