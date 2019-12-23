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
package org.apache.camel.management;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.seda.SedaEndpoint;
import org.apache.camel.support.DefaultExchange;
import org.junit.Test;

/**
 * Tests mbeans is registered when adding a 2nd route from within an existing route.
 */
public class ManagedRouteAddFromRouteTest extends ManagementTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // put a message pre-early on the seda queue, to trigger the route, which
                // then would add a 2nd route during CamelContext startup. This is a test
                // to ensure the foo route is not started too soon, and thus adding the 2nd
                // route works as expected
                SedaEndpoint seda = context.getEndpoint("seda:start", SedaEndpoint.class);
                seda.getQueue().put(new DefaultExchange(context));

                from("seda:start").routeId("foo")
                        .process(exchange -> {
                            RouteBuilder child = new RouteBuilder() {
                                @Override
                                public void configure() throws Exception {
                                    from("seda:bar").routeId("bar").to("mock:bar");
                                }
                            };
                            context.addRoutes(child);
                        })
                        .to("mock:result");
            }
        };
    }

    @Test
    public void testAddRouteFromRoute() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName route1 = ObjectName.getInstance("org.apache.camel:context=camel-1,type=routes,name=\"foo\"");

        // should be started
        String state = (String) mbeanServer.getAttribute(route1, "State");
        assertEquals("Should be started", ServiceStatus.Started.name(), state);

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);

        // should route the message we put on the seda queue before

        result.assertIsSatisfied();

        // find the 2nd route
        ObjectName route2 = ObjectName.getInstance("org.apache.camel:context=camel-1,type=routes,name=\"bar\"");

        // should be started
        state = (String) mbeanServer.getAttribute(route2, "State");
        assertEquals("Should be started", ServiceStatus.Started.name(), state);
    }

}
