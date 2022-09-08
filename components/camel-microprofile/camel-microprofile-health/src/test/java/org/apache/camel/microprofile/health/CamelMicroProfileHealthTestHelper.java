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
import java.util.function.Consumer;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.stream.JsonParser;

import io.smallrye.health.SmallRyeHealth;
import io.smallrye.health.SmallRyeHealthReporter;
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
            JsonObject healthObject,
            Consumer<JsonObject> dataObjectAssertions) {

        assertEquals(expectedName, healthObject.getString("name"));
        assertEquals(expectedState.name(), healthObject.getString("status"));

        if (dataObjectAssertions != null) {
            dataObjectAssertions.accept(healthObject.getJsonObject("data"));
        }
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
}
