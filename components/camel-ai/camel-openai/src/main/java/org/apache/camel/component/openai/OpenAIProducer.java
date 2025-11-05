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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.core.JsonValue;
import com.openai.core.http.StreamResponse;
import com.openai.models.ResponseFormatJsonSchema;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionContentPart;
import com.openai.models.chat.completions.ChatCompletionContentPartImage;
import com.openai.models.chat.completions.ChatCompletionContentPartText;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import com.openai.models.completions.CompletionUsage;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.util.ObjectHelper;

/**
 * OpenAI producer for chat completion.
 */
public class OpenAIProducer extends DefaultAsyncProducer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public OpenAIProducer(OpenAIEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public OpenAIEndpoint getEndpoint() {
        return (OpenAIEndpoint) super.getEndpoint();
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            processInternal(exchange);
            callback.done(true);
            return true;
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }
    }

    private void processInternal(Exchange exchange) throws Exception {
        OpenAIConfiguration config = getEndpoint().getConfiguration();
        Message in = exchange.getIn();

        // Resolve parameters from headers or configuration
        String model = resolveParameter(in, OpenAIConstants.MODEL, config.getModel(), String.class);
        Double temperature = resolveParameter(in, OpenAIConstants.TEMPERATURE, config.getTemperature(), Double.class);
        Double topP = resolveParameter(in, OpenAIConstants.TOP_P, config.getTopP(), Double.class);
        Integer maxTokens = resolveParameter(in, OpenAIConstants.MAX_TOKENS, config.getMaxTokens(), Integer.class);
        Boolean streaming = resolveParameter(in, OpenAIConstants.STREAMING, config.isStreaming(), Boolean.class);
        String outputClass = resolveParameter(in, OpenAIConstants.OUTPUT_CLASS, config.getOutputClass(), String.class);
        String jsonSchema = resolveParameter(in, OpenAIConstants.JSON_SCHEMA, config.getJsonSchema(), String.class);

        List<ChatCompletionMessageParam> messages = buildMessages(exchange, config);

        ChatCompletionCreateParams.Builder paramsBuilder = ChatCompletionCreateParams.builder()
                .model(model);

        for (ChatCompletionMessageParam message : messages) {
            paramsBuilder.addMessage(message);
        }

        if (temperature != null) {
            paramsBuilder.temperature(temperature);
        }
        if (topP != null) {
            paramsBuilder.topP(topP);
        }
        if (maxTokens != null) {
            paramsBuilder.maxTokens(maxTokens.longValue());
        }

        // Structured output handling
        if (outputClass != null && !outputClass.isEmpty()) {
            Class<?> responseClass = getEndpoint().getCamelContext().getClassResolver().resolveClass(outputClass);
            if (responseClass != null) {
                paramsBuilder.responseFormat(responseClass);
            }
        } else if (jsonSchema != null && !jsonSchema.isEmpty()) {
            // Build OpenAI JSON schema response format from provided schema string
            try {
                ResponseFormatJsonSchema.JsonSchema.Schema schema = buildSchemaFromJson(jsonSchema);
                ResponseFormatJsonSchema.JsonSchema jsonSchemaObj = ResponseFormatJsonSchema.JsonSchema.builder()
                        .name("camel_schema")
                        .schema(schema)
                        .build();
                paramsBuilder.responseFormat(
                        ResponseFormatJsonSchema.builder()
                                .jsonSchema(jsonSchemaObj)
                                .build());
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid JSON schema content provided in header/option", e);
            }
        }

        ChatCompletionCreateParams params = paramsBuilder.build();

        if (Boolean.TRUE.equals(streaming)) {
            processStreaming(exchange, params);
        } else {
            processNonStreaming(exchange, params);
        }
    }

    private List<ChatCompletionMessageParam> buildMessages(Exchange exchange, OpenAIConfiguration config) throws Exception {
        Message in = exchange.getIn();
        List<ChatCompletionMessageParam> messages = new ArrayList<>();

        addConversationHistory(messages, in, config);

        ChatCompletionMessageParam userMessage = buildUserMessage(in);
        messages.add(userMessage);

        return messages;
    }

    private void addConversationHistory(List<ChatCompletionMessageParam> messages, Message in, OpenAIConfiguration config) {
        if (!config.isConversationMemory()) {
            return;
        }

        @SuppressWarnings("unchecked")
        List<ChatCompletionMessageParam> history = in.getHeader(
                config.getConversationHistoryHeader(),
                List.class);
        if (history != null) {
            messages.addAll(history);
        }
    }

    private ChatCompletionMessageParam buildUserMessage(Message in) throws Exception {
        Object body = in.getBody();
        String userPrompt = in.getHeader(OpenAIConstants.USER_PROMPT, String.class);

        if (body instanceof String) {
            return buildTextMessage(in, userPrompt);
        } else {
            return buildFileMessage(in, userPrompt);
        }
    }

    private ChatCompletionMessageParam buildTextMessage(Message in, String userPrompt) {
        String prompt = userPrompt != null ? userPrompt : in.getBody(String.class);
        if (prompt == null || prompt.isEmpty()) {
            throw new IllegalArgumentException("Message body or user prompt header must contain the prompt text");
        }

        return createTextMessage(prompt);
    }

    private ChatCompletionMessageParam buildFileMessage(Message in, String userPrompt) throws Exception {
        File inputFile = in.getBody(File.class);
        Path path = inputFile.toPath();
        String mime = Files.probeContentType(path);

        if (mime != null && mime.startsWith("text/")) {
            // Handle text files - read content and use buildTextMessage logic
            String fileContent = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            String prompt = userPrompt != null ? userPrompt : fileContent;
            if (prompt == null || prompt.isEmpty()) {
                throw new IllegalArgumentException("File content or user prompt header must contain the prompt text");
            }
            return createTextMessage(prompt);
        } else if (mime != null && mime.startsWith("image/")) {
            // Handle image files - require user prompt and combine with image
            if (userPrompt == null || userPrompt.isEmpty()) {
                throw new IllegalArgumentException("User prompt header must be set when using image File body");
            }

            ChatCompletionContentPart imageContentPart = createImageContentPart(inputFile, mime);
            ChatCompletionContentPart textContentPart = createTextContentPart(userPrompt);

            return ChatCompletionMessageParam.ofUser(
                    ChatCompletionUserMessageParam.builder()
                            .content(ChatCompletionUserMessageParam.Content.ofArrayOfContentParts(
                                    List.of(textContentPart, imageContentPart)))
                            .build());
        } else {
            throw new IllegalArgumentException("Only text and image files are supported");
        }
    }

    private ChatCompletionMessageParam createTextMessage(String prompt) {
        return ChatCompletionMessageParam.ofUser(
                ChatCompletionUserMessageParam.builder()
                        .content(ChatCompletionUserMessageParam.Content.ofText(prompt))
                        .build());
    }

    private ChatCompletionContentPart createImageContentPart(File inputFile, String mime) throws Exception {
        Path path = inputFile.toPath();
        byte[] img = Files.readAllBytes(path);
        String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(img);

        return ChatCompletionContentPart.ofImageUrl(
                ChatCompletionContentPartImage.builder()
                        .imageUrl(ChatCompletionContentPartImage.ImageUrl.builder()
                                .url(dataUrl)
                                .build())
                        .build());
    }

    private ChatCompletionContentPart createTextContentPart(String text) {
        return ChatCompletionContentPart.ofText(
                ChatCompletionContentPartText.builder()
                        .text(text)
                        .build());
    }

    private void processNonStreaming(Exchange exchange, ChatCompletionCreateParams params)
            throws Exception {
        ChatCompletion response = getEndpoint().getClient().chat().completions().create(params);

        // Extract response content
        String content = response.choices().get(0).message().content().orElse("");
        exchange.getMessage().setBody(content);
        setResponseHeaders(exchange.getMessage(), response);

        // Update conversation history if enabled
        updateConversationHistory(exchange, params, response);
    }

    private void processStreaming(Exchange exchange, ChatCompletionCreateParams params) {
        StreamResponse<ChatCompletionChunk> streamResponse = getEndpoint().getClient().chat().completions()
                .createStreaming(params);

        // hand Camel an Iterator for streaming EIPs (split, recipientList, etc.)
        Iterator<ChatCompletionChunk> it = streamResponse.stream().iterator();
        exchange.getMessage().setBody(it);

        // ensure resp.close() after the Exchange completes (success or failure)
        exchange.getUnitOfWork().addSynchronization(new Synchronization() {
            @Override
            public void onComplete(Exchange e) {
                safeClose();
            }

            @Override
            public void onFailure(Exchange e) {
                safeClose();
            }

            private void safeClose() {
                try {
                    streamResponse.close();
                } catch (Exception ignore) {
                }
            }
        });

    }

    private void setResponseHeaders(Message message, ChatCompletion response) {
        message.setHeader(OpenAIConstants.RESPONSE_ID, response.id());
        message.setHeader(OpenAIConstants.RESPONSE_MODEL, response.model());

        if (!response.choices().isEmpty()) {
            ChatCompletion.Choice choice = response.choices().get(0);
            message.setHeader(OpenAIConstants.FINISH_REASON, choice.finishReason().toString());
        }

        if (response.usage().isPresent()) {
            CompletionUsage usage = response.usage().get();
            message.setHeader(OpenAIConstants.PROMPT_TOKENS, usage.promptTokens());
            message.setHeader(OpenAIConstants.COMPLETION_TOKENS, usage.completionTokens());
            message.setHeader(OpenAIConstants.TOTAL_TOKENS, usage.totalTokens());
        }
    }

    private void updateConversationHistory(
            Exchange exchange, ChatCompletionCreateParams params,
            ChatCompletion response) {
        OpenAIConfiguration config = getEndpoint().getConfiguration();
        if (!config.isConversationMemory()) {
            return;
        }

        Message message = exchange.getMessage();
        @SuppressWarnings("unchecked")
        List<ChatCompletionMessageParam> history = message.getHeader(
                config.getConversationHistoryHeader(),
                List.class);

        if (history == null) {
            history = new ArrayList<>();
        }

        // Add assistant response to history
        String assistantContent = response.choices().get(0).message().content().orElse("");
        ChatCompletionMessageParam assistantMessage = ChatCompletionMessageParam.ofAssistant(
                ChatCompletionAssistantMessageParam.builder()
                        .content(ChatCompletionAssistantMessageParam.Content.ofText(assistantContent))
                        .build());

        history.add(assistantMessage);
        message.setHeader(config.getConversationHistoryHeader(), history);
    }

    private ResponseFormatJsonSchema.JsonSchema.Schema buildSchemaFromJson(String jsonSchemaString) throws Exception {
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> root = OBJECT_MAPPER.readValue(jsonSchemaString, java.util.Map.class);
        if (root == null) {
            throw new IllegalArgumentException("JSON schema string parsed to null");
        }
        if (!(root instanceof java.util.Map)) {
            throw new IllegalArgumentException("JSON schema must be a JSON object at the root");
        }
        ResponseFormatJsonSchema.JsonSchema.Schema.Builder sb = ResponseFormatJsonSchema.JsonSchema.Schema.builder();
        for (java.util.Map.Entry<String, Object> e : root.entrySet()) {
            sb.putAdditionalProperty(e.getKey(), JsonValue.from(e.getValue()));
        }
        return sb.build();
    }

    private <T> T resolveParameter(Message message, String headerName, T defaultValue, Class<T> type) {
        T headerValue = message.getHeader(headerName, type);
        return ObjectHelper.isNotEmpty(headerValue) ? headerValue : defaultValue;
    }

}
