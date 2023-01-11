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
import org.apache.camel.BindToRegistry;
import org.apache.camel.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CamelMicroProfileHealthRegistryBindingTest extends CamelMicroProfileHealthTestSupport {

    @BindToRegistry
    private HealthCheck check = createReadinessCheck("registry-binding-check", true, builder -> builder.up());

    @Test
    public void testHealthCheckCamelRegistryDiscovery() {
        SmallRyeHealth health = reporter.getHealth();

        JsonObject healthObject = getHealthJson(health);
        assertEquals(HealthCheckResponse.Status.UP.name(), healthObject.getString("status"));
        JsonArray checks = healthObject.getJsonArray("checks");
        assertEquals(1, checks.size());
    }
}
