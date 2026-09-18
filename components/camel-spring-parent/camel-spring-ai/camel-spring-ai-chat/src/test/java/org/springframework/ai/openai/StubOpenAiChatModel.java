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
package org.springframework.ai.openai;

import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Test stub placed in Spring AI package namespace for observability tests.
 */
public class StubOpenAiChatModel implements ChatModel {

    private final ChatOptions options = ChatOptions.builder().model("gpt-4o").build();

    @Override
    public ChatOptions getDefaultOptions() {
        return options;
    }

    @Override
    public ChatOptions getOptions() {
        return options;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        Usage usage = new Usage() {
            @Override
            public Integer getPromptTokens() {
                return 3;
            }

            @Override
            public Integer getCompletionTokens() {
                return 2;
            }

            @Override
            public Integer getTotalTokens() {
                return 5;
            }

            @Override
            public Object getNativeUsage() {
                return null;
            }
        };
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .model("gpt-4o-mini")
                .usage(usage)
                .build();
        ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.builder()
                .finishReason("STOP")
                .build();
        AssistantMessage assistantMessage = new AssistantMessage("Hello back");
        Generation generation = new Generation(assistantMessage, generationMetadata);
        return new ChatResponse(List.of(generation), metadata);
    }
}
