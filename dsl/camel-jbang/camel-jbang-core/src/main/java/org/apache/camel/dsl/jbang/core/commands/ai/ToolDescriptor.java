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
    private boolean deterministic = false;
    private String deterministicWhen;
    private String[] deterministicUnless = new String[0];
    private String repeatHint;

    /**
     * A parameter of the tool; {@code items} is the schema of the elements of an array parameter (else null).
     */
    public record Param(String name, String type, String description, boolean required, JsonObject items) {

        public Param(String name, String type, String description, boolean required) {
            this(name, type, description, required, null);
        }
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

    /**
     * An array parameter whose elements are objects with the given string properties (name and description), all
     * required. A client sends it as a JSON array; the executor gets it as JSON text.
     */
    public ToolDescriptor arrayParam(String name, String description, boolean required, String... properties) {
        JsonObject props = new JsonObject();
        JsonArray names = new JsonArray();
        for (int i = 0; i + 1 < properties.length; i += 2) {
            JsonObject prop = new JsonObject();
            prop.put("type", "string");
            prop.put("description", properties[i + 1]);
            props.put(properties[i], prop);
            names.add(properties[i]);
        }
        JsonObject items = new JsonObject();
        items.put("type", "object");
        items.put("properties", props);
        items.put("required", names);
        params.add(new Param(name, "array", description, required, items));
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

    /**
     * Marks the tool as deterministic: the same arguments always give the same answer, as a catalog lookup does. A tool
     * that reads the running integration, the files or the clock is not. Repeating such a call cannot tell an agent
     * anything new, see {@link RepeatedToolCalls}.
     */
    public ToolDescriptor deterministic(boolean v) {
        deterministic = v;
        return this;
    }

    /**
     * Marks the tool as deterministic when the given argument is passed and none of the unless arguments is: validating
     * the given content always gives the same answer, while validating the file on disk, or content checked against the
     * other files of a directory, does not (CAMEL-25371).
     */
    public ToolDescriptor deterministicWhen(String param, String... unless) {
        deterministicWhen = param;
        deterministicUnless = unless != null ? unless : new String[0];
        return this;
    }

    /** What the note of a repeated call adds for this tool: what to do instead of asking again. */
    public ToolDescriptor repeatHint(String hint) {
        repeatHint = hint;
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

    public boolean isDeterministic() {
        return deterministic;
    }

    /**
     * Whether this call gives the same answer each time: the tool is deterministic, or the argument that makes it so is
     * passed without the ones that make it read other files.
     */
    public boolean isDeterministic(Map<String, ?> args) {
        if (deterministic) {
            return true;
        }
        if (deterministicWhen == null || !hasValue(args, deterministicWhen)) {
            return false;
        }
        for (String unless : deterministicUnless) {
            if (hasValue(args, unless)) {
                return false;
            }
        }
        return true;
    }

    /** The argument that makes a call deterministic, or null when the tool is always or never deterministic. */
    public String deterministicWhen() {
        return deterministicWhen;
    }

    private static boolean hasValue(Map<String, ?> args, String name) {
        Object v = args != null ? args.get(name) : null;
        return v != null && !v.toString().isBlank();
    }

    public String repeatHint() {
        return repeatHint;
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
            if (p.items() != null) {
                prop.put("items", p.items());
            }
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
