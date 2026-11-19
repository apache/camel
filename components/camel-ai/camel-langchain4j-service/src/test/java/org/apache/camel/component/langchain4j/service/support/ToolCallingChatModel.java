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
package org.apache.camel.component.langchain4j.service.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * A {@link ChatModel} that asks for one tool call on its first turn and answers normally on the next.
 * <p>
 * Whether a real model elects to call a tool from a natural-language prompt is the model's business and belongs in the
 * integration test. What the component and LangChain4j must do once the model does ask - run the tool, feed its result
 * back, return the final answer - is deterministic, and this makes it assertable in CI.
 */
public class ToolCallingChatModel implements ChatModel {

    private final List<ChatRequest> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger round = new AtomicInteger();
    private final String toolName;
    private final String arguments;
    private final String finalReply;

    public ToolCallingChatModel(String toolName, String arguments, String finalReply) {
        this.toolName = toolName;
        this.arguments = arguments;
        this.finalReply = finalReply;
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        requests.add(request);
        if (round.getAndIncrement() == 0) {
            ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                    .id("t1")
                    .name(toolName)
                    .arguments(arguments)
                    .build();
            return ChatResponse.builder()
                    .aiMessage(AiMessage.builder().toolExecutionRequests(List.of(toolRequest)).build())
                    .build();
        }
        return ChatResponse.builder().aiMessage(AiMessage.from(finalReply)).build();
    }

    /**
     * @return every request the AI Service sent
     */
    public List<ChatRequest> requests() {
        return requests;
    }

    /**
     * @return how many turns the AI Service took; a completed tool round-trip is two
     */
    public int rounds() {
        return round.get();
    }

    /**
     * @return the tool's return value as LangChain4j fed it back to the model, or null when no tool ran
     */
    public String toolResult() {
        for (ChatRequest request : requests) {
            for (ChatMessage message : request.messages()) {
                if (message instanceof ToolExecutionResultMessage result) {
                    return result.text();
                }
            }
        }
        return null;
    }

    /**
     * @return the tool names the AI Service advertised on its first turn, proving {@code .tools(...)} reached the
     *         request
     */
    public List<String> advertisedToolNames() {
        if (requests.isEmpty() || requests.get(0).toolSpecifications() == null) {
            return List.of();
        }
        return requests.get(0).toolSpecifications().stream().map(spec -> spec.name()).toList();
    }
}
