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
package org.apache.camel.component.mcp.server;

import java.util.Map;

import org.apache.camel.component.ai.tool.AiToolAnnotations;
import org.apache.camel.component.ai.tool.AiToolParameterHelper.ParameterDef;

/**
 * A tool published by the bridge into an {@link McpServerEngine}. Engines pick whichever input-schema representation
 * fits their API: the pre-built JSON Schema string or the structured parameter definitions.
 *
 * @since 4.22
 */
public interface McpServerTool {

    /**
     * The tool name, unique within the MCP server (flat namespace).
     */
    String name();

    /**
     * Human-readable tool description.
     */
    String description();

    /**
     * The tool input as a JSON Schema object string, or null when the tool declares no parameters.
     */
    String inputSchemaJson();

    /**
     * The tool input as structured parameter definitions; empty when the tool declares no parameters.
     */
    Map<String, ParameterDef> parameters();

    /**
     * The handler executing the tool. Blocking, timeout-bounded and pre-sanitized by the bridge.
     */
    McpToolCallHandler handler();

    /**
     * Optional MCP tool annotation hints, or {@code null} when none are configured.
     */
    default AiToolAnnotations annotations() {
        return null;
    }

    /**
     * The tool output as a JSON Schema object string, or {@code null} when the tool declares no structured output.
     *
     * @since 4.22
     */
    default String outputSchemaJson() {
        return null;
    }
}
