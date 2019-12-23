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

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.stream.JsonParser;

import io.smallrye.health.SmallRyeHealth;
import io.smallrye.health.SmallRyeHealthReporter;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.eclipse.microprofile.health.HealthCheckResponse;

public class CamelMicroProfileHealthTestSupport extends CamelTestSupport {

    protected SmallRyeHealthReporter reporter;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        reporter = new SmallRyeHealthReporter();
    }

    protected void assertHealthCheckOutput(String expectedName, HealthCheckResponse.State expectedState, JsonObject healthObject, Consumer<JsonObject> dataObjectAssertions) {
        assertEquals(expectedName, healthObject.getString("name"));
        assertEquals(expectedState.name(), healthObject.getString("status"));
        dataObjectAssertions.accept(healthObject.getJsonObject("data"));
    }

    protected JsonObject getHealthJson(SmallRyeHealth health) {
        JsonParser parser = Json.createParser(new StringReader(getHealthOutput(health)));
        assertTrue("Health check content is empty", parser.hasNext());
        parser.next();
        return parser.getObject();
    }

    protected String getHealthOutput(SmallRyeHealth health) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        reporter.reportHealth(outputStream, health);
        return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
    }

    protected HealthCheck createLivenessCheck(String id, boolean enabled, Consumer<HealthCheckResultBuilder> consumer) {
        HealthCheck healthCheck = new AbstractCamelMicroProfileLivenessCheck(id) {
            @Override
            protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
                consumer.accept(builder);
            }
        };
        healthCheck.getConfiguration().setEnabled(enabled);
        return healthCheck;
    }

    protected HealthCheck createReadinessCheck(String id, boolean enabled, Consumer<HealthCheckResultBuilder> consumer) {
        HealthCheck healthCheck = new AbstractCamelMicroProfileReadinessCheck(id) {
            @Override
            protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
                consumer.accept(builder);
            }
        };
        healthCheck.getConfiguration().setEnabled(enabled);
        return healthCheck;
    }
}
