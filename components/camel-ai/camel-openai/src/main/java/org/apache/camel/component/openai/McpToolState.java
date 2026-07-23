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
import java.util.Set;

import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import io.modelcontextprotocol.client.McpSyncClient;

/**
 * Immutable snapshot of the MCP tool state shared by all concurrent exchanges.
 */
record McpToolState(
        List<ChatCompletionFunctionTool> tools,
        Map<String, McpSyncClient> toolClientMap,
        Map<String, String> toolToServerName,
        Set<String> returnDirectTools) {

    McpToolState {
        tools = List.copyOf(tools);
        toolClientMap = Map.copyOf(toolClientMap);
        toolToServerName = Map.copyOf(toolToServerName);
        returnDirectTools = Set.copyOf(returnDirectTools);
    }

    static McpToolState empty() {
        return new McpToolState(List.of(), Map.of(), Map.of(), Set.of());
    }
}
