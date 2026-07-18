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
package org.apache.camel.component.platform.http.main;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.apache.camel.CamelContext;
import org.apache.camel.console.DevConsoleRegistry;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.console.ContextDevConsole;
import org.apache.camel.impl.console.DefaultDevConsoleRegistry;
import org.apache.camel.impl.console.RouteDevConsole;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.util.json.DeserializationException;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevConsoleOpenApiTest {

    @RegisterExtension
    AvailablePortFinder.Port port = AvailablePortFinder.find();

    private CamelContext camelContext;
    private ManagementHttpServer server;

    @BeforeEach
    void setUp() throws Exception {
        camelContext = new DefaultCamelContext();
        camelContext.setDevConsole(true);

        DefaultDevConsoleRegistry registry = new DefaultDevConsoleRegistry(camelContext);
        registry.register(new ContextDevConsole());
        registry.register(new RouteDevConsole());
        camelContext.getCamelContextExtension().addContextPlugin(DevConsoleRegistry.class, registry);

        camelContext.start();

        server = new ManagementHttpServer();
        server.setCamelContext(camelContext);
        server.setHost("0.0.0.0");
        server.setPort(port.getPort());
        server.setPath("/");
        server.setDevConsoleEnabled(true);
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) {
            server.stop();
        }
        if (camelContext != null) {
            camelContext.stop();
        }
    }

    @Test
    void testDevConsoleOpenApiEndpoint() throws IOException, InterruptedException, DeserializationException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port.getPort() + "/q/dev/api"))
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> response = HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("content-type").orElse("").contains("application/json"));

        Object parsed = Jsoner.deserialize(response.body());
        assertTrue(parsed instanceof JsonObject);
        JsonObject root = (JsonObject) parsed;

        assertEquals("3.0.3", root.getString("openapi"));

        JsonObject info = root.getJsonObject("info");
        assertNotNull(info);
        assertEquals("Camel Dev Console API", info.getString("title"));
        assertNotNull(info.getString("version"));

        JsonObject paths = root.getJsonObject("paths");
        assertNotNull(paths);

        // should have context and route consoles (sorted alphabetically)
        assertNotNull(paths.get("/q/dev/context"), "Should have context console path");
        assertNotNull(paths.get("/q/dev/route"), "Should have route console path");

        // verify route console has request body with parameters
        JsonObject routePath = ((JsonObject) paths.get("/q/dev/route"));
        JsonObject post = routePath.getJsonObject("post");
        assertNotNull(post);
        assertEquals("route", post.getString("operationId"));

        JsonObject requestBody = post.getJsonObject("requestBody");
        assertNotNull(requestBody, "Route console should have request body with parameters");

        JsonObject content = requestBody.getJsonObject("content");
        JsonObject appJson = content.getJsonObject("application/json");
        JsonObject schema = appJson.getJsonObject("schema");
        assertEquals("object", schema.getString("type"));

        JsonObject properties = schema.getJsonObject("properties");
        assertNotNull(properties);
        assertNotNull(properties.get("filter"), "Should have filter parameter");
        assertNotNull(properties.get("limit"), "Should have limit parameter");

        // verify filter property has correct type
        JsonObject filterProp = (JsonObject) properties.get("filter");
        assertEquals("string", filterProp.getString("type"));

        // verify limit property has correct type
        JsonObject limitProp = (JsonObject) properties.get("limit");
        assertEquals("integer", limitProp.getString("type"));
    }
}
