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
package org.apache.camel.component.langchain4j.agent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.observability.api.event.AiServiceRequestIssuedEvent;
import dev.langchain4j.observability.api.event.AiServiceResponseReceivedEvent;
import dev.langchain4j.observability.api.listener.AiServiceListener;
import dev.langchain4j.observability.api.listener.AiServiceRequestIssuedListener;
import dev.langchain4j.observability.api.listener.AiServiceResponseReceivedListener;
import org.apache.camel.Exchange;
import org.apache.camel.component.ai.observability.GenAiMessages;
import org.apache.camel.component.ai.observability.GenAiObservability;
import org.apache.camel.component.ai.observability.GenAiObservation;
import org.apache.camel.component.ai.observability.GenAiObservationContext;
import org.apache.camel.component.ai.observability.GenAiOperationName;
import org.apache.camel.component.ai.observability.GenAiUsage;

/**
 * Observes each model call of an agent run as its own {@code chat} generation, nested under the run's
 * {@code invoke_agent} span, from the LangChain4j AI service events.
 */
final class GenAiAgentRunObserver {

    private static final String COMPONENT = "langchain4j-agent";

    private final Exchange exchange;
    private final GenAiObservation agentRun;
    private final String system;
    private final String model;
    private final Object conversationId;
    private final Deque<GenAiObservation> openCalls = new ArrayDeque<>();
    private int observedCalls;

    GenAiAgentRunObserver(Exchange exchange, GenAiObservation agentRun, String system, String model,
                          Object conversationId) {
        this.exchange = exchange;
        this.agentRun = agentRun;
        this.system = system;
        this.model = model;
        this.conversationId = conversationId;
    }

    List<AiServiceListener<?>> listeners() {
        AiServiceRequestIssuedListener onRequest = this::onRequest;
        AiServiceResponseReceivedListener onResponse = this::onResponse;
        return List.of(onRequest, onResponse);
    }

    /**
     * The number of model calls seen. Zero means the agent does not build its AI service through {@code AbstractAgent}
     * (a custom {@code Agent}), so its calls could not be observed one by one.
     */
    synchronized int observedCalls() {
        return observedCalls;
    }

    /**
     * Closes the calls still open, when the run failed before their response.
     */
    synchronized void finish(Throwable error) {
        while (!openCalls.isEmpty()) {
            GenAiObservation call = openCalls.pop();
            if (error != null) {
                call.recordError(error);
            }
            call.close();
        }
    }

    private synchronized void onRequest(AiServiceRequestIssuedEvent event) {
        ChatRequest request = event.request();
        ChatRequestParameters parameters = request.parameters();
        String requestModel = parameters != null && parameters.modelName() != null ? parameters.modelName() : model;
        GenAiObservationContext.Builder context = GenAiObservationContext.builder()
                .operationName(GenAiOperationName.CHAT)
                .system(system)
                .requestModel(requestModel)
                .componentScheme(COMPONENT)
                .conversationId(conversationId)
                .parent(agentRun);
        if (parameters != null) {
            context.requestParameter("temperature", parameters.temperature())
                    .requestParameter("top_p", parameters.topP())
                    .requestParameter("max_tokens", parameters.maxOutputTokens());
        }
        GenAiObservation call = GenAiObservability.start(exchange, context.build());
        call.recordInputMessages(() -> toMessages(request.messages()));
        openCalls.push(call);
        observedCalls++;
    }

    private synchronized void onResponse(AiServiceResponseReceivedEvent event) {
        if (openCalls.isEmpty()) {
            return;
        }
        GenAiObservation call = openCalls.pop();
        ChatResponse response = event.response();
        TokenUsage tokenUsage = response.tokenUsage();
        String finishReason = finishReason(response.finishReason());
        call.recordSuccess(GenAiUsage.of(
                tokenUsage != null ? tokenUsage.inputTokenCount() : null,
                tokenUsage != null ? tokenUsage.outputTokenCount() : null,
                finishReason,
                response.modelName()));
        call.recordOutputMessages(() -> toOutput(response.aiMessage(), finishReason));
        call.close();
    }

    static GenAiMessages toMessages(List<ChatMessage> messages) {
        GenAiMessages result = new GenAiMessages();
        for (ChatMessage message : messages) {
            switch (message) {
                case SystemMessage system -> result.text(GenAiMessages.SYSTEM, system.text());
                case UserMessage user -> result.text(GenAiMessages.USER, text(user));
                case AiMessage ai -> addAssistant(result, ai);
                case ToolExecutionResultMessage toolResult -> result.toolResult(toolResult.id(), toolResult.text());
                default -> result.text(message.type().name().toLowerCase(Locale.ROOT), message.toString());
            }
        }
        return result;
    }

    static GenAiMessages toOutput(AiMessage aiMessage, String finishReason) {
        GenAiMessages result = new GenAiMessages();
        if (aiMessage != null) {
            addAssistant(result, aiMessage);
        }
        return result.finishReason(finishReason);
    }

    private static void addAssistant(GenAiMessages messages, AiMessage ai) {
        messages.text(GenAiMessages.ASSISTANT, ai.text());
        messages.reasoning(ai.thinking());
        if (ai.hasToolExecutionRequests()) {
            for (ToolExecutionRequest request : ai.toolExecutionRequests()) {
                messages.toolCall(request.id(), request.name(), request.arguments());
            }
        }
    }

    private static String text(UserMessage user) {
        if (user.hasSingleText()) {
            return user.singleText();
        }
        return user.contents().stream()
                .map(content -> content instanceof TextContent text ? text.text() : "[" + content.type() + "]")
                .collect(Collectors.joining("\n"));
    }

    /**
     * Maps LangChain4j finish reasons to the values of the GenAI semantic conventions.
     */
    static String finishReason(FinishReason finishReason) {
        if (finishReason == null) {
            return null;
        }
        return switch (finishReason) {
            case TOOL_EXECUTION -> "tool_call";
            default -> finishReason.name().toLowerCase(Locale.ROOT);
        };
    }
}
