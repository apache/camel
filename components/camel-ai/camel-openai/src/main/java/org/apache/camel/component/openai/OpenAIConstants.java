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

import org.apache.camel.spi.Metadata;

/**
 * Constants for OpenAI component headers and properties.
 */
public final class OpenAIConstants {

    // Input Headers
    @Metadata(description = "The user message to send to the OpenAI chat completion API", javaType = "String")
    public static final String USER_MESSAGE = "CamelOpenAIUserMessage";
    @Metadata(description = "The system message to provide context and instructions to the model", javaType = "String")
    public static final String SYSTEM_MESSAGE = "CamelOpenAISystemMessage";
    @Metadata(description = "The developer message to provide additional instructions to the model", javaType = "String")
    public static final String DEVELOPER_MESSAGE = "CamelOpenAIDeveloperMessage";
    @Metadata(description = "The model to use for chat completion (e.g., gpt-4, gpt-3.5-turbo)", javaType = "String")
    public static final String MODEL = "CamelOpenAIModel";
    @Metadata(description = "Controls randomness in the response. Higher values (e.g., 0.8) make output more random, lower values (e.g., 0.2) make it more deterministic",
              javaType = "Double")
    public static final String TEMPERATURE = "CamelOpenAITemperature";
    @Metadata(description = "An alternative to temperature for controlling randomness. Uses nucleus sampling where the model considers tokens with top_p probability mass",
              javaType = "Double")
    public static final String TOP_P = "CamelOpenAITopP";
    @Metadata(description = "The maximum number of tokens to generate in the completion", javaType = "Integer")
    public static final String MAX_TOKENS = "CamelOpenAIMaxTokens";
    @Metadata(description = "Whether to stream the response back incrementally", javaType = "Boolean")
    public static final String STREAMING = "CamelOpenAIStreaming";
    @Metadata(description = "The Java class to use for structured output parsing", javaType = "Class")
    public static final String OUTPUT_CLASS = "CamelOpenAIOutputClass";
    @Metadata(description = "The JSON schema to use for structured output validation", javaType = "String")
    public static final String JSON_SCHEMA = "CamelOpenAIJsonSchema";

    // Output Headers
    @Metadata(description = "The model used for the completion response", javaType = "String")
    public static final String RESPONSE_MODEL = "CamelOpenAIResponseModel";
    @Metadata(description = "The unique identifier for the completion response", javaType = "String")
    public static final String RESPONSE_ID = "CamelOpenAIResponseId";
    @Metadata(description = "The reason the completion finished (e.g., stop, length, content_filter)", javaType = "String")
    public static final String FINISH_REASON = "CamelOpenAIFinishReason";
    @Metadata(description = "The number of tokens used in the prompt", javaType = "Integer")
    public static final String PROMPT_TOKENS = "CamelOpenAIPromptTokens";
    @Metadata(description = "The number of tokens used in the completion", javaType = "Integer")
    public static final String COMPLETION_TOKENS = "CamelOpenAICompletionTokens";
    @Metadata(description = "The total number of tokens used (prompt + completion)", javaType = "Integer")
    public static final String TOTAL_TOKENS = "CamelOpenAITotalTokens";

    // Output Exchange Properties
    @Metadata(description = "The complete OpenAI response object", javaType = "com.openai.models.ChatCompletion")
    public static final String RESPONSE = "CamelOpenAIResponse";

    private OpenAIConstants() {
        // Utility class
    }
}
