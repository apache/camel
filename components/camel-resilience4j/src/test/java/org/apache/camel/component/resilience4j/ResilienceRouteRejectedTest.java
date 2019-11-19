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
package org.apache.camel.component.resilience4j;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class ResilienceRouteRejectedTest extends CamelTestSupport {

    @Override
    protected boolean useJmx() {
        return true;
    }

    protected MBeanServer getMBeanServer() {
        return context.getManagementStrategy().getManagementAgent().getMBeanServer();
    }

    @Test
    public void testResilience() throws Exception {
        // look inside jmx
        // get the stats for the route
        MBeanServer mbeanServer = getMBeanServer();

        // context name
        String name = context.getManagementName();

        ObjectName on = ObjectName.getInstance("org.apache.camel:context=" + name + ",type=processors,name=\"myResilience\"");

        // force it into open state
        mbeanServer.invoke(on, "transitionToForcedOpenState", null, null);
        String state = (String)mbeanServer.getAttribute(on, "CircuitBreakerState");
        assertEquals("FORCED_OPEN", state);

        // send message which should get rejected, so the message is not changed
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").circuitBreaker().id("myResilience").to("direct:foo").to("log:foo").end().to("log:result").to("mock:result");

                from("direct:foo").transform().constant("Bye World");
            }
        };
    }

}
