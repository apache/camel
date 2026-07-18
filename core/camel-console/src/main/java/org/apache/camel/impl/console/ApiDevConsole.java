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
package org.apache.camel.impl.console;

import java.util.List;
import java.util.Map;

import org.apache.camel.CatalogCamelContext;
import org.apache.camel.console.DevConsoleRegistry;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

@DevConsole(name = "api", displayName = "API", description = "OpenAPI specification for the dev console API")
@Configurer(extended = true)
public class ApiDevConsole extends AbstractDevConsole {

    private volatile String cachedOpenApi;

    public ApiDevConsole() {
        super("camel", "api", "API", "OpenAPI specification for the dev console API");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        return doCallJson(options).toJson();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        if (cachedOpenApi == null) {
            cachedOpenApi = buildOpenApi();
        }
        // parse the cached string back into a JsonObject so it integrates
        // with the dev console JSON response structure
        try {
            return (JsonObject) Jsoner.deserialize(cachedOpenApi);
        } catch (Exception e) {
            JsonObject error = new JsonObject();
            error.put("error", e.getMessage());
            return error;
        }
    }

    private String buildOpenApi() {
        JsonObject root = new JsonObject();
        root.put("openapi", "3.0.3");

        JsonObject info = new JsonObject();
        info.put("title", "Camel Dev Console API");
        info.put("version", getCamelContext().getVersion());
        root.put("info", info);

        JsonObject paths = new JsonObject();

        DevConsoleRegistry dcr = getCamelContext().getCamelContextExtension()
                .getContextPlugin(DevConsoleRegistry.class);
        if (dcr != null && dcr.isEnabled()) {
            List<org.apache.camel.console.DevConsole> consoles = dcr.stream()
                    .sorted((a, b) -> a.getId().compareToIgnoreCase(b.getId()))
                    .toList();

            for (org.apache.camel.console.DevConsole console : consoles) {
                String id = console.getId();
                JsonObject pathItem = new JsonObject();
                JsonObject post = new JsonObject();
                post.put("summary", console.getDisplayName());
                post.put("description", console.getDescription());
                post.put("operationId", id);

                JsonObject requestBody = buildConsoleRequestBody(id);
                if (requestBody != null) {
                    post.put("requestBody", requestBody);
                }

                JsonObject responses = new JsonObject();
                JsonObject ok = new JsonObject();
                ok.put("description", console.getDisplayName() + " output");
                JsonObject responseContent = new JsonObject();
                responseContent.put("application/json", new JsonObject());
                ok.put("content", responseContent);
                responses.put("200", ok);
                post.put("responses", responses);

                pathItem.put("post", post);
                paths.put("/q/dev/" + id, pathItem);
            }
        }

        root.put("paths", paths);
        return Jsoner.prettyPrint(root.toJson());
    }

    private JsonObject buildConsoleRequestBody(String consoleId) {
        try {
            String json = ((CatalogCamelContext) getCamelContext())
                    .getDevConsoleParameterJsonSchema(consoleId);
            if (json == null) {
                return null;
            }
            Object parsed = Jsoner.deserialize(json);
            if (!(parsed instanceof JsonObject jo)) {
                return null;
            }
            Object optionsObj = jo.get("options");
            if (!(optionsObj instanceof JsonObject opts) || opts.isEmpty()) {
                return null;
            }

            JsonObject properties = new JsonObject();
            JsonArray required = new JsonArray();
            for (Map.Entry<String, Object> entry : opts.entrySet()) {
                String name = entry.getKey();
                if (!(entry.getValue() instanceof JsonObject opt)) {
                    continue;
                }
                JsonObject prop = new JsonObject();
                String type = opt.getString("type");
                if (type != null) {
                    prop.put("type", type);
                }
                String description = opt.getString("description");
                if (description != null) {
                    prop.put("description", description);
                }
                Object defaultValue = opt.get("defaultValue");
                if (defaultValue != null) {
                    prop.put("default", defaultValue);
                }
                Object enumValues = opt.get("enum");
                if (enumValues instanceof JsonArray ea && !ea.isEmpty()) {
                    prop.put("enum", enumValues);
                }
                properties.put(name, prop);

                Boolean req = opt.getBoolean("required");
                if (req != null && req) {
                    required.add(name);
                }
            }

            JsonObject schema = new JsonObject();
            schema.put("type", "object");
            schema.put("properties", properties);
            if (!required.isEmpty()) {
                schema.put("required", required);
            }

            JsonObject mediaType = new JsonObject();
            mediaType.put("schema", schema);
            JsonObject content = new JsonObject();
            content.put("application/json", mediaType);
            JsonObject requestBody = new JsonObject();
            requestBody.put("content", content);
            return requestBody;
        } catch (Exception e) {
            // ignore
            return null;
        }
    }
}
