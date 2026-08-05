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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import org.apache.camel.dsl.jbang.core.commands.ai.ToolDescriptor;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * Builds MCP JSON schemas from {@link ToolDescriptor} definitions.
 */
final class ToolMcpSchemas {

    private ToolMcpSchemas() {
    }

    static String inputSchemaJson(ToolDescriptor descriptor) {
        JsonObject schema = new JsonObject();
        schema.put("type", "object");
        JsonObject properties = new JsonObject();
        JsonArray required = new JsonArray();
        for (ToolDescriptor.Param param : descriptor.params()) {
            JsonObject prop = new JsonObject();
            prop.put("type", param.type());
            prop.put("description", param.description());
            properties.put(param.name(), prop);
            if (param.required()) {
                required.add(param.name());
            }
        }
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema.toJson();
    }
}
