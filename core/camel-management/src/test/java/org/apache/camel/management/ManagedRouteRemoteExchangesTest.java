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

import java.util.Map;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.direct.DirectComponent;
import org.apache.camel.component.direct.DirectEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_ROUTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The remote exchange counters of the routes: the exchanges a route processed that came from a remote endpoint. Here
 * the remote endpoint is a direct endpoint that says it is remote.
 */
@DisabledOnOs(OS.AIX)
public class ManagedRouteRemoteExchangesTest extends ManagementTestSupport {

    @Test
    public void testRemoteExchangesOfTheRoutes() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(3);

        template.sendBody("remote:orders", "order 1");
        template.sendBody("remote:orders", "order 2");
        template.sendBody("direct:internal", "tick");
        assertThrows(Exception.class, () -> template.sendBody("remote:orders", "boom"));

        assertMockEndpointsSatisfied();

        // a route with a remote consumer: all its exchanges came from a remote endpoint
        assertCounters("orders", 3, 2, 1, 0);
        // a route with an internal consumer that a remote and an internal route call: only some came from remote
        assertExchangesTotal("audit", 4);
        assertCounters("audit", 3, 2, 1, 0);
        // a route with an internal consumer called internally
        assertCounters("internal", 0, 0, 0, 0);
    }

    @Test
    public void testResetTheRemoteExchanges() throws Exception {
        template.sendBody("remote:orders", "order 1");

        MBeanServer mbeanServer = getMBeanServer();
        mbeanServer.invoke(getCamelObjectName(TYPE_ROUTE, "audit"), "reset", null, null);

        assertCounters("audit", 0, 0, 0, 0);
    }

    private void assertCounters(String routeId, long total, long completed, long failed, long inflight)
            throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = getCamelObjectName(TYPE_ROUTE, routeId);
        assertEquals(total, (Long) mbeanServer.getAttribute(on, "RemoteExchangesTotal"), routeId + " total");
        assertEquals(completed, (Long) mbeanServer.getAttribute(on, "RemoteExchangesCompleted"), routeId + " completed");
        assertEquals(failed, (Long) mbeanServer.getAttribute(on, "RemoteExchangesFailed"), routeId + " failed");
        assertEquals(inflight, (Long) mbeanServer.getAttribute(on, "RemoteExchangesInflight"), routeId + " inflight");
    }

    private void assertExchangesTotal(String routeId, long total) throws Exception {
        ObjectName on = getCamelObjectName(TYPE_ROUTE, routeId);
        assertEquals(total, (Long) getMBeanServer().getAttribute(on, "ExchangesTotal"), routeId + " exchanges");
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        // a direct component with remote endpoints, as a messaging broker or an http server has
        context.addComponent("remote", new DirectComponent() {
            @Override
            protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters)
                    throws Exception {
                Endpoint endpoint = new DirectEndpoint(uri, this) {
                    @Override
                    public boolean isRemote() {
                        return true;
                    }
                };
                setProperties(endpoint, parameters);
                return endpoint;
            }
        });
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("remote:orders").routeId("orders").to("direct:audit");
                from("direct:internal").routeId("internal").to("direct:audit");
                from("direct:audit").routeId("audit")
                        .filter(body().isEqualTo("boom")).throwException(new IllegalArgumentException("Forced")).end()
                        .to("mock:result");
            }
        };
    }
}
