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
package org.apache.camel.dsl.jbang.core.commands.action;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.ai.SourceValidator;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The validator fills the gaps of its walk with what a running integration of the file saw at its endpoints
 * (CAMEL-25750): here the stream cache that the HTTP consumer gives, which a field read cannot read.
 */
@ExtendWith(MockitoExtension.class)
class ValidateWithObservedBodiesTest extends ActionCommandTestSupport {

    private static final String YAML = """
            - route:
                id: orders
                from:
                  uri: platform-http:/orders
                  steps:
                    - setHeader:
                        name: qty
                        expression:
                          simple: ${body.qty}
            """;

    @TempDir
    Path dir;

    @Test
    void theValidatorUsesWhatTheRunningAppSaw() throws Exception {
        writeStatusFile(TEST_PID, status("file:" + dir.resolve("orders.camel.yaml") + ":1"));

        List<String> errors = new ArrayList<>();
        int exit = callWithResponse(() -> {
            errors.addAll(SourceValidator.validate("orders.camel.yaml", YAML, new DefaultCamelCatalog(), null, dir));
            return 0;
        }, routeStructure());

        assertEquals(0, exit);
        assertEquals("route-structure", readActionFile(TEST_PID).getString("action"));
        assertTrue(errors.stream().anyMatch(
                m -> m.contains("in the running app, from: platform-http:/orders gave a ByteArrayInputStreamCache")),
                errors.toString());
    }

    @Test
    void anIntegrationOfAnotherFileIsNotAsked() throws Exception {
        writeStatusFile(TEST_PID, status("file:" + dir.resolve("invoices.camel.yaml") + ":1"));

        List<String> errors = new ArrayList<>();
        callWithResponse(() -> {
            errors.addAll(SourceValidator.validate("orders.camel.yaml", YAML, new DefaultCamelCatalog(), null, dir));
            return 0;
        }, routeStructure());

        assertNull(readActionFile(TEST_PID));
        assertTrue(errors.isEmpty(), errors.toString());
    }

    private static JsonObject status(String source) {
        JsonObject route = new JsonObject();
        route.put("routeId", "orders");
        route.put("source", source);
        JsonArray routes = new JsonArray();
        routes.add(route);
        JsonObject context = new JsonObject();
        context.put("name", "orders");
        JsonObject root = new JsonObject();
        root.put("context", context);
        root.put("routes", routes);
        return root;
    }

    // what the route-structure console of the integration answers: the first step was reached with a stream cache
    private static JsonObject routeStructure() {
        return Jsoner.deserialize("""
                {"routes": [{"routeId": "orders", "code": [
                  {"type": "route", "id": "orders", "level": 0},
                  {"type": "from", "id": "orders", "level": 1, "uri": "platform-http:/orders"},
                  {"type": "setHeader", "id": "setHeader1", "level": 2,
                   "bodyTypes": ["org.apache.camel.converter.stream.ByteArrayInputStreamCache"], "bodyKinds": ["payload"]}
                ]}]}
                """, (JsonObject) null);
    }
}
