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
import org.apache.camel.ServiceStatus;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.impl.engine.ExplicitCamelContextNameStrategy;
import org.eclipse.microprofile.health.HealthCheckResponse.State;
import org.junit.Test;

public class CamelMicroProfileHealthCheckTest extends CamelMicroProfileHealthTestSupport {

    @Test
    public void testCamelContextHealthCheckUpStatus() {
        context.setNameStrategy(new ExplicitCamelContextNameStrategy("health-context"));
        CamelMicroProfileContextCheck check = new CamelMicroProfileContextCheck();
        check.setCamelContext(context);
        reporter.addHealthCheck(check);

        SmallRyeHealth health = reporter.getHealth();

        JsonObject healthObject = getHealthJson(health);

        assertEquals(State.UP.name(), healthObject.getString("status"));

        JsonArray checks = healthObject.getJsonArray("checks");
        assertEquals(1, checks.size());

        assertHealthCheckOutput("camel", State.UP, checks.getJsonObject(0), checksJson -> {
            assertEquals(ServiceStatus.Started.toString(), checksJson.getString("contextStatus"));
            assertEquals("health-context", checksJson.getString("name"));
        });
    }

    @Test
    public void testCamelContextHealthCheckDownStatus() {
        context.setNameStrategy(new ExplicitCamelContextNameStrategy("health-context"));
        CamelMicroProfileContextCheck check = new CamelMicroProfileContextCheck();
        check.setCamelContext(context);
        reporter.addHealthCheck(check);

        context.stop();

        SmallRyeHealth health = reporter.getHealth();

        JsonObject healthObject = getHealthJson(health);

        assertEquals(State.DOWN.name(), healthObject.getString("status"));

        JsonArray checks = healthObject.getJsonArray("checks");
        assertEquals(1, checks.size());

        assertHealthCheckOutput("camel", State.DOWN, checks.getJsonObject(0), checksJson -> {
            assertEquals(ServiceStatus.Stopped.toString(), checksJson.getString("contextStatus"));
            assertEquals("health-context", checksJson.getString("name"));
        });
    }

    @Test
    public void testCamelMicroProfileLivenessCheckUpStatus() {
        HealthCheckRegistry healthCheckRegistry = HealthCheckRegistry.get(context);

        healthCheckRegistry.register(createLivenessCheck("liveness-1", true, builder -> builder.up()));
        healthCheckRegistry.register(createLivenessCheck("liveness-2", true, builder -> builder.up()));
        healthCheckRegistry.register(createReadinessCheck("readiness-3", true, builder -> builder.up()));

        CamelMicroProfileLivenessCheck livenessCheck = new CamelMicroProfileLivenessCheck();
        livenessCheck.setCamelContext(context);
        reporter.addHealthCheck(livenessCheck);

        SmallRyeHealth health = reporter.getHealth();

        JsonObject healthObject = getHealthJson(health);
        assertEquals(State.UP.name(), healthObject.getString("status"));

        JsonArray checks = healthObject.getJsonArray("checks");
        assertEquals(1, checks.size());

        JsonObject checksObject = checks.getJsonObject(0);
        assertHealthCheckOutput("camel-liveness-checks", State.UP, checksObject, checksJson -> {
            assertEquals(State.UP.name(), checksJson.getString("liveness-1"));
            assertEquals(State.UP.name(), checksJson.getString("liveness-2"));
        });
    }

    @Test
    public void testCamelMicroProfileLivenessCheckDownStatus() {
        HealthCheckRegistry healthCheckRegistry = HealthCheckRegistry.get(context);

        healthCheckRegistry.register(createLivenessCheck("liveness-1", true, builder -> builder.up()));
        healthCheckRegistry.register(createLivenessCheck("liveness-2", true, builder -> builder.down()));
        healthCheckRegistry.register(createReadinessCheck("readiness-3", true, builder -> builder.up()));

        CamelMicroProfileLivenessCheck livenessCheck = new CamelMicroProfileLivenessCheck();
        livenessCheck.setCamelContext(context);
        reporter.addHealthCheck(livenessCheck);

        SmallRyeHealth health = reporter.getHealth();

        JsonObject healthObject = getHealthJson(health);
        assertEquals(State.DOWN.name(), healthObject.getString("status"));

        JsonArray checks = healthObject.getJsonArray("checks");
        assertEquals(1, checks.size());

        JsonObject checksObject = checks.getJsonObject(0);
        assertHealthCheckOutput("camel-liveness-checks", State.DOWN, checksObject, checksJson -> {
            assertEquals(State.UP.name(), checksJson.getString("liveness-1"));
            assertEquals(State.DOWN.name(), checksJson.getString("liveness-2"));
        });
    }

    @Test
    public void testCamelMicroProfileReadinessCheckUpStatus() {
        HealthCheckRegistry healthCheckRegistry = HealthCheckRegistry.get(context);

        healthCheckRegistry.register(createLivenessCheck("liveness-1", true, builder -> builder.up()));
        healthCheckRegistry.register(createReadinessCheck("readiness-1", true, builder -> builder.up()));
        healthCheckRegistry.register(createReadinessCheck("readiness-2", true, builder -> builder.up()));

        CamelMicroProfileReadinessCheck readinessCheck = new CamelMicroProfileReadinessCheck();
        readinessCheck.setCamelContext(context);
        reporter.addHealthCheck(readinessCheck);

        SmallRyeHealth health = reporter.getHealth();

        JsonObject healthObject = getHealthJson(health);
        assertEquals(State.UP.name(), healthObject.getString("status"));

        JsonArray checks = healthObject.getJsonArray("checks");
        assertEquals(1, checks.size());

        assertHealthCheckOutput("camel-readiness-checks", State.UP, checks.getJsonObject(0), jsonObject -> {
            assertEquals(State.UP.name(), jsonObject.getString("readiness-1"));
            assertEquals(State.UP.name(), jsonObject.getString("readiness-2"));
        });
    }

    @Test
    public void testCamelMicroProfileReadinessCheckDownStatus() {
        HealthCheckRegistry healthCheckRegistry = HealthCheckRegistry.get(context);

        healthCheckRegistry.register(createLivenessCheck("liveness-1", true, builder -> builder.up()));
        healthCheckRegistry.register(createReadinessCheck("readiness-1", true, builder -> builder.up()));
        healthCheckRegistry.register(createReadinessCheck("readiness-2", true, builder -> builder.down()));

        CamelMicroProfileReadinessCheck readinessCheck = new CamelMicroProfileReadinessCheck();
        readinessCheck.setCamelContext(context);
        reporter.addHealthCheck(readinessCheck);

        SmallRyeHealth health = reporter.getHealth();

        JsonObject healthObject = getHealthJson(health);
        assertEquals(State.DOWN.name(), healthObject.getString("status"));

        JsonArray checks = healthObject.getJsonArray("checks");
        assertEquals(1, checks.size());

        assertHealthCheckOutput("camel-readiness-checks", State.DOWN, checks.getJsonObject(0), jsonObject -> {
            assertEquals(State.UP.name(), jsonObject.getString("readiness-1"));
            assertEquals(State.DOWN.name(), jsonObject.getString("readiness-2"));
        });
    }

    @Test
    public void testCamelHealthCheckDisabled() {
        HealthCheckRegistry healthCheckRegistry = HealthCheckRegistry.get(context);

        healthCheckRegistry.register(createReadinessCheck("disabled-check", false, builder -> builder.up()));

        CamelMicroProfileReadinessCheck readinessCheck = new CamelMicroProfileReadinessCheck();
        readinessCheck.setCamelContext(context);
        reporter.addHealthCheck(readinessCheck);

        SmallRyeHealth health = reporter.getHealth();

        JsonObject healthObject = getHealthJson(health);
        assertEquals(State.UP.name(), healthObject.getString("status"));

        JsonArray checks = healthObject.getJsonArray("checks");
        assertEquals(1, checks.size());

        assertHealthCheckOutput("camel-readiness-checks", State.UP, checks.getJsonObject(0), jsonObject -> {
            assertNull(jsonObject);
        });
    }

    @Test
    public void testNoCamelHealthChecksRegistered() {
        CamelMicroProfileReadinessCheck readinessCheck = new CamelMicroProfileReadinessCheck();
        readinessCheck.setCamelContext(context);
        reporter.addHealthCheck(readinessCheck);

        CamelMicroProfileLivenessCheck livenessCheck = new CamelMicroProfileLivenessCheck();
        livenessCheck.setCamelContext(context);
        reporter.addHealthCheck(livenessCheck);

        SmallRyeHealth health = reporter.getHealth();

        JsonObject healthObject = getHealthJson(health);
        assertEquals(State.DOWN.name(), healthObject.getString("status"));

        JsonArray checks = healthObject.getJsonArray("checks");
        assertEquals(2, checks.size());

        assertHealthCheckOutput("camel-readiness-checks", State.DOWN, checks.getJsonObject(0), jsonObject -> {
            assertNull(jsonObject);
        });

        assertHealthCheckOutput("camel-liveness-checks", State.DOWN, checks.getJsonObject(1), jsonObject -> {
            assertNull(jsonObject);
        });
    }
}
