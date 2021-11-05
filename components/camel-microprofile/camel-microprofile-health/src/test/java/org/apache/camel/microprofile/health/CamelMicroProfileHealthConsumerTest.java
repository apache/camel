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
import org.eclipse.microprofile.health.HealthCheckResponse.Status;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CamelMicroProfileHealthConsumerTest extends CamelMicroProfileHealthTestSupport {

    @Test
    public void testCamelHealthRepositoryUpStatus() {
        HealthCheckRegistry healthCheckRegistry = HealthCheckRegistry.get(context);
        // enable consumers health check
        Object hc = healthCheckRegistry.resolveById("consumers");
        healthCheckRegistry.register(hc);

        CamelMicroProfileReadinessCheck readinessCheck = new CamelMicroProfileReadinessCheck();
        readinessCheck.setCamelContext(context);
        reporter.addHealthCheck(readinessCheck);

        SmallRyeHealth health = reporter.getHealth();

        JsonObject healthObject = getHealthJson(health);
        assertEquals(Status.UP.name(), healthObject.getString("status"));

        JsonArray checks = healthObject.getJsonArray("checks");
        assertEquals(1, checks.size());

        Assertions.assertNotNull(checks.getJsonObject(0).getJsonObject("data"));
        Assertions.assertEquals("healthyRoute", checks.getJsonObject(0).getJsonObject("data").getString("route.id"));

        assertHealthCheckOutput("camel-readiness-checks", Status.UP, checks.getJsonObject(0), jsonObject -> {
            assertEquals(Status.UP.name(), jsonObject.getString("consumer:healthyRoute"));
            assertEquals("healthyRoute", jsonObject.getString("route.id"));
            assertEquals("Started", jsonObject.getString("route.status"));
        });
    }

    @Test
    public void testCamelHealthRepositoryDownStatus() throws Exception {
        HealthCheckRegistry healthCheckRegistry = HealthCheckRegistry.get(context);
        // enable consumers health check
        Object hc = healthCheckRegistry.resolveById("consumers");
        healthCheckRegistry.register(hc);

        CamelMicroProfileReadinessCheck readinessCheck = new CamelMicroProfileReadinessCheck();
        readinessCheck.setCamelContext(context);
        reporter.addHealthCheck(readinessCheck);

        context.getRouteController().stopRoute("healthyRoute");

        SmallRyeHealth health = reporter.getHealth();

        JsonObject healthObject = getHealthJson(health);
        assertEquals(Status.DOWN.name(), healthObject.getString("status"));

        JsonArray checks = healthObject.getJsonArray("checks");
        assertEquals(1, checks.size());

        assertHealthCheckOutput("camel-readiness-checks", Status.DOWN, checks.getJsonObject(0), jsonObject -> {
            assertEquals(Status.DOWN.name(), jsonObject.getString("consumer:healthyRoute"));
            assertEquals("healthyRoute", jsonObject.getString("route.id"));
            assertEquals("Stopped", jsonObject.getString("route.status"));
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
