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
package org.apache.camel.builder.endpoint;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.openmbean.TabularData;

import org.apache.camel.CamelContext;
import org.apache.camel.ManagementStatisticsLevel;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.isPlatform;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ManagedEnricherEndpointDslTest extends CamelTestSupport {

    @Override
    protected boolean useJmx() {
        return true;
    }

    protected MBeanServer getMBeanServer() {
        return context.getManagementStrategy().getManagementAgent().getMBeanServer();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getManagementStrategy().getManagementAgent().setStatisticsLevel(ManagementStatisticsLevel.Extended);
        return context;
    }

    @Test
    public void testManageEnricher() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MockEndpoint foo = getMockEndpoint("mock:foo");
        foo.expectedMessageCount(2);

        MockEndpoint bar = getMockEndpoint("mock:bar");
        bar.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", "Hello World", "whereto", "foo");
        template.sendBodyAndHeader("direct:start", "Bye World", "whereto", "foo");
        template.sendBodyAndHeader("direct:start", "Hi World", "whereto", "bar");

        assertMockEndpointsSatisfied();

        // get the stats for the route
        MBeanServer mbeanServer = getMBeanServer();

        // get the object name for the enricher
        String id = context.getManagementName();
        ObjectName on = ObjectName.getInstance("org.apache.camel:context=" + id + ",type=processors,name=\"mysend\"");

        // should be on route1
        String routeId = (String) mbeanServer.getAttribute(on, "RouteId");
        assertEquals("myroute", routeId);

        String camelId = (String) mbeanServer.getAttribute(on, "CamelId");
        assertEquals(id, camelId);

        String state = (String) mbeanServer.getAttribute(on, "State");
        assertEquals(ServiceStatus.Started.name(), state);

        String lan = (String) mbeanServer.getAttribute(on, "ExpressionLanguage");
        assertEquals("simple", lan);

        String uri = (String) mbeanServer.getAttribute(on, "Expression");
        assertEquals("direct://${header.whereto}?failIfNoConsumers=false", uri);

        TabularData data = (TabularData) mbeanServer.invoke(on, "extendedInformation", null, null);
        assertNotNull(data);
        assertEquals(2, data.size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new EndpointRouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("myroute")
                        .enrich(direct("${header.whereto}").failIfNoConsumers(false)).id("mysend");

                from(direct("foo")).to(mock("foo"));
                from(direct("bar")).to(mock("bar"));
            }
        };
    }
}
