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

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_ROUTE;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A doCatch that caught the failure of a route that the doTry called (and that failed unhandled) handled the failure,
 * so the calling route does not count a handled failure, as when the doTry failed in the route itself.
 */
@DisabledOnOs(OS.AIX)
class ManagedDoCatchFailuresHandledTest extends ManagementTestSupport {

    @ParameterizedTest
    @ValueSource(strings = { "viaCallee", "inRoute" })
    void doCatchIsNotCountedAsAFailureHandled(String variant) throws Exception {
        getMockEndpoint("mock:catch").expectedMessageCount(1);

        template.sendBody("direct:" + variant, "Hello World");

        assertMockEndpointsSatisfied();

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = getCamelObjectName(TYPE_ROUTE, variant);
        assertEquals(1L, mbeanServer.getAttribute(on, "ExchangesCompleted"));
        assertEquals(0L, mbeanServer.getAttribute(on, "ExchangesFailed"));
        assertEquals(0L, mbeanServer.getAttribute(on, "FailuresHandled"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:viaCallee").routeId("viaCallee")
                        .doTry()
                            .to("direct:failing")
                        .doCatch(IllegalArgumentException.class)
                            .to("mock:catch")
                        .end();
                from("direct:inRoute").routeId("inRoute")
                        .doTry()
                            .throwException(new IllegalArgumentException("in route"))
                        .doCatch(IllegalArgumentException.class)
                            .to("mock:catch")
                        .end();
                // fails unhandled (default error handler)
                from("direct:failing").routeId("failing")
                        .throwException(new IllegalArgumentException("callee"));
            }
        };
    }
}
