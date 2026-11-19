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
import java.util.function.Function;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * A {@link ChatModel} that calls no LLM: it records every request LangChain4j builds and answers with a canned reply.
 * <p>
 * This is what makes the AI Service contract testable in CI. {@link ChatRequest#messages()} holds the prompt
 * <em>after</em> LangChain4j rendered it, so a test can prove that {@code {{it}}} and {@code {{variable}}} were
 * substituted from what the component bound, and that a method-level {@code @SystemMessage} was emitted at all. A
 * hand-rolled proxy standing in for the AI Service can show none of that.
 * <p>
 * Every method of {@link ChatModel} is a default method, so overriding {@code doChat} is all a stub needs.
 */
public class RecordingChatModel implements ChatModel {

    private final List<ChatRequest> requests = new CopyOnWriteArrayList<>();
    private final Function<ChatRequest, String> reply;

    public RecordingChatModel() {
        this("ok");
    }

    public RecordingChatModel(String fixedReply) {
        this(request -> fixedReply);
    }

    public RecordingChatModel(Function<ChatRequest, String> reply) {
        this.reply = reply;
    }

    /**
     * A model that echoes the rendered user message back, so the route body carries the prompt the AI Service built.
     *
     * @return an echoing model
     */
    public static RecordingChatModel echoing() {
        return new RecordingChatModel(request -> "echo: " + userTextOf(request));
    }

    /**
     * A model that fails the way a provider fails, to prove the exception reaches the route unwrapped from both the AI
     * Service proxy and the reflective invocation.
     *
     * @param  failure the exception the model throws
     * @return         a failing model
     */
    public static RecordingChatModel failingWith(RuntimeException failure) {
        return new RecordingChatModel(request -> {
            throw failure;
        });
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        requests.add(request);
        return ChatResponse.builder().aiMessage(AiMessage.from(reply.apply(request))).build();
    }

    /**
     * Every request the AI Service sent, in order: one per turn, more than one when tools round-trip.
     *
     * @return the recorded requests
     */
    public List<ChatRequest> requests() {
        return requests;
    }

    /**
     * @return the last request the AI Service sent
     */
    public ChatRequest lastRequest() {
        if (requests.isEmpty()) {
            throw new IllegalStateException("The AI Service never called the model");
        }
        return requests.get(requests.size() - 1);
    }

    /**
     * @return the rendered user message of the last request
     */
    public String lastUserMessage() {
        return userTextOf(lastRequest());
    }

    /**
     * @return the rendered system message of the last request, or null when the service declares none
     */
    public String lastSystemMessage() {
        for (ChatMessage message : lastRequest().messages()) {
            if (message instanceof SystemMessage system) {
                return system.text();
            }
        }
        return null;
    }

    /**
     * @return every message of the last request, which is what the chat memory of the bound memory id left behind
     */
    public List<ChatMessage> lastMessages() {
        return lastRequest().messages();
    }

    private static String userTextOf(ChatRequest request) {
        List<ChatMessage> messages = request.messages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage user) {
                return user.singleText();
            }
        }
        return null;
    }
}
