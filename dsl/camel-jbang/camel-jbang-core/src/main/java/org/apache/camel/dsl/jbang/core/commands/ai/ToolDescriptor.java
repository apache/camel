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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * Describes a single AI tool: its name, description, parameters, and execution logic. Used by both the Agent REPL and
 * the MCP server to avoid duplicating tool definitions.
 */
public class ToolDescriptor {

    private final String name;
    private final String description;
    private final List<Param> params;
    private ToolExecutor executor;
    private boolean readOnly = true;
    private boolean destructive = false;
    private boolean core = false;

    public record Param(String name, String type, String description, boolean required) {
    }

    @FunctionalInterface
    public interface ToolExecutor {
        Object execute(ToolContext ctx, Map<String, String> args) throws ToolExecutionException;
    }

    private ToolDescriptor(String name, String description) {
        this.name = name;
        this.description = description;
        this.params = new ArrayList<>();
    }

    public static ToolDescriptor tool(String name, String description) {
        return new ToolDescriptor(name, description);
    }

    // Builder methods

    public ToolDescriptor param(String name, String type, String description, boolean required) {
        params.add(new Param(name, type, description, required));
        return this;
    }

    public ToolDescriptor readOnly(boolean v) {
        readOnly = v;
        return this;
    }

    public ToolDescriptor destructive(boolean v) {
        destructive = v;
        return this;
    }

    /**
     * Marks the tool as part of the core subset: the tools a small local model gets when every tool schema counts
     * against its prompt budget. Tools outside the subset are still available to hosted models and MCP clients.
     */
    public ToolDescriptor core(boolean v) {
        core = v;
        return this;
    }

    public ToolDescriptor executor(ToolExecutor exec) {
        this.executor = exec;
        return this;
    }

    // Getters

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public List<Param> params() {
        return Collections.unmodifiableList(params);
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public boolean isDestructive() {
        return destructive;
    }

    public boolean isCore() {
        return core;
    }

    /**
     * The JSON schema of the tool's arguments, the way an MCP server lists it under {@code inputSchema} and an LLM
     * client sends it as the function parameters: an object with one property per parameter (its type and description)
     * and the names of the required ones.
     */
    public JsonObject inputSchema() {
        JsonObject properties = new JsonObject();
        JsonArray required = new JsonArray();
        for (Param p : params) {
            JsonObject prop = new JsonObject();
            prop.put("type", p.type() != null ? p.type() : "string");
            prop.put("description", p.description());
            properties.put(p.name(), prop);
            if (p.required()) {
                required.add(p.name());
            }
        }
        JsonObject schema = new JsonObject();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    public ToolExecutor executor() {
        return executor;
    }
}
