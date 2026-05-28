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

import java.util.Date;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_ROUTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisabledOnOs(OS.AIX)
public class ManagedFailureHandledTimestampTest extends ManagementTestSupport {

    @Test
    public void testLastExchangeFailureHandledTimestamp() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();

        MockEndpoint dead = getMockEndpoint("mock:dead");
        dead.expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        ObjectName on = getCamelObjectName(TYPE_ROUTE, "route1");

        Long completed = (Long) mbeanServer.getAttribute(on, "ExchangesCompleted");
        assertEquals(1, completed.longValue());

        Long failed = (Long) mbeanServer.getAttribute(on, "ExchangesFailed");
        assertEquals(0, failed.longValue());

        Long failuresHandled = (Long) mbeanServer.getAttribute(on, "FailuresHandled");
        assertEquals(1, failuresHandled.longValue());

        Date handledTs = (Date) mbeanServer.getAttribute(on, "LastExchangeFailureHandledTimestamp");
        assertNotNull(handledTs);

        Date failureTs = (Date) mbeanServer.getAttribute(on, "LastExchangeFailureTimestamp");
        assertNull(failureTs);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("mock:dead"));

                from("direct:start")
                        .process(exchange -> {
                            throw new IllegalArgumentException("Forced");
                        });
            }
        };
    }
}
