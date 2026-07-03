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

    public ToolExecutor executor() {
        return executor;
    }
}
