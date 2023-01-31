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

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import io.smallrye.health.SmallRyeHealth;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.health.HealthCheckRegistry;
import org.eclipse.microprofile.health.HealthCheckResponse.Status;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CamelMicroProfileHealthConsumerTest extends CamelMicroProfileHealthTestSupport {

    @Test
    public void testCamelHealthRepositoryUpStatus() {
        HealthCheckRegistry healthCheckRegistry = HealthCheckRegistry.get(context);
        // enable consumers health check
        Object hc = healthCheckRegistry.resolveById("consumers");
        healthCheckRegistry.register(hc);

        SmallRyeHealth health = reporter.getHealth();

        JsonObject healthObject = getHealthJson(health);
        assertEquals(Status.UP.name(), healthObject.getString("status"));

        JsonArray checks = healthObject.getJsonArray("checks");
        assertEquals(1, checks.size());

        assertHealthCheckOutput("camel-consumers", Status.UP, checks.getJsonObject(0));
    }

    @Test
    public void testCamelHealthRepositoryDownStatus() throws Exception {
        HealthCheckRegistry healthCheckRegistry = HealthCheckRegistry.get(context);
        // enable consumers health check
        Object hc = healthCheckRegistry.resolveById("consumers");
        healthCheckRegistry.register(hc);

        context.getRouteController().stopRoute("healthyRoute");

        SmallRyeHealth health = reporter.getHealth();

        JsonObject healthObject = getHealthJson(health);
        assertEquals(Status.DOWN.name(), healthObject.getString("status"));

        JsonArray checks = healthObject.getJsonArray("checks");
        assertEquals(1, checks.size());

        assertHealthCheckOutput("camel-consumers", Status.DOWN, checks.getJsonObject(0), jsonObject -> {
            assertEquals("healthyRoute", jsonObject.getString("route.id"));
            assertEquals(ServiceStatus.Stopped.name(), jsonObject.getString("route.status"));
        });
    }

    @Test
    public void testRoutesRepositoryUnregister() {
        HealthCheckRegistry healthCheckRegistry = HealthCheckRegistry.get(context);
        Object hc = healthCheckRegistry.resolveById("consumers");
        healthCheckRegistry.register(hc);

        SmallRyeHealth health = reporter.getHealth();

        JsonObject healthObject = getHealthJson(health);
        assertEquals(Status.UP.name(), healthObject.getString("status"));

        JsonArray checks = healthObject.getJsonArray("checks");
        assertEquals(1, checks.size());

        assertHealthCheckOutput("camel-consumers", Status.UP, checks.getJsonObject(0));

        healthCheckRegistry.unregister(hc);

        health = reporter.getHealth();

        healthObject = getHealthJson(health);
        assertEquals(Status.UP.name(), healthObject.getString("status"));

        checks = healthObject.getJsonArray("checks");
        assertEquals(0, checks.size());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("healthyRoute")
                        .setBody(constant("Hello Camel MicroProfile Health"));
            }
        };
    }
}
