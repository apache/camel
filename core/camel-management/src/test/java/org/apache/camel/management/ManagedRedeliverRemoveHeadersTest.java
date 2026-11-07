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

import java.util.concurrent.atomic.AtomicInteger;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_PROCESSOR;
import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_ROUTE;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An exchange that completes after a redelivery counts as redelivered also when the route removes the Camel headers
 * after the redelivery, which takes the CamelRedelivered header away (CAMEL-26712).
 */
@DisabledOnOs(OS.AIX)
public class ManagedRedeliverRemoveHeadersTest extends ManagementTestSupport {

    private final AtomicInteger calls = new AtomicInteger();

    @Test
    public void testRedeliveredAlsoWhenTheHeadersAreRemoved() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();

        getMockEndpoint("mock:result").expectedMessageCount(2);
        getMockEndpoint("mock:result").message(0).header("CamelRedelivered").isNull();

        template.sendBody("direct:removing", "Hello");
        template.sendBody("direct:keeping", "World");

        assertMockEndpointsSatisfied();

        for (String route : new String[] { "removing", "keeping" }) {
            ObjectName on = getCamelObjectName(TYPE_ROUTE, route);
            assertEquals(1L, mbeanServer.getAttribute(on, "ExchangesCompleted"), route);
            assertEquals(0L, mbeanServer.getAttribute(on, "FailuresHandled"), route);
            assertEquals(1L, mbeanServer.getAttribute(on, "Redeliveries"), route);

            on = getCamelObjectName(TYPE_PROCESSOR, route + "-flaky");
            assertEquals(1L, mbeanServer.getAttribute(on, "Redeliveries"), route);
        }
    }

    @Test
    public void testContinuedIsNotRedelivered() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();

        getMockEndpoint("mock:continued").expectedMessageCount(1);

        template.sendBody("direct:continued", "Bye");

        assertMockEndpointsSatisfied();

        ObjectName on = getCamelObjectName(TYPE_ROUTE, "continued");
        assertEquals(1L, mbeanServer.getAttribute(on, "ExchangesCompleted"));
        assertEquals(0L, mbeanServer.getAttribute(on, "Redeliveries"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                onException(IllegalStateException.class).maximumRedeliveries(2).redeliveryDelay(0);
                onException(IllegalArgumentException.class).maximumRedeliveries(2).redeliveryDelay(0).continued(true);

                // the first call fails, the redelivery succeeds; then the Camel headers are removed
                from("direct:removing").routeId("removing")
                        .process(e -> flaky()).id("removing-flaky")
                        .removeHeaders("Camel*")
                        .to("mock:result");

                from("direct:keeping").routeId("keeping")
                        .process(e -> flaky()).id("keeping-flaky")
                        .to("mock:result");

                // every attempt fails, and the failure is continued
                from("direct:continued").routeId("continued")
                        .throwException(new IllegalArgumentException("Forced"))
                        .to("mock:continued");
            }
        };
    }

    private void flaky() {
        if (calls.incrementAndGet() % 2 == 1) {
            throw new IllegalStateException("stock down");
        }
    }
}
