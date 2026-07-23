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

package org.apache.camel.component.langchain4j.agent.api;

import org.apache.camel.spi.Metadata;

/**
 * Headers that the agent component may rely on
 */
public class Headers {
    @Metadata(description = "The system prompt.", javaType = "String")
    public static final String SYSTEM_MESSAGE = "CamelLangChain4jAgentSystemMessage";

    @Metadata(description = "Memory ID.", javaType = "Object")
    public static final String MEMORY_ID = "CamelLangChain4jAgentMemoryId";

    @Metadata(description = "The user message to accompany file content when using WrappedFile as input.", javaType = "String")
    public static final String USER_MESSAGE = "CamelLangChain4jAgentUserMessage";

    @Metadata(description = "The media type (MIME type) of the file content. Overrides auto-detection from file extension.",
              javaType = "String")
    public static final String MEDIA_TYPE = "CamelLangChain4jAgentMediaType";

    @Metadata(description = "Comma-separated list of Camel tool tags to exclude from this agent invocation.",
              javaType = "String")
    public static final String EXCLUDE_TAGS = "CamelLangChain4jAgentExcludeTags";

    @Metadata(description = "Comma-separated list of MCP server names (keys) to exclude from this agent invocation.",
              javaType = "String")
    public static final String EXCLUDE_MCP_SERVERS = "CamelLangChain4jAgentExcludeMcpServers";

    @Metadata(description = "The Finish Reason.", javaType = "dev.langchain4j.model.output.FinishReason")
    public static final String FINISH_REASON = "CamelLangChain4jAgentFinishReason";

    @Metadata(description = "The Input Token Count.", javaType = "int")
    public static final String INPUT_TOKEN_COUNT = "CamelLangChain4jAgentInputTokenCount";

    @Metadata(description = "The Output Token Count.", javaType = "int")
    public static final String OUTPUT_TOKEN_COUNT = "CamelLangChain4jAgentOutputTokenCount";

    @Metadata(description = "The Total Token Count.", javaType = "int")
    public static final String TOTAL_TOKEN_COUNT = "CamelLangChain4jAgentTotalTokenCount";

    @Metadata(description = "RAG sources retrieved during agent invocation.",
              javaType = "java.util.List<dev.langchain4j.rag.content.Content>")
    public static final String SOURCES = "CamelLangChain4jAgentSources";

    @Metadata(description = "Tool executions performed during agent invocation.",
              javaType = "java.util.List<dev.langchain4j.service.tool.ToolExecution>")
    public static final String TOOL_EXECUTIONS = "CamelLangChain4jAgentToolExecutions";
}
