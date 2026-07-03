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
package org.apache.camel.component.ai.tools;

import java.util.Collections;
import java.util.Map;

/**
 * Typed wrapper around the tool arguments sent by the LLM. Set as an exchange variable by {@link AiToolExecutor} under
 * the key {@link AiTool#TOOL_ARGUMENTS}, so route authors can retrieve it with:
 *
 * <pre>{@code
 * AiToolArguments args = exchange.getVariable(AiTool.TOOL_ARGUMENTS, AiToolArguments.class);
 * String city = args.getString("city");
 * }</pre>
 *
 * @since 4.22
 */
public final class AiToolArguments {

    private final String toolName;
    private final Map<String, Object> parameters;

    public AiToolArguments(String toolName, Map<String, Object> parameters) {
        this.toolName = toolName;
        this.parameters = parameters != null ? Collections.unmodifiableMap(parameters) : Map.of();
    }

    public String getToolName() {
        return toolName;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public Object get(String name) {
        return parameters.get(name);
    }

    public String getString(String name) {
        Object value = parameters.get(name);
        return value != null ? value.toString() : null;
    }

    /**
     * Returns the argument as an Integer, or {@code null} if absent or not convertible.
     */
    public Integer getInteger(String name) {
        Object value = parameters.get(name);
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Returns the argument as a Double, or {@code null} if absent or not convertible.
     */
    public Double getDouble(String name) {
        Object value = parameters.get(name);
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Returns the argument as a Boolean, or {@code null} if absent or not convertible.
     */
    public Boolean getBoolean(String name) {
        Object value = parameters.get(name);
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            if ("true".equalsIgnoreCase(s)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(s)) {
                return Boolean.FALSE;
            }
            return null;
        }
        return null;
    }

    public boolean has(String name) {
        return parameters.containsKey(name);
    }

    @Override
    public String toString() {
        return "AiToolArguments{toolName=" + toolName + ", parameters=" + parameters + '}';
    }
}
