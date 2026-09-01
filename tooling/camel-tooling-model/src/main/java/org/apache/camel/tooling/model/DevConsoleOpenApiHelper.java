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
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * Builds an OpenAPI 3.0 specification describing the dev console {@code /q/dev/{id}} endpoints, from
 * {@link DevConsoleModel} instances. Shared between the live {@code api} dev console (which sources models from a
 * running {@code DevConsoleRegistry}) and the build-time catalog generator (which sources models from the aggregated
 * catalog {@code dev-console/*.json} files), so both produce identical output.
 */
public final class DevConsoleOpenApiHelper {

    private DevConsoleOpenApiHelper() {
    }

    /**
     * Builds a full OpenAPI document for the given consoles.
     */
    public static JsonObject buildOpenApiDocument(List<DevConsoleModel> consoles, String version) {
        JsonObject root = new JsonObject();
        root.put("openapi", "3.0.3");

        JsonObject info = new JsonObject();
        info.put("title", "Camel Dev Console API");
        info.put("version", version);
        root.put("info", info);

        JsonObject paths = new JsonObject();
        consoles.stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .forEach(model -> paths.put("/q/dev/" + model.getName(), buildPathItem(model)));
        root.put("paths", paths);

        return root;
    }

    /**
     * Builds the OpenAPI path item ({@code get} or {@code post} operation) for a single console.
     */
    public static JsonObject buildPathItem(DevConsoleModel model) {
        JsonObject operation = new JsonObject();
        operation.put("summary", model.getTitle());
        operation.put("description", model.getDescription());
        operation.put("operationId", model.getName());

        boolean readOnly = model.isReadOnly();
        if (readOnly) {
            JsonArray parameters = buildParameters(model.getOptions());
            if (parameters != null) {
                operation.put("parameters", parameters);
            }
        } else {
            JsonObject requestBody = buildRequestBody(model.getOptions());
            if (requestBody != null) {
                operation.put("requestBody", requestBody);
            }
        }

        JsonObject responses = new JsonObject();
        JsonObject ok = new JsonObject();
        ok.put("description", model.getTitle() + " output");
        JsonObject mediaType = new JsonObject();
        if (model.getResponseSchema() != null) {
            mediaType.put("schema", model.getResponseSchema());
        }
        JsonObject responseContent = new JsonObject();
        responseContent.put("application/json", mediaType);
        ok.put("content", responseContent);
        responses.put("200", ok);
        operation.put("responses", responses);

        JsonObject pathItem = new JsonObject();
        pathItem.put(readOnly ? "get" : "post", operation);
        return pathItem;
    }

    private static JsonArray buildParameters(List<DevConsoleOptionModel> options) {
        if (options == null || options.isEmpty()) {
            return null;
        }

        JsonArray parameters = new JsonArray();
        for (DevConsoleOptionModel opt : options) {
            JsonObject param = new JsonObject();
            param.put("name", opt.getName());
            param.put("in", "query");
            if (opt.getDescription() != null) {
                param.put("description", opt.getDescription());
            }
            param.put("required", opt.isRequired());
            param.put("schema", buildSchema(opt));
            parameters.add(param);
        }
        return parameters;
    }

    private static JsonObject buildRequestBody(List<DevConsoleOptionModel> options) {
        if (options == null || options.isEmpty()) {
            return null;
        }

        JsonObject properties = new JsonObject();
        JsonArray required = new JsonArray();
        for (DevConsoleOptionModel opt : options) {
            JsonObject prop = buildSchema(opt);
            if (opt.getDescription() != null) {
                prop.put("description", opt.getDescription());
            }
            properties.put(opt.getName(), prop);
            if (opt.isRequired()) {
                required.add(opt.getName());
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
    }

    private static JsonObject buildSchema(DevConsoleOptionModel opt) {
        JsonObject schema = new JsonObject();
        if (opt.getType() != null) {
            schema.put("type", opt.getType());
        }
        if (opt.getDefaultValue() != null) {
            schema.put("default", opt.resolveDefaultValue());
        }
        if (opt.getEnums() != null && !opt.getEnums().isEmpty()) {
            schema.put("enum", new JsonArray(opt.getEnums()));
        }
        return schema;
    }
}
