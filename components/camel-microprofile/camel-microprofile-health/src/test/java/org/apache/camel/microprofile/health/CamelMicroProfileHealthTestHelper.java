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

import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.stream.JsonParser;

import io.smallrye.health.SmallRyeHealth;
import io.smallrye.health.SmallRyeHealthReporter;
import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.impl.health.AbstractHealthCheck;
import org.apache.camel.impl.health.ProducersHealthCheckRepository;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultProducer;
import org.eclipse.microprofile.health.HealthCheckResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class CamelMicroProfileHealthTestHelper {

    private CamelMicroProfileHealthTestHelper() {
        // Utility class
    }

    public static void assertHealthCheckOutput(
            String expectedName,
            HealthCheckResponse.Status expectedState,
            JsonObject healthObject) {
        assertHealthCheckOutput(expectedName, expectedState, healthObject, null);
    }

    public static void assertHealthCheckOutput(
            String expectedName,
            HealthCheckResponse.Status expectedState,
            JsonArray healthObjects) {
        assertHealthCheckOutput(expectedName, expectedState, healthObjects, null);
    }

    public static void assertHealthCheckOutput(
            String expectedName,
            HealthCheckResponse.Status expectedState,
            JsonObject healthObject,
            Consumer<JsonObject> dataObjectAssertions) {

        assertEquals(expectedName, healthObject.getString("name"));
        assertEquals(expectedState.name(), healthObject.getString("status"));

        if (dataObjectAssertions != null) {
            dataObjectAssertions.accept(healthObject.getJsonObject("data"));
        }
    }

    public static void assertHealthCheckOutput(
            String expectedName,
            HealthCheckResponse.Status expectedState,
            JsonArray healthObjects,
            Consumer<JsonObject> dataObjectAssertions) {

        boolean match = false;

        for (int i = 0; i < healthObjects.size(); i++) {
            JsonObject healthObject = healthObjects.getJsonObject(i);

            if (expectedName.equals(healthObject.getString("name"))) {
                match = true;

                assertEquals(expectedState.name(), healthObject.getString("status"));

                if (dataObjectAssertions != null) {
                    dataObjectAssertions.accept(healthObject.getJsonObject("data"));
                }
            }
        }

        assertTrue(match, "No elements with name " + expectedName);
    }

    public static JsonObject getHealthJson(SmallRyeHealthReporter reporter, SmallRyeHealth health) {
        JsonParser parser = Json.createParser(new StringReader(getHealthOutput(reporter, health)));
        assertTrue(parser.hasNext(), "Health check content is empty");
        parser.next();
        return parser.getObject();
    }

    public static String getHealthOutput(SmallRyeHealthReporter reporter, SmallRyeHealth health) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        reporter.reportHealth(outputStream, health);
        return outputStream.toString(StandardCharsets.UTF_8);
    }

    public static void dumpHealth(SmallRyeHealthReporter reporter, SmallRyeHealth health) {
        reporter.reportHealth(System.out, health);
    }

    public static class MyComponent extends DefaultComponent {
        private final MyHealthCheck check = new MyHealthCheck("my-hc");

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
            return new MyEndpoint(uri, this, check);
        }

        public void setState(HealthCheck.State state) {
            check.setState(state);
        }
    }

    public static class MyEndpoint extends DefaultEndpoint {
        private final MyHealthCheck check;

        public MyEndpoint(String endpointUri, Component component, MyHealthCheck check) {
            super(endpointUri, component);

            this.check = check;
        }

        @Override
        protected void doStart() throws Exception {
            super.doStart();

            var repo = HealthCheckHelper.getHealthCheckRepository(
                    getCamelContext(),
                    ProducersHealthCheckRepository.REPOSITORY_ID,
                    ProducersHealthCheckRepository.class);

            if (repo != null) {
                repo.addHealthCheck(this.check);
            }
        }

        @Override
        public Producer createProducer() throws Exception {
            return new DefaultProducer(this) {
                @Override
                public void process(Exchange exchange) throws Exception {
                }
            };
        }

        @Override
        public org.apache.camel.Consumer createConsumer(Processor processor) throws Exception {
            return new DefaultConsumer(this, processor) {
            };
        }
    }

    public static class MyHealthCheck extends AbstractHealthCheck {
        private HealthCheck.State state;

        public MyHealthCheck(String id) {
            super(id);

            this.state = State.UP;
        }

        @Override
        protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
            builder.state(state);
        }

        @Override
        public boolean isLiveness() {
            return false;
        }

        public void setState(HealthCheck.State state) {
            this.state = state;
        }

    }
}
