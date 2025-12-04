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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a single step in a tool execution sequence. A step can contain multiple tools that should be executed in
 * parallel.
 */
public class ToolExecutionStep {
    private final List<ToolCallDefinition> toolCalls;

    public ToolExecutionStep() {
        this.toolCalls = new ArrayList<>();
    }

    public void addToolCall(ToolCallDefinition toolCall) {
        this.toolCalls.add(Objects.requireNonNull(toolCall, "Tool call cannot be null"));
    }

    public List<ToolCallDefinition> getToolCalls() {
        return new ArrayList<>(toolCalls); // Return defensive copy
    }

    public boolean isEmpty() {
        return toolCalls.isEmpty();
    }

    public int size() {
        return toolCalls.size();
    }

    public ToolCallDefinition getLastToolCall() {
        if (toolCalls.isEmpty()) {
            throw new IllegalStateException("No tool calls in this step");
        }
        return toolCalls.get(toolCalls.size() - 1);
    }

    @Override
    public String toString() {
        return String.format("ToolExecutionStep{toolCalls=%s}", toolCalls);
    }
}
