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

import org.apache.camel.ManagementStatisticsLevel;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 *
 */
public class ManagedRedeliverRouteOnlyTest extends ManagementTestSupport {

    @Test
    public void testRedeliver() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();

        getMockEndpoint("mock:foo").expectedMessageCount(1);

        Object out = template.requestBody("direct:start", "Hello World");
        assertEquals("Error", out);

        assertMockEndpointsSatisfied();

        ObjectName on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=routes,name=\"route1\"");

        Long num = (Long) mbeanServer.getAttribute(on, "ExchangesCompleted");
        assertEquals(1, num.longValue());

        num = (Long) mbeanServer.getAttribute(on, "ExchangesFailed");
        assertEquals(0, num.longValue());

        num = (Long) mbeanServer.getAttribute(on, "FailuresHandled");
        assertEquals(1, num.longValue());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.getManagementStrategy().getManagementAgent().setStatisticsLevel(ManagementStatisticsLevel.RoutesOnly);

                onException(Exception.class).handled(true)
                    .redeliveryDelay(0)
                    .maximumRedeliveries(4).logStackTrace(false)
                    .setBody().constant("Error");

                from("direct:start")
                    .to("mock:foo")
                    .process(exchange -> {
                        log.info("Invoking me");

                        throw new IllegalArgumentException("Damn");
                    }).id("myprocessor");
            }
        };
    }
}
