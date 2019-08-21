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
package org.apache.camel.impl;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.RoutePolicySupport;
import org.junit.Test;

public class RoutePolicySupportTest extends ContextTestSupport {

    private MyRoutePolicy policy = new MyRoutePolicy();

    public static class MyRoutePolicy extends RoutePolicySupport {
    }

    @Test
    public void testLifecycleCallbacks() throws Exception {
        Route route = context.getRoute("foo");

        assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("foo"));

        policy.startRoute(route);

        assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("foo"));

        policy.suspendRoute(route);

        assertEquals(ServiceStatus.Suspended, context.getRouteController().getRouteStatus("foo"));

        policy.resumeRoute(route);

        assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("foo"));

        policy.stopRoute(route);

        assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("foo"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start").routeId("foo").routePolicy(policy).autoStartup(false).to("mock:result");
            }
        };
    }
}
