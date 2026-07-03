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
package org.apache.camel.component.ai.tools;

/**
 * Result of a tool execution by {@link AiToolExecutor}. Classifies the outcome without deciding error handling policy.
 * Framework adapters (LangChain4j, Spring AI, OpenAI) inspect the result type and decide whether to return the error as
 * a string to the LLM, rethrow the cause so framework-level error handlers fire, or sanitize the message before
 * returning it.
 * <p>
 * <b>Security note:</b> {@link ExecutionError#message()} may contain raw exception messages from route execution, which
 * can include internal details (file paths, database errors, class names). Framework adapters MUST NOT return this
 * message verbatim to the LLM without sanitization. Use a generic message (e.g., "Tool execution failed") for the LLM
 * response and log the detailed cause internally.
 *
 * @since 4.22
 */
public sealed interface AiToolResult {

    /**
     * The route executed successfully and produced a result.
     *
     * @param value the route result as a string
     */
    record Success(String value) implements AiToolResult {
    }

    /**
     * The tool call failed before reaching the route due to a missing required argument.
     *
     * @param message a human-readable description of the validation failure
     * @param cause   the underlying exception
     */
    record ArgumentError(String message, Exception cause) implements AiToolResult {
    }

    /**
     * The route processor threw an exception or the exchange carries an exception after processing.
     *
     * @param message a human-readable description of the execution failure
     * @param cause   the underlying exception
     */
    record ExecutionError(String message, Exception cause) implements AiToolResult {
    }
}
