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

import java.util.List;

/**
 * Trace entry for one iteration of the OpenAI MCP agentic loop.
 *
 * @param iteration        1-based sequence number for each model call in the agentic loop
 * @param toolCalls        tool calls executed in this iteration, empty when the model produced a final answer
 * @param promptTokens     prompt tokens consumed by the model call in this iteration
 * @param completionTokens completion tokens consumed by the model call in this iteration
 * @param durationMs       wall-clock duration of the iteration in milliseconds
 * @since                  4.23
 */
public record AgenticIterationTrace(
        int iteration,
        List<AgenticToolCallTrace> toolCalls,
        long promptTokens,
        long completionTokens,
        long durationMs) {
}
