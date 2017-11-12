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
package org.apache.camel.component.netty.http;

import java.util.Set;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class ManagedNettyEndpointTest extends BaseNettyTest {

    @Override
    protected boolean useJmx() {
        return true;
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        return context;
    }

    protected MBeanServer getMBeanServer() {
        return context.getManagementStrategy().getManagementAgent().getMBeanServer();
    }

    @Test
    public void testManagement() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        // should not add 10 endpoints
        getMockEndpoint("mock:foo").expectedMessageCount(10);
        for (int i = 0; i < 10; i++) {
            String out = template.requestBody("netty-http:http://localhost:{{port}}/foo?param" + i + "=value" + i, "Hello World", String.class);
            assertEquals("param" + i + "=value" + i, out);
        }
        assertMockEndpointsSatisfied();

        Set<ObjectName> endpointQueryResult = getMBeanServer().queryNames(new ObjectName("org.apache.camel:context=camel-*,type=endpoints,name=\"http://0.0.0.0:" + getPort() + "/foo\""), null);
        assertEquals(1, endpointQueryResult.size());

        // should only be 2 endpoints in JMX
        Set<ObjectName> set = getMBeanServer().queryNames(new ObjectName("*:context=camel-*,type=endpoints,*"), null);
        assertEquals(2, set.size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("netty-http:http://0.0.0.0:{{port}}/foo")
                    .to("mock:foo")
                    .transform().header(Exchange.HTTP_QUERY);
            }
        };
    }

}
