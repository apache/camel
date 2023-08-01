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
import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.health.HealthCheckRepository;
import org.eclipse.microprofile.health.HealthCheckResponse.Status;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CamelMicroProfileHealthComponentsTest extends CamelMicroProfileHealthTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        camelContext.addComponent("my", new CamelMicroProfileHealthTestHelper.MyComponent());

        HealthCheckRegistry healthCheckRegistry = HealthCheckRegistry.get(camelContext);
        // enable producers health check
        HealthCheckRepository hc = (HealthCheckRepository) healthCheckRegistry.resolveById("producers");
        hc.setEnabled(true);
        healthCheckRegistry.register(hc);

        return camelContext;
    }

    @Test
    public void testCamelComponentRepositoryUpStatus() {
        context.getComponent("my", CamelMicroProfileHealthTestHelper.MyComponent.class).setState(HealthCheck.State.UP);

        SmallRyeHealth health = reporter.getHealth();

        JsonObject healthObject = getHealthJson(health);
        assertEquals(Status.UP.name(), healthObject.getString("status"));

        JsonArray checks = healthObject.getJsonArray("checks");

        assertHealthCheckOutput("camel-producers", Status.UP, checks);
    }

    @Test
    public void testCamelComponentRepositoryDownStatus() {
        context.getComponent("my", CamelMicroProfileHealthTestHelper.MyComponent.class).setState(HealthCheck.State.DOWN);

        SmallRyeHealth health = reporter.getHealth();

        JsonObject healthObject = getHealthJson(health);
        assertEquals(Status.DOWN.name(), healthObject.getString("status"));

        JsonArray checks = healthObject.getJsonArray("checks");

        assertHealthCheckOutput("camel-producers", Status.DOWN, checks);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("my:start").routeId("healthyRoute")
                        .setBody(constant("Hello Camel MicroProfile Health"));
            }
        };
    }
}
