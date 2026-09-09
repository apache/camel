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

import java.util.List;

import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Routes created by Kamelets are not registered in JMX by default, so they must not show up as null entries in the
 * managed routes list.
 */
@DisabledOnOs(OS.AIX)
public class ManagedCamelContextKameletRoutesTest extends ManagementTestSupport {

    @Test
    public void testKameletRoutesNotInManagedRoutes() {
        ManagedCamelContext mcc = context.getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);

        assertNull(mcc.getManagedRoute("mysource-1"));

        List<ManagedRouteMBean> routes = mcc.getManagedRoutes();
        assertEquals(1, routes.size());
        assertEquals("route1", routes.get(0).getRouteId());

        routes = mcc.getManagedRoutesByGroup(null);
        assertEquals(1, routes.size());
        assertEquals("route1", routes.get(0).getRouteId());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // simulates the route a Kamelet creates internally, feeding the user route
                RouteDefinition kamelet = from("timer:tick?period=100000").routeId("mysource-1")
                        .to("direct:mysource");
                kamelet.setKamelet(true);

                from("direct:mysource").routeId("route1")
                        .to("mock:result");
            }
        };
    }
}
