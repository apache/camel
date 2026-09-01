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
package org.apache.camel.tooling.model;

import java.util.List;

import org.apache.camel.tooling.model.DevConsoleModel.DevConsoleOptionModel;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The unit test class for {@link DevConsoleOpenApiHelper}.
 */
class DevConsoleOpenApiHelperTest {

    private static DevConsoleOptionModel option(String name, String type, boolean required) {
        DevConsoleOptionModel option = new DevConsoleOptionModel();
        option.setName(name);
        option.setType(type);
        option.setRequired(required);
        option.setDescription("The " + name);
        return option;
    }

    @Test
    void readOnlyConsoleWithOptionsBuildsGetWithParameters() {
        DevConsoleModel model = new DevConsoleModel();
        model.setName("route");
        model.setTitle("Route");
        model.setDescription("Route information");
        model.setReadOnly(true);
        model.addOption(option("filter", "string", false));
        model.addOption(option("limit", "integer", false));

        JsonObject pathItem = DevConsoleOpenApiHelper.buildPathItem(model);

        assertNull(pathItem.get("post"));
        JsonObject get = pathItem.getJsonObject("get");
        assertNotNull(get);
        assertEquals("route", get.getString("operationId"));
        assertNull(get.get("requestBody"));
        var parameters = get.getCollection("parameters");
        assertEquals(2, parameters.size());
    }

    @Test
    void mutatingConsoleWithOptionsBuildsPostWithRequestBody() {
        DevConsoleModel model = new DevConsoleModel();
        model.setName("route");
        model.setTitle("Route");
        model.setDescription("Route information");
        model.setReadOnly(false);
        model.addOption(option("action", "string", true));

        JsonObject pathItem = DevConsoleOpenApiHelper.buildPathItem(model);

        assertNull(pathItem.get("get"));
        JsonObject post = pathItem.getJsonObject("post");
        assertNotNull(post);
        assertNull(post.get("parameters"));
        JsonObject requestBody = post.getJsonObject("requestBody");
        assertNotNull(requestBody);
        JsonObject schema = requestBody.getJsonObject("content").getJsonObject("application/json").getJsonObject("schema");
        assertTrue(schema.getCollection("required").contains("action"));
    }

    @Test
    void consoleWithoutOptionsHasNeitherParametersNorRequestBody() {
        DevConsoleModel model = new DevConsoleModel();
        model.setName("context");
        model.setTitle("CamelContext");
        model.setDescription("Overall information about the CamelContext");
        model.setReadOnly(true);

        JsonObject pathItem = DevConsoleOpenApiHelper.buildPathItem(model);

        JsonObject get = pathItem.getJsonObject("get");
        assertNotNull(get);
        assertNull(get.get("parameters"));
        assertNull(get.get("requestBody"));
    }

    @Test
    void consoleWithResponseSchemaPopulatesResponseContent() {
        DevConsoleModel model = new DevConsoleModel();
        model.setName("circuit-breaker");
        model.setTitle("Circuit Breaker");
        model.setDescription("Circuit breaker information");
        model.setReadOnly(true);

        JsonObject schema = new JsonObject();
        schema.put("type", "object");
        model.setResponseSchema(schema);

        JsonObject pathItem = DevConsoleOpenApiHelper.buildPathItem(model);
        JsonObject content = pathItem.getJsonObject("get").getJsonObject("responses").getJsonObject("200")
                .getJsonObject("content").getJsonObject("application/json");

        assertEquals(schema, content.getJsonObject("schema"));
    }

    @Test
    void consoleWithoutResponseSchemaHasEmptyResponseContent() {
        DevConsoleModel model = new DevConsoleModel();
        model.setName("context");
        model.setTitle("CamelContext");
        model.setDescription("Overall information about the CamelContext");
        model.setReadOnly(true);

        JsonObject pathItem = DevConsoleOpenApiHelper.buildPathItem(model);
        JsonObject content = pathItem.getJsonObject("get").getJsonObject("responses").getJsonObject("200")
                .getJsonObject("content").getJsonObject("application/json");

        assertTrue(content.isEmpty());
    }

    @Test
    void openApiDocumentContainsOnePathPerConsoleSortedByName() {
        DevConsoleModel context = new DevConsoleModel();
        context.setName("context");
        context.setTitle("CamelContext");
        context.setDescription("Overall information about the CamelContext");
        context.setReadOnly(true);

        DevConsoleModel route = new DevConsoleModel();
        route.setName("route");
        route.setTitle("Route");
        route.setDescription("Route information");
        route.setReadOnly(false);

        JsonObject doc = DevConsoleOpenApiHelper.buildOpenApiDocument(List.of(route, context), "4.23.0-SNAPSHOT");

        assertEquals("3.0.3", doc.getString("openapi"));
        assertEquals("4.23.0-SNAPSHOT", doc.getJsonObject("info").getString("version"));
        JsonObject paths = doc.getJsonObject("paths");
        assertNotNull(paths.getJsonObject("/q/dev/context").getJsonObject("get"));
        assertNotNull(paths.getJsonObject("/q/dev/route").getJsonObject("post"));
        assertFalse(paths.containsKey("/q/dev/missing"));
    }
}
