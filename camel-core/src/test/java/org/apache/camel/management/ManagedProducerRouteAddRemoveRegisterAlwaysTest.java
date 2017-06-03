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

import java.util.Set;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version
 */
public class ManagedProducerRouteAddRemoveRegisterAlwaysTest extends ManagementTestSupport {

    private int services = 10;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getManagementStrategy().getManagementAgent().setRegisterAlways(true);
        return context;
    }

    public void testRouteAddRemoteRouteWithRecipientList() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        result.assertIsSatisfied();

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=services,*");

        // number of services
        Set<ObjectName> names = mbeanServer.queryNames(on, null);
        assertEquals(services, names.size());

        // number of producers
        ObjectName onP = ObjectName.getInstance("org.apache.camel:context=camel-1,type=producers,*");
        Set<ObjectName> namesP = mbeanServer.queryNames(onP, null);
        assertEquals(3, namesP.size());

        log.info("Adding 2nd route");

        // add a 2nd route
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:bar").routeId("bar").recipientList(header("bar"));
            }
        });

        // and send a message to it
        MockEndpoint bar = getMockEndpoint("mock:bar");
        bar.expectedMessageCount(1);
        template.sendBodyAndHeader("direct:bar", "Hello World", "bar", "mock:bar");
        bar.assertIsSatisfied();

        // there should still be the same number of services
        names = mbeanServer.queryNames(on, null);
        assertEquals(services, names.size());

        // but as its recipient list which is dynamic-to we add new producers because we have register always
        namesP = mbeanServer.queryNames(onP, null);
        assertEquals(5, namesP.size());

        log.info("Removing 2nd route");

        // now remove the 2nd route
        context.stopRoute("bar");
        boolean removed = context.removeRoute("bar");
        assertTrue(removed);

        // there should still be the same number of services
        names = mbeanServer.queryNames(on, null);
        assertEquals(services, names.size());

        // and we still have the other producers, but not the one from the 2nd route that was removed
        namesP = mbeanServer.queryNames(onP, null);
        assertEquals(4, namesP.size());

        log.info("Shutting down...");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("foo").to("log:foo").to("mock:result");
            }
        };
    }

}