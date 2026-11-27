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
    @Metadata(description = "The system message that the agent sends to the model with the user message. Not used when"
                            + " the body is an AiAgentBody, which holds its own system message.",
              javaType = "String")
    public static final String SYSTEM_MESSAGE = "CamelLangChain4jAgentSystemMessage";

    @Metadata(description = "The id of the conversation in the chat memory, required by an agent with a ChatMemoryProvider"
                            + " and not used by an agent without one. Not used when the body is an AiAgentBody, which"
                            + " holds its own memory id.",
              javaType = "Object")
    public static final String MEMORY_ID = "CamelLangChain4jAgentMemoryId";

    @Metadata(description = "The text of the user message that goes with a file, bytes or a stream in the body. With a text"
                            + " body, this header replaces the text of the body. Not used when the body is an AiAgentBody.",
              javaType = "String")
    public static final String USER_MESSAGE = "CamelLangChain4jAgentUserMessage";

    @Metadata(description = "The media type (MIME type) of a file, bytes or a stream in the body, for example image/png. It"
                            + " takes precedence over the content type headers and over the file name extension.",
              javaType = "String")
    public static final String MEDIA_TYPE = "CamelLangChain4jAgentMediaType";

    @Metadata(description = "A comma-separated list of tags to remove from the tags option for this message. A route tool"
                            + " is still offered when another of its tags remains.",
              javaType = "String")
    public static final String EXCLUDE_TAGS = "CamelLangChain4jAgentExcludeTags";

    @Metadata(description = "A comma-separated list of MCP server names whose tools the model is not offered for this"
                            + " message. The name is the key of the McpClient, or myServer of the mcpServer option.",
              javaType = "String")
    public static final String EXCLUDE_MCP_SERVERS = "CamelLangChain4jAgentExcludeMcpServers";

    @Metadata(description = "The reason why the model stopped generating the answer: STOP, LENGTH, TOOL_EXECUTION,"
                            + " CONTENT_FILTER, or OTHER.",
              javaType = "dev.langchain4j.model.output.FinishReason")
    public static final String FINISH_REASON = "CamelLangChain4jAgentFinishReason";

    @Metadata(description = "The number of input tokens, summed over all model calls for the message.", javaType = "int")
    public static final String INPUT_TOKEN_COUNT = "CamelLangChain4jAgentInputTokenCount";

    @Metadata(description = "The number of output tokens, summed over all model calls for the message.", javaType = "int")
    public static final String OUTPUT_TOKEN_COUNT = "CamelLangChain4jAgentOutputTokenCount";

    @Metadata(description = "The number of input and output tokens, summed over all model calls for the message.",
              javaType = "int")
    public static final String TOTAL_TOKEN_COUNT = "CamelLangChain4jAgentTotalTokenCount";

    @Metadata(description = "The name of the model that the request went to, read from the chat model of the agent, or"
                            + " unknown when Camel cannot read it.",
              javaType = "String")
    public static final String REQUEST_MODEL = "CamelLangChain4jAgentRequestModel";

    @Metadata(description = "The name of the model that answered. The producer does not set it, as the Result of"
                            + " LangChain4j does not hold it.",
              javaType = "String")
    public static final String RESPONSE_MODEL = "CamelLangChain4jAgentResponseModel";

    @Metadata(description = "The contents that the retrieval augmentor (RAG) of the agent retrieved for the message, if"
                            + " any.",
              javaType = "java.util.List<dev.langchain4j.rag.content.Content>")
    public static final String SOURCES = "CamelLangChain4jAgentSources";

    @Metadata(description = "The tool executions, each with its request and result, that the agent ran for the message, if"
                            + " any.",
              javaType = "java.util.List<dev.langchain4j.service.tool.ToolExecution>")
    public static final String TOOL_EXECUTIONS = "CamelLangChain4jAgentToolExecutions";

    @Metadata(description = "Set to true when the moderation model of the agent flags the user message, and the exchange"
                            + " fails with a ModerationException. Not set otherwise.",
              javaType = "Boolean")
    public static final String MODERATION_FLAGGED = "CamelLangChain4jAgentModerationFlagged";
}
