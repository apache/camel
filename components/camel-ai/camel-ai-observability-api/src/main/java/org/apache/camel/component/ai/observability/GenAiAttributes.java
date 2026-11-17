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

/**
 * OpenTelemetry GenAI semantic convention attribute keys (stable subset aligned with Spring AI).
 */
public final class GenAiAttributes {

    public static final String OPERATION_NAME = "gen_ai.operation.name";
    public static final String SYSTEM = "gen_ai.system";
    public static final String REQUEST_MODEL = "gen_ai.request.model";
    public static final String RESPONSE_MODEL = "gen_ai.response.model";
    public static final String INPUT_TOKENS = "gen_ai.usage.input_tokens";
    public static final String OUTPUT_TOKENS = "gen_ai.usage.output_tokens";
    public static final String FINISH_REASONS = "gen_ai.response.finish_reasons";
    public static final String ERROR_TYPE = "error.type";
    public static final String CAMEL_COMPONENT = "camel.component";

    // recorded from level detailed (development status in the OpenTelemetry GenAI semantic conventions)
    public static final String PROVIDER_NAME = "gen_ai.provider.name";
    public static final String CONVERSATION_ID = "gen_ai.conversation.id";
    public static final String AGENT_NAME = "gen_ai.agent.name";
    public static final String TOOL_NAME = "gen_ai.tool.name";
    public static final String TOOL_CALL_ID = "gen_ai.tool.call.id";
    public static final String CACHE_READ_INPUT_TOKENS = "gen_ai.usage.cache_read.input_tokens";
    public static final String REQUEST_PREFIX = "gen_ai.request.";

    // recorded at level full
    public static final String INPUT_MESSAGES = "gen_ai.input.messages";
    public static final String OUTPUT_MESSAGES = "gen_ai.output.messages";
    public static final String SYSTEM_INSTRUCTIONS = "gen_ai.system_instructions";
    public static final String TOOL_CALL_ARGUMENTS = "gen_ai.tool.call.arguments";
    public static final String TOOL_CALL_RESULT = "gen_ai.tool.call.result";

    private GenAiAttributes() {
    }
}
