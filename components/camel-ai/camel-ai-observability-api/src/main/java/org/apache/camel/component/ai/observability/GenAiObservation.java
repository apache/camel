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
package org.apache.camel.component.ai.observability;

import java.util.function.Supplier;

/**
 * Active GenAI operation observation. Close when the LLM call completes.
 */
public interface GenAiObservation extends AutoCloseable {

    /**
     * Records a successful GenAI operation with usage metadata.
     */
    void recordSuccess(GenAiUsage usage);

    /**
     * Records a failed GenAI operation.
     */
    void recordError(Throwable error);

    /**
     * Records the number of input tokens read from the provider's prompt cache. Recorded from level
     * {@link GenAiObservabilityLevel#DETAILED}.
     *
     * @since 4.24
     */
    default void recordCachedInputTokens(Long cachedInputTokens) {
        // noop
    }

    /**
     * Records the messages sent to the model. The supplier is only called at level
     * {@link GenAiObservabilityLevel#FULL}.
     *
     * @since 4.24
     */
    default void recordInputMessages(Supplier<GenAiMessages> messages) {
        // noop
    }

    /**
     * Records the messages returned by the model. The supplier is only called at level
     * {@link GenAiObservabilityLevel#FULL}.
     *
     * @since 4.24
     */
    default void recordOutputMessages(Supplier<GenAiMessages> messages) {
        // noop
    }

    /**
     * Records the system instructions given separately from the chat history. The supplier is only called at level
     * {@link GenAiObservabilityLevel#FULL}.
     *
     * @since 4.24
     */
    default void recordSystemInstructions(Supplier<String> instructions) {
        // noop
    }

    /**
     * Records the tools offered to the model in the request. The supplier is only called at level
     * {@link GenAiObservabilityLevel#FULL}.
     *
     * @since 4.24
     */
    default void recordToolDefinitions(Supplier<GenAiToolDefinitions> definitions) {
        // noop
    }

    /**
     * Records the arguments of a tool call (JSON). The supplier is only called at level
     * {@link GenAiObservabilityLevel#FULL}.
     *
     * @since 4.24
     */
    default void recordToolArguments(Supplier<String> arguments) {
        // noop
    }

    /**
     * Records the result of a tool call. The supplier is only called at level {@link GenAiObservabilityLevel#FULL}.
     *
     * @since 4.24
     */
    default void recordToolResult(Supplier<String> result) {
        // noop
    }

    @Override
    void close();
}
