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

import java.util.Map;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import io.smallrye.health.SmallRyeHealth;
import org.apache.camel.ServiceStatus;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.impl.engine.ExplicitCamelContextNameStrategy;
import org.apache.camel.impl.health.AbstractHealthCheck;
import org.apache.camel.impl.health.ContextHealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponse.Status;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class CamelMicroProfileHealthCheckTest extends CamelMicroProfileHealthTestSupport {

    @Test
    public void testCamelContextHealthCheckUpStatus() {
        context.setNameStrategy(new ExplicitCamelContextNameStrategy("health-context"));
        context.getCamelContextExtension().getContextPlugin(HealthCheckRegistry.class).register(new ContextHealthCheck());

        SmallRyeHealth health = reporter.getHealth();

        JsonObject healthObject = getHealthJson(health);
        assertEquals(Status.UP.name(), healthObject.getString("status"));

        JsonArray checks = healthObject.getJsonArray("checks");
        assertEquals(2, checks.size());

        assertHealthCheckOutput("context", Status.UP, checks.getJsonObject(0), checksJson -> {
            assertEquals("health-context", checksJson.getString("context.name"));
            assertEquals(ServiceStatus.Started.name(), checksJson.getString("context.status"));
        });
        assertHealthCheckOutput("context", Status.UP, checks.getJsonObject(1), checksJson -> {
            assertEquals("health-context", checksJson.getString("context.name"));
            assertEquals(ServiceStatus.Started.name(), checksJson.getString("context.status"));
        });
    }

    @Test
    public void testCamelContextHealthCheckDownStatus() {
        context.setNameStrategy(new ExplicitCamelContextNameStrategy("health-context"));
        context.getCamelContextExtension().getContextPlugin(HealthCheckRegistry.class).register(new ContextHealthCheck());

        context.stop();

        SmallRyeHealth health = reporter.getHealth();

        JsonObject healthObject = getHealthJson(health);
        assertEquals(Status.DOWN.name(), healthObject.getString("status"));

        JsonArray checks = healthObject.getJsonArray("checks");
        assertEquals(2, checks.size());

        assertHealthCheckOutput("context", Status.DOWN, checks.getJsonObject(0), checksJson -> {
            assertEquals("health-context", checksJson.getString("context.name"));
            assertEquals(ServiceStatus.Stopped.name(), checksJson.getString("context.status"));
        });
        assertHealthCheckOutput("context", Status.DOWN, checks.getJsonObject(1), checksJson -> {
            assertEquals("health-context", checksJson.getString("context.name"));
            assertEquals(ServiceStatus.Stopped.name(), checksJson.getString("context.status"));
        });
    }

    @Test
    public void testCamelMicroProfileLivenessCheckUpStatus() {
        HealthCheckRegistry healthCheckRegistry = HealthCheckRegistry.get(context);

        healthCheckRegistry.register(createLivenessCheck("liveness-1", true, builder -> builder.up()));
        healthCheckRegistry.register(createLivenessCheck("liveness-2", true, builder -> builder.up()));
        healthCheckRegistry.register(createReadinessCheck("readiness-3", true, builder -> builder.up()));

        SmallRyeHealth health = reporter.getLiveness();

        JsonObject healthObject = getHealthJson(health);
        assertEquals(Status.UP.name(), healthObject.getString("status"));

        JsonArray checks = healthObject.getJsonArray("checks");
        assertEquals(2, checks.size());

        assertHealthCheckOutput("liveness-1", Status.UP, checks.getJsonObject(0));
        assertHealthCheckOutput("liveness-2", Status.UP, checks.getJsonObject(1));
    }

    @Test
    public void testCamelMicroProfileLivenessCheckDownStatus() {
        HealthCheckRegistry healthCheckRegistry = HealthCheckRegistry.get(context);

        healthCheckRegistry.register(createLivenessCheck("liveness-1", true, builder -> builder.up()));
        healthCheckRegistry.register(createLivenessCheck("liveness-2", true, builder -> builder.down()));
        healthCheckRegistry.register(createReadinessCheck("readiness-3", true, builder -> builder.up()));

        SmallRyeHealth health = reporter.getLiveness();

        JsonObject healthObject = getHealthJson(health);
        assertEquals(Status.DOWN.name(), healthObject.getString("status"));

        JsonArray checks = healthObject.getJsonArray("checks");
        assertEquals(2, checks.size());

        assertHealthCheckOutput("liveness-1", Status.UP, checks.getJsonObject(0));
        assertHealthCheckOutput("liveness-2", Status.DOWN, checks.getJsonObject(1));
    }

    @Test
    public void testCamelMicroProfileReadinessCheckUpStatus() {
        HealthCheckRegistry healthCheckRegistry = HealthCheckRegistry.get(context);

        healthCheckRegistry.register(createLivenessCheck("liveness-1", true, builder -> builder.up()));
        healthCheckRegistry.register(createReadinessCheck("readiness-1", true, builder -> builder.up()));
        healthCheckRegistry.register(createReadinessCheck("readiness-2", true, builder -> builder.up()));

        SmallRyeHealth health = reporter.getReadiness();

        JsonObject healthObject = getHealthJson(health);
        assertEquals(Status.UP.name(), healthObject.getString("status"));

        JsonArray checks = healthObject.getJsonArray("checks");
        assertEquals(2, checks.size());

        assertHealthCheckOutput("readiness-2", Status.UP, checks.getJsonObject(0));
        assertHealthCheckOutput("readiness-1", Status.UP, checks.getJsonObject(1));
    }

    @Test
    public void testCamelMicroProfileReadinessCheckDownStatus() {
        HealthCheckRegistry healthCheckRegistry = HealthCheckRegistry.get(context);

        healthCheckRegistry.register(createLivenessCheck("liveness-1", true, builder -> builder.up()));
        healthCheckRegistry.register(createReadinessCheck("readiness-1", true, builder -> builder.up()));
        healthCheckRegistry.register(createReadinessCheck("readiness-2", true, builder -> builder.down()));

        SmallRyeHealth health = reporter.getReadiness();

        JsonObject healthObject = getHealthJson(health);
        assertEquals(Status.DOWN.name(), healthObject.getString("status"));

        JsonArray checks = healthObject.getJsonArray("checks");
        assertEquals(2, checks.size());

        assertHealthCheckOutput("readiness-2", Status.DOWN, checks.getJsonObject(0));
        assertHealthCheckOutput("readiness-1", Status.UP, checks.getJsonObject(1));
    }

    @Test
    public void testCamelHealthCheckDisabled() {
        HealthCheckRegistry healthCheckRegistry = HealthCheckRegistry.get(context);

        healthCheckRegistry.register(createReadinessCheck("disabled-check", false, builder -> builder.up()));

        SmallRyeHealth health = reporter.getHealth();

        JsonObject healthObject = getHealthJson(health);
        assertEquals(Status.UP.name(), healthObject.getString("status"));
        JsonArray checks = healthObject.getJsonArray("checks");
        assertEquals(0, checks.size());
    }

    @Test
    public void testCamelHealthCheckDisabledAtRuntime() {
        HealthCheckRegistry healthCheckRegistry = HealthCheckRegistry.get(context);

        HealthCheck readinessCheck = createReadinessCheck("disabled-check", true, builder -> builder.up());
        healthCheckRegistry.register(readinessCheck);
        readinessCheck.setEnabled(false);

        SmallRyeHealth health = reporter.getHealth();

        JsonObject healthObject = getHealthJson(health);
        assertEquals(Status.UP.name(), healthObject.getString("status"));
        JsonArray checks = healthObject.getJsonArray("checks");
        assertEquals(1, checks.size());

        assertHealthCheckOutput("disabled-check", Status.UP, checks.getJsonObject(0), jsonObject -> {
            assertFalse(jsonObject.getBoolean("check.enabled"));
        });
    }

    @Test
    public void testNoCamelHealthChecksRegistered() {
        SmallRyeHealth health = reporter.getHealth();

        JsonObject healthObject = getHealthJson(health);
        assertEquals(Status.UP.name(), healthObject.getString("status"));

        JsonArray checks = healthObject.getJsonArray("checks");
        assertEquals(0, checks.size());
    }

    @Test
    public void testHealthCheckMultipleRegistrations() {
        HealthCheckRegistry healthCheckRegistry = HealthCheckRegistry.get(context);
        HealthCheck check = new AbstractHealthCheck("test-check") {
            @Override
            protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
                builder.up();
            }
        };

        for (int i = 0; i < 5; i++) {
            healthCheckRegistry.register(check);
        }

        SmallRyeHealth health = reporter.getHealth();

        JsonObject healthObject = getHealthJson(health);
        assertEquals(Status.UP.name(), healthObject.getString("status"));
        JsonArray checks = healthObject.getJsonArray("checks");
        assertEquals(1, checks.size());

        assertHealthCheckOutput("test-check", Status.UP, checks.getJsonObject(0));
    }

    @Test
    public void testHealthCheckMultipleUnregisters() {
        HealthCheckRegistry healthCheckRegistry = HealthCheckRegistry.get(context);
        HealthCheck check = new AbstractHealthCheck("test-check") {
            @Override
            protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
                builder.up();
            }
        };
        healthCheckRegistry.register(check);

        SmallRyeHealth health = reporter.getHealth();

        JsonObject healthObject = getHealthJson(health);
        assertEquals(Status.UP.name(), healthObject.getString("status"));
        JsonArray checks = healthObject.getJsonArray("checks");
        assertEquals(1, checks.size());

        assertHealthCheckOutput("test-check", Status.UP, checks.getJsonObject(0));

        for (int i = 0; i < 5; i++) {
            healthCheckRegistry.unregister(check);
        }

        health = reporter.getHealth();
        healthObject = getHealthJson(health);
        assertEquals(Status.UP.name(), healthObject.getString("status"));
        checks = healthObject.getJsonArray("checks");
        assertEquals(0, checks.size());
    }

    @Test
    public void testHealthCheckUncheckedException() {
        String errorMessage = "Forced exception";

        HealthCheckRegistry healthCheckRegistry = HealthCheckRegistry.get(context);
        HealthCheck check = new AbstractHealthCheck("exception-check") {
            @Override
            protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
                throw new IllegalStateException(errorMessage);
            }
        };
        healthCheckRegistry.register(check);

        SmallRyeHealth health = reporter.getHealth();

        JsonObject healthObject = getHealthJson(health);
        assertEquals(Status.DOWN.name(), healthObject.getString("status"));
        JsonArray checks = healthObject.getJsonArray("checks");
        assertEquals(1, checks.size());

        assertHealthCheckOutput(CamelMicroProfileHealthCheck.class.getName(), Status.DOWN, checks.getJsonObject(0),
                jsonObject -> {
                    assertEquals(errorMessage, jsonObject.getString("rootCause"));
                });
    }

    @Test
    public void testHealthCheckCheckedException() {
        String errorMessage = "Forced exception";

        HealthCheckRegistry healthCheckRegistry = HealthCheckRegistry.get(context);
        HealthCheck check = new AbstractHealthCheck("exception-check") {
            @Override
            protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
                builder.error(new Exception(errorMessage));
                builder.down();
            }
        };
        healthCheckRegistry.register(check);

        SmallRyeHealth health = reporter.getHealth();

        JsonObject healthObject = getHealthJson(health);
        assertEquals(Status.DOWN.name(), healthObject.getString("status"));
        JsonArray checks = healthObject.getJsonArray("checks");
        assertEquals(1, checks.size());

        assertHealthCheckOutput("exception-check", Status.DOWN, checks.getJsonObject(0), jsonObject -> {
            assertEquals(errorMessage, jsonObject.getString("error.message"));
            assertNotNull(jsonObject.getString("error.stacktrace"));
        });
    }

    @Test
    public void testExposureLevelDefault() {
        HealthCheckRegistry healthCheckRegistry = HealthCheckRegistry.get(context);

        HealthCheck failingCheck = new AbstractHealthCheck("failing-check") {
            @Override
            public boolean isLiveness() {
                return false;
            }

            @Override
            protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
                builder.error(new Exception("Forced exception"));
                builder.down();
            }
        };

        healthCheckRegistry.register(failingCheck);
        healthCheckRegistry.register(createLivenessCheck("liveness-1", true, builder -> builder.detail("test", "test").up()));

        SmallRyeHealth health = reporter.getHealth();

        JsonObject healthObject = getHealthJson(health);
        assertEquals(HealthCheckResponse.Status.DOWN.name(), healthObject.getString("status"));

        JsonArray checks = healthObject.getJsonArray("checks");
        assertEquals(2, checks.size());

        JsonObject check = checks.getJsonObject(0);
        assertHealthCheckOutput("liveness-1", HealthCheckResponse.Status.UP, check, result -> {
            assertNotNull(result);
            assertEquals("test", result.getString("test"));
        });

        JsonObject failedCheck = checks.getJsonObject(1);
        assertHealthCheckOutput("failing-check", HealthCheckResponse.Status.DOWN, failedCheck, result -> {
            assertNotNull(result);
            assertEquals("Forced exception", result.getString("error.message"));
            assertNotNull(result.getString("error.stacktrace"));
        });
    }

    @Test
    public void testExposureLevelFull() {
        HealthCheckRegistry healthCheckRegistry = HealthCheckRegistry.get(context);
        healthCheckRegistry.setExposureLevel("full");

        HealthCheck failingCheck = new AbstractHealthCheck("failing-check") {
            @Override
            public boolean isLiveness() {
                return false;
            }

            @Override
            protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
                builder.error(new Exception("Forced exception"));
                builder.down();
            }
        };

        healthCheckRegistry.register(failingCheck);
        healthCheckRegistry.register(createLivenessCheck("liveness-1", true, builder -> builder.detail("test", "test").up()));

        SmallRyeHealth health = reporter.getHealth();

        JsonObject healthObject = getHealthJson(health);
        assertEquals(HealthCheckResponse.Status.DOWN.name(), healthObject.getString("status"));

        JsonArray checks = healthObject.getJsonArray("checks");
        assertEquals(2, checks.size());

        JsonObject livenessCheck = checks.getJsonObject(0);
        assertHealthCheckOutput("liveness-1", HealthCheckResponse.Status.UP, livenessCheck, result -> {
            assertNotNull(result);
            assertEquals("test", result.getString("test"));
        });

        JsonObject failedCheck = checks.getJsonObject(1);
        assertHealthCheckOutput("failing-check", HealthCheckResponse.Status.DOWN, failedCheck, result -> {
            assertNotNull(result);
            assertEquals("Forced exception", result.getString("error.message"));
            assertNotNull(result.getString("error.stacktrace"));
        });
    }

    @Test
    public void testExposureLevelOneline() {
        HealthCheckRegistry healthCheckRegistry = HealthCheckRegistry.get(context);
        healthCheckRegistry.setExposureLevel("oneline");

        HealthCheck failingCheck = new AbstractHealthCheck("failing-check") {
            @Override
            public boolean isLiveness() {
                return false;
            }

            @Override
            protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
                builder.error(new Exception("Forced exception"));
                builder.down();
            }
        };

        healthCheckRegistry.register(failingCheck);
        healthCheckRegistry.register(createLivenessCheck("liveness-1", true, builder -> builder.detail("test", "test").up()));

        SmallRyeHealth health = reporter.getHealth();

        JsonObject healthObject = getHealthJson(health);
        assertEquals(HealthCheckResponse.Status.DOWN.name(), healthObject.getString("status"));

        JsonArray checks = healthObject.getJsonArray("checks");
        assertEquals(2, checks.size());

        JsonObject livenessCheck = checks.getJsonObject(0);
        assertHealthCheckOutput("liveness-1", HealthCheckResponse.Status.UP, livenessCheck, result -> {
            assertNull(result);
        });

        JsonObject failedCheck = checks.getJsonObject(1);
        assertHealthCheckOutput("failing-check", HealthCheckResponse.Status.DOWN, failedCheck, result -> {
            assertNull(result);
        });
    }
}
