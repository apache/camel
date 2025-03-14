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

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.stream.Stream;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import io.smallrye.health.SmallRyeHealth;
import io.smallrye.health.SmallRyeHealthReporter;
import io.smallrye.health.api.HealthType;
import io.smallrye.health.api.event.HealthStatusChangeEvent;
import io.smallrye.health.registry.HealthRegistries;
import io.smallrye.health.registry.HealthRegistryImpl;
import io.smallrye.mutiny.Uni;
import org.apache.camel.CamelContext;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.impl.health.AbstractHealthCheck;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.util.ReflectionHelper;
import org.eclipse.microprofile.health.HealthCheckResponse;

public class CamelMicroProfileHealthTestSupport extends CamelTestSupport {

    protected SmallRyeHealthReporter reporter = new SmallRyeHealthReporter();

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();

        // Work around to fulfil CDI injected fields
        setupHealthReporterEventFields();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void doPostTearDown() {
        // Hack to clean up all registered checks
        Stream.of(HealthType.LIVENESS, HealthType.READINESS)
                .forEach(type -> {
                    HealthRegistryImpl registry = (HealthRegistryImpl) HealthRegistries.getRegistry(type);
                    try {
                        Field field = registry.getClass().getDeclaredField("checks");
                        field.setAccessible(true);
                        Map<String, Uni<HealthCheckResponse>> checks
                                = (Map<String, Uni<HealthCheckResponse>>) field.get(registry);
                        checks.clear();
                    } catch (NoSuchFieldException | IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        HealthCheckRegistry registry = new CamelMicroProfileHealthCheckRegistry();
        camelContext.getCamelContextExtension().addContextPlugin(HealthCheckRegistry.class, registry);
        return camelContext;
    }

    protected void setupHealthReporterEventFields() {
        Stream.of("healthEvent", "livenessEvent", "readinessEvent", "wellnessEvent", "startupEvent")
                .forEach(fieldName -> {
                    try {
                        Field field = SmallRyeHealthReporter.class.getDeclaredField(fieldName);
                        ReflectionHelper.setField(field, reporter, new NoOpHealthEvent());
                    } catch (NoSuchFieldException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    protected void assertHealthCheckOutput(
            String expectedName,
            HealthCheckResponse.Status expectedState,
            JsonObject healthObject) {
        CamelMicroProfileHealthTestHelper.assertHealthCheckOutput(expectedName, expectedState, healthObject);
    }

    protected void assertHealthCheckOutput(
            String expectedName,
            HealthCheckResponse.Status expectedState,
            JsonArray healthObjects) {
        CamelMicroProfileHealthTestHelper.assertHealthCheckOutput(expectedName, expectedState, healthObjects);
    }

    protected void assertHealthCheckOutput(
            String expectedName,
            HealthCheckResponse.Status expectedState,
            JsonObject healthObject,
            Consumer<JsonObject> dataObjectAssertions) {

        CamelMicroProfileHealthTestHelper.assertHealthCheckOutput(expectedName, expectedState, healthObject,
                dataObjectAssertions);
    }

    protected void assertHealthCheckOutput(
            String expectedName,
            HealthCheckResponse.Status expectedState,
            JsonArray healthObjects,
            Consumer<JsonObject> dataObjectAssertions) {

        CamelMicroProfileHealthTestHelper.assertHealthCheckOutput(expectedName, expectedState, healthObjects,
                dataObjectAssertions);
    }

    protected JsonObject getHealthJson(SmallRyeHealth health) {
        return CamelMicroProfileHealthTestHelper.getHealthJson(reporter, health);
    }

    protected String getHealthOutput(SmallRyeHealth health) {
        return CamelMicroProfileHealthTestHelper.getHealthOutput(reporter, health);
    }

    protected HealthCheck createLivenessCheck(String id, boolean enabled, Consumer<HealthCheckResultBuilder> consumer) {
        HealthCheck livenessCheck = new AbstractHealthCheck(id) {
            @Override
            protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
                consumer.accept(builder);
            }

            @Override
            public boolean isLiveness() {
                return true;
            }

            @Override
            public boolean isReadiness() {
                return false;
            }
        };
        livenessCheck.setEnabled(enabled);
        return livenessCheck;
    }

    protected HealthCheck createReadinessCheck(String id, boolean enabled, Consumer<HealthCheckResultBuilder> consumer) {
        HealthCheck readinessCheck = new AbstractHealthCheck(id) {
            @Override
            protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
                consumer.accept(builder);
            }

            @Override
            public boolean isReadiness() {
                return true;
            }

            @Override
            public boolean isLiveness() {
                return false;
            }
        };
        readinessCheck.setEnabled(enabled);
        return readinessCheck;
    }

    static final class NoOpHealthEvent implements Event<HealthStatusChangeEvent> {
        @Override
        public void fire(HealthStatusChangeEvent healthStatusChangeEvent) {
        }

        @Override
        public <U extends HealthStatusChangeEvent> CompletionStage<U> fireAsync(U u) {
            return null;
        }

        @Override
        public <U extends HealthStatusChangeEvent> CompletionStage<U> fireAsync(U u, NotificationOptions notificationOptions) {
            return null;
        }

        @Override
        public Event<HealthStatusChangeEvent> select(Annotation... annotations) {
            return null;
        }

        @Override
        public <U extends HealthStatusChangeEvent> Event<U> select(Class<U> aClass, Annotation... annotations) {
            return null;
        }

        @Override
        public <U extends HealthStatusChangeEvent> Event<U> select(TypeLiteral<U> typeLiteral, Annotation... annotations) {
            return null;
        }
    }
}
