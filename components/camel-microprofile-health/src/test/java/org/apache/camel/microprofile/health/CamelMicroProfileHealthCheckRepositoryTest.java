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
package org.apache.camel.microprofile.health;

import javax.json.JsonArray;
import javax.json.JsonObject;

import io.smallrye.health.SmallRyeHealth;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.impl.health.RoutesHealthCheckRepository;
import org.eclipse.microprofile.health.HealthCheckResponse.State;
import org.junit.Test;

public class CamelMicroProfileHealthCheckRepositoryTest extends CamelMicroProfileHealthTestSupport {

    @Test
    public void testCamelHealthRepositoryUpStatus() {
        HealthCheckRegistry healthCheckRegistry = HealthCheckRegistry.get(context);
        healthCheckRegistry.addRepository(new RoutesHealthCheckRepository());

        CamelMicroProfileReadinessCheck readinessCheck = new CamelMicroProfileReadinessCheck();
        readinessCheck.setCamelContext(context);
        reporter.addHealthCheck(readinessCheck);

        SmallRyeHealth health = reporter.getHealth();

        JsonObject healthObject = getHealthJson(health);
        assertEquals(State.UP.name(), healthObject.getString("status"));

        JsonArray checks = healthObject.getJsonArray("checks");
        assertEquals(1, checks.size());

        assertHealthCheckOutput("camel-readiness-checks", State.UP, checks.getJsonObject(0), jsonObject -> {
            assertEquals(State.UP.name(), jsonObject.getString("route:healthyRoute"));
        });
    }

    @Test
    public void testCamelHealthRepositoryDownStatus() throws Exception {
        HealthCheckRegistry healthCheckRegistry = HealthCheckRegistry.get(context);
        healthCheckRegistry.addRepository(new RoutesHealthCheckRepository());

        CamelMicroProfileReadinessCheck readinessCheck = new CamelMicroProfileReadinessCheck();
        readinessCheck.setCamelContext(context);
        reporter.addHealthCheck(readinessCheck);

        context.getRouteController().stopRoute("healthyRoute");

        SmallRyeHealth health = reporter.getHealth();

        JsonObject healthObject = getHealthJson(health);
        assertEquals(State.DOWN.name(), healthObject.getString("status"));

        JsonArray checks = healthObject.getJsonArray("checks");
        assertEquals(1, checks.size());

        assertHealthCheckOutput("camel-readiness-checks", State.DOWN, checks.getJsonObject(0), jsonObject -> {
            assertEquals(State.DOWN.name(), jsonObject.getString("route:healthyRoute"));
        });
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("healthyRoute")
                    .setBody(constant("Hello Camel MicroProfile Health"));
            }
        };
    }
}
