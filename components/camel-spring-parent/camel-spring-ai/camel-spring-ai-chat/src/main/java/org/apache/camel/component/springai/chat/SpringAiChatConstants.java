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
package org.apache.camel.component.springai.chat;

import org.apache.camel.spi.Metadata;

/**
 * Constants for Spring AI Chat component headers.
 */
public final class SpringAiChatConstants {

    @Metadata(description = "The response from the chat model", javaType = "String")
    public static final String CHAT_RESPONSE = "CamelSpringAiChatResponse";

    @Metadata(description = "The number of input tokens used", javaType = "Integer")
    public static final String INPUT_TOKEN_COUNT = "CamelSpringAiInputTokenCount";

    @Metadata(description = "The number of output tokens used", javaType = "Integer")
    public static final String OUTPUT_TOKEN_COUNT = "CamelSpringAiOutputTokenCount";

    @Metadata(description = "The total number of tokens used", javaType = "Integer")
    public static final String TOTAL_TOKEN_COUNT = "CamelSpringAiTotalTokenCount";

    @Metadata(description = "The prompt template with placeholders for variable substitution", javaType = "String")
    public static final String PROMPT_TEMPLATE = "CamelSpringAiChatPromptTemplate";

    @Metadata(description = "Augmented data for RAG as List<org.springframework.ai.document.Document>",
              javaType = "java.util.List<org.springframework.ai.document.Document>")
    public static final String AUGMENTED_DATA = "CamelSpringAiChatAugmentedData";

    @Metadata(description = "System message for the conversation", javaType = "String")
    public static final String SYSTEM_MESSAGE = "CamelSpringAiChatSystemMessage";

    @Metadata(description = "Temperature parameter for response randomness (0.0-2.0)", javaType = "Double")
    public static final String TEMPERATURE = "CamelSpringAiChatTemperature";

    @Metadata(description = "Maximum tokens in the response", javaType = "Integer")
    public static final String MAX_TOKENS = "CamelSpringAiChatMaxTokens";

    @Metadata(description = "Top P parameter for nucleus sampling", javaType = "Double")
    public static final String TOP_P = "CamelSpringAiChatTopP";

    @Metadata(description = "Top K parameter for sampling", javaType = "Integer")
    public static final String TOP_K = "CamelSpringAiChatTopK";

    @Metadata(description = "User message text for multimodal requests", javaType = "String")
    public static final String USER_MESSAGE = "CamelSpringAiChatUserMessage";

    @Metadata(description = "Media data for multimodal requests (image or audio)", javaType = "byte[]")
    public static final String MEDIA_DATA = "CamelSpringAiChatMediaData";

    @Metadata(description = "Media type (MIME type) for multimodal requests (e.g., image/png, audio/wav)", javaType = "String")
    public static final String MEDIA_TYPE = "CamelSpringAiChatMediaType";

    @Metadata(description = "The output format type for structured output conversion (BEAN, MAP, LIST)", javaType = "String")
    public static final String OUTPUT_FORMAT = "CamelSpringAiChatOutputFormat";

    @Metadata(description = "The Java class to use for structured output bean conversion", javaType = "Class<?>")
    public static final String OUTPUT_CLASS = "CamelSpringAiChatOutputClass";

    @Metadata(description = "The structured output converted from the chat response", javaType = "Object")
    public static final String STRUCTURED_OUTPUT = "CamelSpringAiChatStructuredOutput";

    @Metadata(description = "Comma-separated list of sensitive words for SafeGuard advisor", javaType = "String")
    public static final String SAFEGUARD_SENSITIVE_WORDS = "CamelSpringAiChatSafeguardSensitiveWords";

    @Metadata(description = "Failure response message for SafeGuard advisor when sensitive content is detected",
              javaType = "String")
    public static final String SAFEGUARD_FAILURE_RESPONSE = "CamelSpringAiChatSafeguardFailureResponse";

    @Metadata(description = "Order of execution for SafeGuard advisor", javaType = "Integer")
    public static final String SAFEGUARD_ORDER = "CamelSpringAiChatSafeguardOrder";

    @Metadata(description = "List of custom advisors to add to the request",
              javaType = "java.util.List<org.springframework.ai.chat.client.advisor.api.Advisor>")
    public static final String ADVISORS = "CamelSpringAiChatAdvisors";

    @Metadata(description = "The Java class to use for entity response conversion", javaType = "Class<?>")
    public static final String ENTITY_CLASS = "CamelSpringAiChatEntityClass";

    @Metadata(description = "Metadata to attach to user messages", javaType = "java.util.Map<String, Object>")
    public static final String USER_METADATA = "CamelSpringAiChatUserMetadata";

    @Metadata(description = "Metadata to attach to system messages", javaType = "java.util.Map<String, Object>")
    public static final String SYSTEM_METADATA = "CamelSpringAiChatSystemMetadata";

    @Metadata(description = "Conversation ID for managing separate conversation contexts in chat memory", javaType = "String")
    public static final String CONVERSATION_ID = "CamelSpringAiChatConversationId";

    @Metadata(description = "Maximum file size in bytes for multimodal content. Overrides endpoint configuration.",
              javaType = "Long")
    public static final String MAX_FILE_SIZE = "CamelSpringAiChatMaxFileSize";

    @Metadata(description = "The reason why the chat response generation stopped (e.g., STOP, LENGTH, TOOL_CALLS)",
              javaType = "String")
    public static final String FINISH_REASON = "CamelSpringAiChatFinishReason";

    @Metadata(description = "The name of the AI model used to generate the response", javaType = "String")
    public static final String MODEL_NAME = "CamelSpringAiChatModelName";

    @Metadata(description = "The unique ID of the chat response", javaType = "String")
    public static final String RESPONSE_ID = "CamelSpringAiChatResponseId";

    @Metadata(description = "Full response metadata as a Map containing all available metadata fields",
              javaType = "java.util.Map<String, Object>")
    public static final String RESPONSE_METADATA = "CamelSpringAiChatResponseMetadata";

    private SpringAiChatConstants() {
    }
}
