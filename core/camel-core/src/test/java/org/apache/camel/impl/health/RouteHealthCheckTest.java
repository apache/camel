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
package org.apache.camel.impl.health;

import java.util.Collections;

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RouteHealthCheckTest {

    private static final String TEST_ROUTE_ID = "Test-Route";

    @Test
    public void testDoCallDoesNotHaveNPEWhenJmxDisabled() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:input").id(TEST_ROUTE_ID).log("Message");
            }
        });
        context.start();

        Route route = context.getRoute(TEST_ROUTE_ID);

        RouteHealthCheck healthCheck = new RouteHealthCheck(route);
        final HealthCheckResultBuilder builder = HealthCheckResultBuilder.on(healthCheck);

        healthCheck.doCall(builder, Collections.emptyMap());

        context.stop();
    }

    @Test
    public void testHealthCheckIsUpWhenRouteIsNotAutoStartup() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:input").id(TEST_ROUTE_ID).autoStartup(false).log("Message");
            }
        });
        context.start();

        Route route = context.getRoute(TEST_ROUTE_ID);

        RouteHealthCheck healthCheck = new RouteHealthCheck(route);
        final HealthCheckResultBuilder builder = HealthCheckResultBuilder.on(healthCheck);

        healthCheck.doCall(builder, Collections.emptyMap());
        HealthCheck.Result result = builder.build();

        Assertions.assertEquals(HealthCheck.State.UP, result.getState());

        context.stop();
    }

}
