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
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ResilienceManagementTest extends CamelTestSupport {

    @Override
    protected boolean useJmx() {
        return true;
    }

    protected MBeanServer getMBeanServer() {
        return context.getManagementStrategy().getManagementAgent().getMBeanServer();
    }

    @Test
    public void testResilience() throws Exception {
        test("start", "myResilience");
    }

    @Test
    public void testResilienceWithTimeOut() throws Exception {
        test("start.with.timeout.enabled", "myResilienceWithTimeout");
    }

    public void test(String routId, String circuitBreakerName) throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");

        template.sendBody("direct:" + routId, "Hello World");

        MockEndpoint.assertIsSatisfied(context);

        // look inside jmx
        // get the stats for the route
        MBeanServer mbeanServer = getMBeanServer();

        // context name
        String name = context.getManagementName();

        // get the object name for the delayer
        ObjectName on = ObjectName
                .getInstance("org.apache.camel:context=" + name + ",type=processors,name=\"" + circuitBreakerName + "\"");

        // should be on start
        String routeId = (String) mbeanServer.getAttribute(on, "RouteId");
        assertEquals(routId, routeId);

        Integer num = (Integer) mbeanServer.getAttribute(on, "CircuitBreakerMinimumNumberOfCalls");
        assertEquals("100", num.toString());

        Integer totalRequests = (Integer) mbeanServer.getAttribute(on, "NumberOfSuccessfulCalls");
        assertEquals(1, totalRequests.intValue());

        Integer errorCount = (Integer) mbeanServer.getAttribute(on, "NumberOfFailedCalls");
        assertEquals(0, errorCount.intValue());

        String state = (String) mbeanServer.getAttribute(on, "CircuitBreakerState");
        assertEquals("CLOSED", state);

        mbeanServer.invoke(on, "transitionToOpenState", null, null);
        state = (String) mbeanServer.getAttribute(on, "CircuitBreakerState");
        assertEquals("OPEN", state);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("start").circuitBreaker().id("myResilience").to("direct:foo").onFallback()
                        .transform().constant("Fallback message").end()
                        .to("mock:result");

                from("direct:start.with.timeout.enabled").routeId("start.with.timeout.enabled").circuitBreaker().id("myResilienceWithTimeout")
                        .resilience4jConfiguration().timeoutEnabled(true).timeoutDuration(2000).end()
                        .to("direct:foo").onFallback()
                        .transform().constant("Fallback message").end()
                        .to("mock:result");

                from("direct:foo").transform().constant("Bye World");
            }
        };
    }

}
