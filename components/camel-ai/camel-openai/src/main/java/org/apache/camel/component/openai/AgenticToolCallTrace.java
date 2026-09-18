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

/**
 * Trace entry for a single tool call within an agentic loop iteration.
 *
 * @param toolName         the tool that was invoked
 * @param argumentsSummary truncated JSON arguments passed to the tool
 * @param resultSummary    truncated textual result returned to the model
 * @param durationMs       wall-clock duration of the tool execution in milliseconds
 * @param success          whether the tool call completed without an error result
 * @since                  4.23
 */
public record AgenticToolCallTrace(
        String toolName,
        String argumentsSummary,
        String resultSummary,
        long durationMs,
        boolean success) {
}
