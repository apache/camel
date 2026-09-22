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

import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.completions.CompletionUsage;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseUsage;
import org.apache.camel.Message;

/**
 * Tracks cumulative token usage across the agentic loop of the chat-completion and responses operations.
 */
final class OpenAIAgenticTokenTracker {

    private long promptTokens;
    private long completionTokens;

    record Snapshot(long promptTokens, long completionTokens) {
    }

    Snapshot snapshot() {
        return new Snapshot(promptTokens, completionTokens);
    }

    long promptTokensSince(Snapshot before) {
        return promptTokens - before.promptTokens();
    }

    long completionTokensSince(Snapshot before) {
        return completionTokens - before.completionTokens();
    }

    void addUsage(ChatCompletion response) {
        if (response == null) {
            return;
        }
        response.usage().ifPresent(this::addUsage);
    }

    void addUsage(CompletionUsage usage) {
        promptTokens += usage.promptTokens();
        completionTokens += usage.completionTokens();
    }

    void addUsage(Response response) {
        if (response == null) {
            return;
        }
        response.usage().ifPresent(this::addUsage);
    }

    /** The Responses API counts the same tokens under the input and output names. */
    void addUsage(ResponseUsage usage) {
        promptTokens += usage.inputTokens();
        completionTokens += usage.outputTokens();
    }

    void setHeaders(Message message) {
        message.setHeader(OpenAIConstants.AGENTIC_PROMPT_TOKENS, promptTokens);
        message.setHeader(OpenAIConstants.AGENTIC_COMPLETION_TOKENS, completionTokens);
        message.setHeader(OpenAIConstants.AGENTIC_TOTAL_TOKENS, getTotalTokens());
    }

    boolean exceedsBudget(long maxAgenticTokens) {
        return maxAgenticTokens > 0 && getTotalTokens() > maxAgenticTokens;
    }

    long getPromptTokens() {
        return promptTokens;
    }

    long getCompletionTokens() {
        return completionTokens;
    }

    long getTotalTokens() {
        return promptTokens + completionTokens;
    }
}
