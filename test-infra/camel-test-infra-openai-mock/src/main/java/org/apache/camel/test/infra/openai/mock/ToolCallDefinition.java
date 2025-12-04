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

package org.apache.camel.test.infra.openai.mock;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a tool call with its name and parameters.
 */
public class ToolCallDefinition {
    private final String name;
    private final Map<String, Object> arguments;

    public ToolCallDefinition(String name) {
        this.name = Objects.requireNonNull(name, "Tool name cannot be null");
        this.arguments = new HashMap<>();
    }

    public String getName() {
        return name;
    }

    public Map<String, Object> getArguments() {
        return new HashMap<>(arguments); // Return defensive copy
    }

    public void addArgument(String key, Object value) {
        arguments.put(key, value);
    }

    @Override
    public String toString() {
        return String.format("ToolCall{name='%s', arguments=%s}", name, arguments);
    }
}
