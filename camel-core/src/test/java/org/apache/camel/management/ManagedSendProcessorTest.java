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
package org.apache.camel.management;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version $Revision$
 */
public class ManagedSendProcessorTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        DefaultManagementNamingStrategy naming = (DefaultManagementNamingStrategy) context.getManagementStrategy().getManagementNamingStrategy();
        naming.setHostName("localhost");
        naming.setDomainName("org.apache.camel");
        return context;
    }

    @SuppressWarnings("unchecked")
    public void testManageSendProcessor() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);
        MockEndpoint foo = getMockEndpoint("mock:foo");
        foo.expectedMessageCount(0);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        // get the stats for the route
        MBeanServer mbeanServer = context.getManagementStrategy().getManagementAgent().getMBeanServer();

        // get the object name for the delayer
        ObjectName on = ObjectName.getInstance("org.apache.camel:context=localhost/camel-1,type=processors,name=\"mysend\"");

        // should be on route1
        String routeId = (String) mbeanServer.getAttribute(on, "RouteId");
        assertEquals("route1", routeId);

        // send it somewhere else
        mbeanServer.invoke(on, "changeDestination", new Object[]{"direct:foo"}, new String[]{"java.lang.String"});

        // prepare mocks
        result.reset();
        result.expectedMessageCount(0);
        foo.reset();
        foo.expectedMessageCount(1);

        // send in another message that should be sent to mock:foo
        template.sendBody("direct:start", "Bye World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("mock:result").id("mysend");

                from("direct:foo").to("mock:foo");
            }
        };
    }

}
