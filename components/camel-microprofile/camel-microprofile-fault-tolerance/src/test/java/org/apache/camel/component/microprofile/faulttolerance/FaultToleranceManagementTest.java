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
package org.apache.camel.component.microprofile.faulttolerance;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FaultToleranceManagementTest extends CamelTestSupport {

    @Override
    protected boolean useJmx() {
        return true;
    }

    protected MBeanServer getMBeanServer() {
        return context.getManagementStrategy().getManagementAgent().getMBeanServer();
    }

    @Test
    public void testFaultTolerance() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");

        template.sendBody("direct:start", "Hello World");

        MockEndpoint.assertIsSatisfied(context);

        // look inside jmx
        // get the stats for the route
        MBeanServer mbeanServer = getMBeanServer();

        // context name
        String name = context.getManagementName();

        // get the object name for the delayer
        ObjectName on
                = ObjectName.getInstance("org.apache.camel:context=" + name + ",type=processors,name=\"myFaultTolerance\"");

        // should be on start
        String routeId = (String) mbeanServer.getAttribute(on, "RouteId");
        assertEquals("start", routeId);

        Long num = (Long) mbeanServer.getAttribute(on, "Delay");
        assertEquals("5000", num.toString());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("start").circuitBreaker().id("myFaultTolerance").to("direct:foo").onFallback()
                        .transform().constant("Fallback message").end()
                        .to("mock:result");

                from("direct:foo").transform().constant("Bye World");
            }
        };
    }

}
