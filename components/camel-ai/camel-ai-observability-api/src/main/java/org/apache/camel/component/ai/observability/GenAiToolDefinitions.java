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
package org.apache.camel.component.ai.observability;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * The tools offered to the model in a request, in the shape of the OpenTelemetry GenAI semantic conventions, for the
 * {@code gen_ai.tool.definitions} span attribute. Rendered only at level {@link GenAiObservabilityLevel#FULL}.
 *
 * @since 4.24
 */
public final class GenAiToolDefinitions {

    private final List<JsonObject> tools = new ArrayList<>();

    /**
     * Adds a function tool.
     *
     * @param name        the tool name
     * @param description the description, may be {@code null}
     * @param parameters  the JSON schema of the parameters, as a map or a JSON string; may be {@code null}
     */
    public GenAiToolDefinitions function(String name, String description, Object parameters) {
        JsonObject tool = new JsonObject();
        tool.put("type", "function");
        tool.put("name", name);
        if (description != null) {
            tool.put("description", description);
        }
        Object schema = parameters instanceof String json ? parse(json) : parameters;
        if (schema instanceof Map<?, ?> map) {
            tool.put("parameters", new JsonObject(castMap(map)));
        } else if (schema != null) {
            tool.put("parameters", schema);
        }
        tools.add(tool);
        return this;
    }

    public boolean isEmpty() {
        return tools.isEmpty();
    }

    /**
     * Renders the tools as a JSON array, cutting every description longer than {@code maxLength} characters. The
     * parameter schemas are kept whole, as they are structure rather than content.
     */
    public String toJson(int maxLength) {
        JsonArray array = new JsonArray();
        for (JsonObject tool : tools) {
            JsonObject copy = new JsonObject(tool);
            if (copy.get("description") instanceof String description) {
                copy.put("description", GenAiMessages.truncate(description, maxLength));
            }
            array.add(copy);
        }
        return Jsoner.serialize(array);
    }

    private static Object parse(String json) {
        try {
            return Jsoner.deserialize(json);
        } catch (Exception e) {
            return json;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }
}
