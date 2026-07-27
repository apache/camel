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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.MainModel;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * Helper for fetching and formatting Spring Boot configuration metadata from a running integration via the CLI
 * connector IPC mechanism.
 */
final class SpringBootMetadataHelper {

    private SpringBootMetadataHelper() {
    }

    static Map<String, JsonObject> fetchMetadata(MonitorContext ctx, String pid) {
        JsonObject root = new JsonObject();
        root.put("action", "spring-boot-configuration");

        JsonObject response = ctx.executeAction(pid, root, 5000);
        if (response == null) {
            return Map.of();
        }

        Object propsObj = response.get("properties");
        if (!(propsObj instanceof JsonArray arr)) {
            return Map.of();
        }

        Map<String, JsonObject> result = new HashMap<>();
        for (int i = 0; i < arr.size(); i++) {
            Object item = arr.get(i);
            if (item instanceof JsonObject prop) {
                String name = prop.getString("name");
                if (name != null && !name.isEmpty()) {
                    result.put(name, prop);
                }
            }
        }
        return result;
    }

    static String formatDoc(JsonObject prop) {
        StringBuilder sb = new StringBuilder();
        String description = prop.getString("description");
        if (description != null && !description.isEmpty()) {
            sb.append(RoutesTab.truncateText(description, 80));
        }

        List<String> meta = new ArrayList<>();
        String type = prop.getString("type");
        if (type != null) {
            int lastDot = type.lastIndexOf('.');
            if (lastDot >= 0) {
                type = type.substring(lastDot + 1);
            }
            meta.add(type);
        }
        Object defaultValue = prop.get("defaultValue");
        if (defaultValue != null) {
            meta.add("default: " + defaultValue);
        }
        if (!meta.isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append(" ");
            }
            sb.append("(").append(String.join(", ", meta)).append(")");
        }

        if (Boolean.TRUE.equals(prop.get("deprecated"))) {
            sb.append(" [deprecated]");
        }

        return !sb.isEmpty() ? sb.toString() : null;
    }

    static BaseOptionModel toOptionModel(JsonObject prop) {
        MainModel.MainOptionModel model = new MainModel.MainOptionModel();
        model.setName(prop.getString("name"));
        model.setDescription(prop.getString("description"));

        String type = prop.getString("type");
        if (type != null) {
            int lastDot = type.lastIndexOf('.');
            if (lastDot >= 0) {
                type = type.substring(lastDot + 1);
            }
            model.setType(type);
        }

        Object defaultValue = prop.get("defaultValue");
        if (defaultValue != null) {
            model.setDefaultValue(defaultValue.toString());
        }

        if (Boolean.TRUE.equals(prop.get("deprecated"))) {
            model.setDeprecated(true);
        }

        return model;
    }
}
