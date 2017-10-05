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

import java.util.Collections;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ribbon.RibbonConfiguration;
import org.apache.camel.impl.cloud.StaticServiceDiscovery;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class RibbonServiceCallRouteMetadataTest extends CamelTestSupport {
    @Test
    public void testServiceCall() throws Exception {
        getMockEndpoint("mock:app1").expectedMessageCount(1);
        getMockEndpoint("mock:app2").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(2);

        String out = template.requestBody("direct:start", null, String.class);
        String out2 = template.requestBody("direct:start", null, String.class);
        assertEquals("app2", out);
        assertEquals("app1", out2);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // setup a static ribbon server list with these 2 servers to start with
                StaticServiceDiscovery servers = new StaticServiceDiscovery();
                servers.addServer("myService", "localhost", 9090, Collections.singletonMap("contextPath", "app1"));
                servers.addServer("myService", "localhost", 9090, Collections.singletonMap("contextPath", "app2"));

                RibbonConfiguration configuration = new RibbonConfiguration();
                RibbonServiceLoadBalancer loadBalancer = new RibbonServiceLoadBalancer(configuration);

                from("direct:start")
                    .serviceCall()
                        .name("myService")
                        .expression().simple("jetty:http://${header.CamelServiceCallServiceHost}:${header.CamelServiceCallServicePort}/${header.CamelServiceCallServiceMeta[contextPath]}")
                        .loadBalancer(loadBalancer)
                        .serviceDiscovery(servers)
                        .end()
                    .to("mock:result");
                from("jetty:http://localhost:9090/app1")
                    .to("mock:app1")
                    .transform().constant("app1");
                from("jetty:http://localhost:9090/app2")
                    .to("mock:app2")
                    .transform().constant("app2");
            }
        };
    }
}

