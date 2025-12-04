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
 * Constants for OpenAI component headers and properties.
 */
public final class OpenAIConstants {

    // Input Headers
    public static final String USER_MESSAGE = "CamelOpenAIUserMessage";
    public static final String SYSTEM_MESSAGE = "CamelOpenAISystemMessage";
    public static final String DEVELOPER_MESSAGE = "CamelOpenAIDeveloperMessage";
    public static final String MODEL = "CamelOpenAIModel";
    public static final String TEMPERATURE = "CamelOpenAITemperature";
    public static final String TOP_P = "CamelOpenAITopP";
    public static final String MAX_TOKENS = "CamelOpenAIMaxTokens";
    public static final String STREAMING = "CamelOpenAIStreaming";
    public static final String OUTPUT_CLASS = "CamelOpenAIOutputClass";
    public static final String JSON_SCHEMA = "CamelOpenAIJsonSchema";

    // Output Headers
    public static final String RESPONSE_MODEL = "CamelOpenAIResponseModel";
    public static final String RESPONSE_ID = "CamelOpenAIResponseId";
    public static final String FINISH_REASON = "CamelOpenAIFinishReason";
    public static final String PROMPT_TOKENS = "CamelOpenAIPromptTokens";
    public static final String COMPLETION_TOKENS = "CamelOpenAICompletionTokens";
    public static final String TOTAL_TOKENS = "CamelOpenAITotalTokens";

    // Output Exchange Properties
    public static final String RESPONSE = "CamelOpenAIResponse";

    private OpenAIConstants() {
        // Utility class
    }
}
