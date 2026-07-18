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
package org.apache.camel.component.openai;

import java.util.List;
import java.util.Map;

import com.openai.core.JsonValue;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * Converts MCP tool definitions to OpenAI chat completion tool format.
 */
final class McpToolConverter {

    private McpToolConverter() {
    }

    static List<ChatCompletionFunctionTool> convert(List<McpSchema.Tool> mcpTools) {
        return mcpTools.stream()
                .map(McpToolConverter::convertTool)
                .toList();
    }

    private static ChatCompletionFunctionTool convertTool(McpSchema.Tool tool) {
        FunctionDefinition.Builder funcBuilder = FunctionDefinition.builder()
                .name(tool.name());

        if (tool.description() != null) {
            funcBuilder.description(tool.description());
        }

        if (tool.inputSchema() != null) {
            FunctionParameters.Builder paramsBuilder = FunctionParameters.builder();
            Map<String, Object> inputSchema = tool.inputSchema();

            if (!inputSchema.containsKey("type")) {
                paramsBuilder.putAdditionalProperty("type", JsonValue.from("object"));
            }
            for (Map.Entry<String, Object> entry : inputSchema.entrySet()) {
                paramsBuilder.putAdditionalProperty(entry.getKey(), JsonValue.from(entry.getValue()));
            }

            funcBuilder.parameters(paramsBuilder.build());
        }

        return ChatCompletionFunctionTool.builder()
                .function(funcBuilder.build())
                .build();
    }
}
