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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.json.JsonArray;
import javax.json.JsonObject;

import io.smallrye.health.SmallRyeHealth;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.health.HealthCheckRepository;
import org.eclipse.microprofile.health.HealthCheckResponse.Status;
import org.junit.jupiter.api.Test;

import static org.apache.camel.microprofile.health.CamelMicroProfileHealthCheckRegistry.ROUTES_CHECK_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CamelMicroProfileHealthCheckRepositoryTest extends CamelMicroProfileHealthTestSupport {

    @Test
    public void testCamelHealthRepositoryUpStatus() {
        HealthCheckRegistry healthCheckRegistry = HealthCheckRegistry.get(context);
        // enable routes health check
        Object hc = healthCheckRegistry.resolveById("routes");
        healthCheckRegistry.register(hc);

        SmallRyeHealth health = reporter.getHealth();

        JsonObject healthObject = getHealthJson(health);
        assertEquals(Status.UP.name(), healthObject.getString("status"));

        JsonArray checks = healthObject.getJsonArray("checks");
        assertEquals(1, checks.size());

        assertHealthCheckOutput(ROUTES_CHECK_NAME, Status.UP, checks.getJsonObject(0));
    }

    @Test
    public void testCamelHealthRepositoryDownStatus() throws Exception {
        HealthCheckRegistry healthCheckRegistry = HealthCheckRegistry.get(context);
        // enable routes health check
        Object hc = healthCheckRegistry.resolveById("routes");
        healthCheckRegistry.register(hc);

        context.getRouteController().stopRoute("healthyRoute");

        SmallRyeHealth health = reporter.getHealth();

        JsonObject healthObject = getHealthJson(health);
        assertEquals(Status.DOWN.name(), healthObject.getString("status"));

        JsonArray checks = healthObject.getJsonArray("checks");
        assertEquals(1, checks.size());

        assertHealthCheckOutput(ROUTES_CHECK_NAME, Status.DOWN, checks.getJsonObject(0), jsonObject -> {
            assertEquals("healthyRoute", jsonObject.getString("route.id"));
            assertEquals(ServiceStatus.Stopped.name(), jsonObject.getString("route.status"));
        });
    }

    @Test
    public void testCamelHealthCheckRepositoryDisabled() throws Exception {
        HealthCheckRegistry healthCheckRegistry = HealthCheckRegistry.get(context);
        // register disabled routes health check repository
        HealthCheckRepository hc = (HealthCheckRepository) healthCheckRegistry.resolveById("routes");
        hc.setEnabled(false);
        healthCheckRegistry.register(hc);

        context.getRouteController().stopRoute("healthyRoute");

        SmallRyeHealth health = reporter.getHealth();

        JsonObject healthObject = getHealthJson(health);
        assertEquals(Status.UP.name(), healthObject.getString("status"));

        JsonArray checks = healthObject.getJsonArray("checks");
        assertEquals(0, checks.size());
    }

    @Test
    public void testCamelHealthCheckRepositorySpecificChecksDisabled() throws Exception {
        List<HealthCheck> repositoryChecks = new ArrayList<>();
        repositoryChecks.add(createLivenessCheck("check-1", true, builder -> builder.up()));
        repositoryChecks.add(createLivenessCheck("check-2", false, builder -> builder.up()));
        repositoryChecks.add(createLivenessCheck("check-3", true, builder -> builder.down()));

        HealthCheckRepository repository = new HealthCheckRepository() {
            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public void setEnabled(boolean enabled) {
                // Noop
            }

            @Override
            public Stream<HealthCheck> stream() {
                return repositoryChecks.stream();
            }

            @Override
            public String getId() {
                return "custom-repository";
            }
        };

        HealthCheckRegistry healthCheckRegistry = HealthCheckRegistry.get(context);
        healthCheckRegistry.register(repository);

        SmallRyeHealth health = reporter.getHealth();

        JsonObject healthObject = getHealthJson(health);
        assertEquals(Status.DOWN.name(), healthObject.getString("status"));

        JsonArray checks = healthObject.getJsonArray("checks");
        assertEquals(2, checks.size());

        assertHealthCheckOutput("check-3", Status.DOWN, checks.getJsonObject(0));

        assertHealthCheckOutput("check-1", Status.UP, checks.getJsonObject(1));
    }

    @Test
    public void testRoutesRepositoryUnregister() {
        HealthCheckRegistry healthCheckRegistry = HealthCheckRegistry.get(context);
        Object hc = healthCheckRegistry.resolveById("routes");
        healthCheckRegistry.register(hc);

        SmallRyeHealth health = reporter.getHealth();

        JsonObject healthObject = getHealthJson(health);
        assertEquals(Status.UP.name(), healthObject.getString("status"));

        JsonArray checks = healthObject.getJsonArray("checks");
        assertEquals(1, checks.size());

        assertHealthCheckOutput(ROUTES_CHECK_NAME, Status.UP, checks.getJsonObject(0));

        healthCheckRegistry.unregister(hc);

        health = reporter.getHealth();

        healthObject = getHealthJson(health);
        assertEquals(Status.UP.name(), healthObject.getString("status"));

        checks = healthObject.getJsonArray("checks");
        assertEquals(0, checks.size());
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
