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
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FaultToleranceManagementTest extends CamelTestSupport {

    @Override
    protected boolean useJmx() {
        return true;
    }

    protected MBeanServer getMBeanServer() {
        return context.getManagementStrategy().getManagementAgent().getMBeanServer();
    }

    @Test
    void testFaultTolerance() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");

        template.sendBody("direct:start", "Hello World");

        MockEndpoint.assertIsSatisfied(context);

        MBeanServer mbeanServer = getMBeanServer();
        String name = context.getManagementName();
        ObjectName on
                = ObjectName.getInstance("org.apache.camel:context=" + name + ",type=processors,name=\"myFaultTolerance\"");

        // configuration attributes
        String routeId = (String) mbeanServer.getAttribute(on, "RouteId");
        assertEquals("start", routeId);

        Long delay = (Long) mbeanServer.getAttribute(on, "Delay");
        assertEquals(5000L, delay);

        Float failureRatio = (Float) mbeanServer.getAttribute(on, "FailureRatio");
        assertEquals(0.5f, failureRatio);

        Integer requestVolumeThreshold = (Integer) mbeanServer.getAttribute(on, "RequestVolumeThreshold");
        assertEquals(20, requestVolumeThreshold);

        Integer successThreshold = (Integer) mbeanServer.getAttribute(on, "SuccessThreshold");
        assertEquals(1, successThreshold);

        String state = (String) mbeanServer.getAttribute(on, "CircuitBreakerState");
        assertEquals("CLOSED", state);

        // live call counters (one successful call was made above)
        Long successfulCalls = (Long) mbeanServer.getAttribute(on, "NumberOfSuccessfulCalls");
        assertEquals(1L, successfulCalls);

        Long failedCalls = (Long) mbeanServer.getAttribute(on, "NumberOfFailedCalls");
        assertEquals(0L, failedCalls);

        Long notPermittedCalls = (Long) mbeanServer.getAttribute(on, "NumberOfNotPermittedCalls");
        assertEquals(0L, notPermittedCalls);

        // test reset operation
        mbeanServer.invoke(on, "transitionToCloseState", null, null);
        state = (String) mbeanServer.getAttribute(on, "CircuitBreakerState");
        assertEquals("CLOSED", state);

        // counters should be reset after transitionToCloseState
        successfulCalls = (Long) mbeanServer.getAttribute(on, "NumberOfSuccessfulCalls");
        assertEquals(0L, successfulCalls);
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
