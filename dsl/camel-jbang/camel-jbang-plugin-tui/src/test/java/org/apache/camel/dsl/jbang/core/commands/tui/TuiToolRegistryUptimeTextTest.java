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
package org.apache.camel.dsl.jbang.core.commands.tui;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TuiToolRegistryUptimeTextTest {

    @Test
    void addsHumanReadableUptimeNextToMillis() {
        JsonObject context = new JsonObject();
        context.put("name", "mqtt");
        context.put("uptime", 13_800_803L);

        TuiToolRegistry.addUptimeText(context);

        assertEquals(13_800_803L, context.get("uptime"), "the millis value stays for callers that compute with it");
        assertEquals("3h50m", context.get("uptimeText"));
    }

    @Test
    void leavesTextUptimeAndUnrelatedValuesAlone() {
        JsonObject route = new JsonObject();
        route.put("routeId", "route1");
        route.put("uptime", "3h56m");
        JsonArray routes = new JsonArray();
        routes.add(route);
        JsonObject root = new JsonObject();
        root.put("routes", routes);
        JsonObject nested = new JsonObject();
        nested.put("uptime", 65_000L);
        root.put("nested", nested);

        TuiToolRegistry.addUptimeText(root);

        assertFalse(route.containsKey("uptimeText"), "route uptime is already text");
        assertEquals("1m5s", nested.get("uptimeText"), "nested objects are handled too");
        TuiToolRegistry.addUptimeText("not json");
        TuiToolRegistry.addUptimeText(null);
    }
}
