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

import java.util.ArrayList;
import java.util.List;

import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Producer that executes MCP tool calls from a stored OpenAI chat completion response.
 *
 * <p>
 * This producer is designed for manual tool loop routes where {@code autoToolExecution=false} is used on the
 * chat-completion endpoint. It reads the stored {@code ChatCompletion} response, extracts tool calls, executes them via
 * MCP, and rebuilds the conversation history for the next chat-completion call.
 *
 * <p>
 * Example route:
 *
 * <pre>
 * from("direct:chat")
 *         .setProperty("originalPrompt", body())
 *         .to("openai:chat-completion?autoToolExecution=false&amp;conversationMemory=true&amp;storeFullResponse=true&amp;mcpServer...")
 *         .loopDoWhile(header("CamelOpenAIFinishReason").isEqualTo("tool_calls"))
 *         .to("openai:tool-execution?mcpServer...")
 *         .to("openai:chat-completion?autoToolExecution=false&amp;conversationMemory=true&amp;storeFullResponse=true&amp;mcpServer...")
 *         .end();
 * </pre>
 */
public class OpenAIToolExecutionProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(OpenAIToolExecutionProducer.class);

    private McpToolCallExecutor toolCallExecutor;

    public OpenAIToolExecutionProducer(OpenAIEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public OpenAIEndpoint getEndpoint() {
        return (OpenAIEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        toolCallExecutor = new McpToolCallExecutor(getEndpoint());
        ServiceHelper.startService(toolCallExecutor);
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(toolCallExecutor);
        super.doStop();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        // Get the full ChatCompletion response (stored by storeFullResponse=true on chat-completion)
        ChatCompletion response = exchange.getProperty(OpenAIConstants.RESPONSE, ChatCompletion.class);
        if (response == null) {
            throw new IllegalStateException(
                    "No ChatCompletion response found in exchange property '" + OpenAIConstants.RESPONSE
                                            + "'. Set storeFullResponse=true on the chat-completion endpoint.");
        }

        if (response.choices().isEmpty()) {
            LOG.warn("No choices in ChatCompletion response, skipping tool execution");
            return;
        }

        List<ChatCompletionMessageToolCall> toolCalls = response.choices().get(0).message().toolCalls().orElse(List.of());
        if (toolCalls.isEmpty()) {
            LOG.debug("No tool calls found in the response, skipping tool execution");
            return;
        }

        // Get or create conversation history
        OpenAIConfiguration config = getEndpoint().getConfiguration();
        String historyProperty = config.getConversationHistoryProperty();

        List<ChatCompletionMessageParam> history = exchange.getProperty(historyProperty, List.class);
        if (history == null) {
            history = new ArrayList<>();
        }

        // The chat-completion endpoint stored an assistant message with empty text
        // for the tool_calls response. Remove it — we'll add the correct one with tool calls.
        if (!history.isEmpty()) {
            history.remove(history.size() - 1);
        }

        // On the first iteration, add the original user message to the history
        if (history.isEmpty()) {
            String prompt = exchange.getProperty("originalPrompt", String.class);
            if (prompt != null) {
                history.add(ChatCompletionMessageParam.ofUser(
                        ChatCompletionUserMessageParam.builder()
                                .content(ChatCompletionUserMessageParam.Content.ofText(prompt))
                                .build()));
            }
        }

        // Add the assistant message with tool calls (the correct representation)
        history.add(ChatCompletionMessageParam.ofAssistant(
                ChatCompletionAssistantMessageParam.builder()
                        .toolCalls(toolCalls)
                        .build()));

        // Execute each tool call via MCP or route tools and add tool result messages
        McpToolState toolState = getEndpoint().getMcpToolState();
        if (toolState.toolClientMap().isEmpty() && toolState.routeTools().isEmpty()) {
            throw new IllegalStateException(
                    "No tools configured on the endpoint. Configure mcpServer.* parameters and/or the tags option.");
        }

        List<McpToolCallExecutor.ToolResult> results = toolCallExecutor.execute(toolCalls);
        for (McpToolCallExecutor.ToolResult result : results) {
            history.add(ChatCompletionMessageParam.ofTool(
                    ChatCompletionToolMessageParam.builder()
                            .toolCallId(result.toolCallId())
                            .content(result.content())
                            .build()));
        }

        // Update conversation history and clear body for the next chat-completion call
        exchange.setProperty(historyProperty, OpenAIConversationHistoryTrimmer.trim(history, config));
        exchange.getMessage().setBody(null);
        exchange.getMessage().setHeader(OpenAIConstants.TOOL_ITERATIONS, results.size());
    }
}
