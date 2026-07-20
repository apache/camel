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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.core.JsonField;
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
import com.openai.models.chat.completions.ChatCompletionDeveloperMessageParam;
import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import com.openai.models.completions.CompletionUsage;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.WrappedFile;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenAI producer for chat completion.
 */
public class OpenAIProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(OpenAIProducer.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern THINK_PATTERN = Pattern.compile("^\\s*<think>(.*?)</think>\\s*", Pattern.DOTALL);
    private static final String PENDING_USER_MESSAGE = "CamelOpenAIPendingUserMessage";

    private Class<?> outputClassResolved;

    public OpenAIProducer(OpenAIEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected void doStart() throws Exception {
        OpenAIConfiguration config = getEndpoint().getConfiguration();

        if (ObjectHelper.isNotEmpty(config.getOutputClass())) {
            outputClassResolved = getEndpoint().getCamelContext().getClassResolver()
                    .resolveMandatoryClass(config.getOutputClass());
        }

        if (ObjectHelper.isNotEmpty(config.getJsonSchema())) {
            String resolved = getEndpoint().getCamelContext().resolvePropertyPlaceholders(config.getJsonSchema());
            String content = resolveResourceContent(resolved);
            if (content != null) {
                config.setJsonSchema(content);
            } else {
                config.setJsonSchema(resolved);
            }
        }

        super.doStart();
    }

    private String resolveResourceContent(String property) {
        try (InputStream is = ResourceHelper.resolveResourceAsInputStream(getEndpoint().getCamelContext(), property)) {
            if (is != null) {
                return getEndpoint().getCamelContext().getTypeConverter().convertTo(String.class, is);
            }
        } catch (Exception e) {
            // ignore and treat the value as inline content
        }
        return null;
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
            paramsBuilder.maxCompletionTokens(maxTokens.longValue());
        }

        // Structured output handling
        if (ObjectHelper.isNotEmpty(outputClass)) {
            Class<?> responseClass;
            String headerOutputClass = in.getHeader(OpenAIConstants.OUTPUT_CLASS, String.class);
            if (ObjectHelper.isNotEmpty(headerOutputClass)) {
                responseClass = getEndpoint().getCamelContext().getClassResolver()
                        .resolveMandatoryClass(headerOutputClass);
            } else {
                responseClass = outputClassResolved;
            }
            paramsBuilder.responseFormat(responseClass);
        } else if (ObjectHelper.isNotEmpty(jsonSchema)) {
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

        applyAdditionalBodyProperties(paramsBuilder, config);

        // Add MCP tools to the request if configured
        List<ChatCompletionFunctionTool> mcpTools = getEndpoint().getMcpToolState().tools();
        boolean hasMcpTools = mcpTools != null && !mcpTools.isEmpty();
        if (hasMcpTools) {
            for (ChatCompletionFunctionTool tool : mcpTools) {
                paramsBuilder.addTool(tool);
            }
        }

        ChatCompletionCreateParams params = paramsBuilder.build();

        if (Boolean.TRUE.equals(streaming) && hasMcpTools && config.isAutoToolExecution()) {
            LOG.info("Streaming with MCP tools is not supported; falling back to non-streaming for the agentic loop");
            processNonStreaming(exchange, params, config);
        } else if (Boolean.TRUE.equals(streaming)) {
            processStreaming(exchange, params);
        } else {
            processNonStreaming(exchange, params, config);
        }
    }

    private List<ChatCompletionMessageParam> buildMessages(Exchange exchange, OpenAIConfiguration config)
            throws Exception {
        Message in = exchange.getIn();
        List<ChatCompletionMessageParam> messages = new ArrayList<>();

        exchange.removeProperty(PENDING_USER_MESSAGE);

        // If a system message is configured and conversation memory is enabled, reset
        // history
        if (ObjectHelper.isNotEmpty(config.getSystemMessage()) && config.isConversationMemory()) {
            exchange.removeProperty(config.getConversationHistoryProperty());
        }

        String systemPrompt = in.getHeader(OpenAIConstants.SYSTEM_MESSAGE, String.class);
        String developerPrompt = in.getHeader(OpenAIConstants.DEVELOPER_MESSAGE, String.class);
        if (systemPrompt == null || systemPrompt.isEmpty() && ObjectHelper.isNotEmpty(config.getSystemMessage())) {
            systemPrompt = config.getSystemMessage();
        }
        if (developerPrompt == null
                || developerPrompt.isEmpty() && ObjectHelper.isNotEmpty(config.getDeveloperMessage())) {
            developerPrompt = config.getDeveloperMessage();
        }

        // Prepend system and developer messages when configured
        if (ObjectHelper.isNotEmpty(systemPrompt)) {
            messages.add(createSystemMessage(systemPrompt));
        }
        if (ObjectHelper.isNotEmpty(developerPrompt)) {
            messages.add(createDeveloperMessage(developerPrompt));
        }

        addConversationHistory(messages, in, config);

        ChatCompletionMessageParam userMessage = buildUserMessage(in, config);
        if (userMessage != null) {
            messages.add(userMessage);
            if (config.isConversationMemory()) {
                exchange.setProperty(PENDING_USER_MESSAGE, userMessage);
            }
        }

        if (messages.isEmpty()) {
            throw new IllegalArgumentException(
                    "No input provided to LLM. At least one message (user, system, or developer) must be provided");
        }

        return messages;
    }

    private void addConversationHistory(
            List<ChatCompletionMessageParam> messages, Message in,
            OpenAIConfiguration config) {
        if (!config.isConversationMemory()) {
            return;
        }

        List<ChatCompletionMessageParam> history = in.getExchange().getProperty(
                config.getConversationHistoryProperty(),
                List.class);
        if (history != null) {
            messages.addAll(history);
        }
    }

    private ChatCompletionMessageParam buildUserMessage(Message in, OpenAIConfiguration config) throws Exception {
        Object body = in.getBody();
        String userPrompt = in.getHeader(OpenAIConstants.USER_MESSAGE, String.class);
        if (userPrompt == null || userPrompt.isEmpty() && ObjectHelper.isNotEmpty(config.getUserMessage())) {
            userPrompt = config.getUserMessage();
        }

        if (body instanceof WrappedFile || body instanceof File || body instanceof Path) {
            return buildFileMessage(in, userPrompt, config);
        } else if (body instanceof byte[] || body instanceof InputStream) {
            return buildBinaryMessage(in, userPrompt, config);
        } else {
            return buildTextMessage(in, userPrompt, config);
        }
    }

    private ChatCompletionMessageParam buildTextMessage(Message in, String userPrompt, OpenAIConfiguration config) {
        String prompt = userPrompt != null ? userPrompt : in.getBody(String.class);
        if (prompt == null || prompt.trim().isEmpty()) {
            return null;
        }
        return createTextMessage(prompt);
    }

    private ChatCompletionMessageParam buildFileMessage(Message in, String userPrompt, OpenAIConfiguration config)
            throws Exception {
        Object body = in.getBody();
        File inputFile = null;
        if (body instanceof WrappedFile<?> wrappedFile && wrappedFile.getFile() instanceof File file) {
            // local file-based components (camel-file) expose the underlying java.io.File
            inputFile = file;
        } else if (body instanceof File file) {
            inputFile = file;
        } else if (body instanceof Path path) {
            inputFile = path.toFile();
        }

        // for remote file-based components (FTP, SFTP, ...) there is no local java.io.File, so the
        // MIME type is detected from headers and the file name only, before reading any content
        String mime = inputFile != null
                ? MimeTypeHelper.resolveForFile(in, inputFile) : MimeTypeHelper.resolveForBinary(in);

        if (MimeTypeHelper.isText(mime)) {
            // Handle text files - read content and use buildTextMessage logic
            String prompt = userPrompt;
            if (prompt == null || prompt.isEmpty()) {
                // the type converter reads the content honoring the charset configured on file-based endpoints
                prompt = in.getBody(String.class);
            }

            if (prompt == null || prompt.isEmpty()) {
                throw new IllegalArgumentException(
                        "File content or user message configuration must contain the prompt text");
            }
            return createTextMessage(prompt);
        } else if (MimeTypeHelper.isImage(mime)) {
            byte[] image = inputFile != null ? Files.readAllBytes(inputFile.toPath()) : readBodyBytes(in);
            return createImageMessage(image, mime, userPrompt);
        } else {
            throw unsupportedMimeType(mime,
                    inputFile != null ? inputFile.getName() : in.getHeader(Exchange.FILE_NAME, String.class));
        }
    }

    private ChatCompletionMessageParam buildBinaryMessage(Message in, String userPrompt, OpenAIConfiguration config)
            throws Exception {
        String mime = MimeTypeHelper.resolveForBinary(in);
        if (MimeTypeHelper.isImage(mime)) {
            return createImageMessage(readBodyBytes(in), mime, userPrompt);
        }
        // not an image: keep the previous behavior and treat the payload as text
        return buildTextMessage(in, userPrompt, config);
    }

    private byte[] readBodyBytes(Message in) throws IOException {
        Object body = in.getBody();
        if (body instanceof byte[] bytes) {
            return bytes;
        }
        InputStream is = in.getBody(InputStream.class);
        if (is == null) {
            throw new IllegalArgumentException(
                    "Cannot read message body as InputStream: " + (body != null ? body.getClass().getName() : "null"));
        }
        try (is) {
            return is.readAllBytes();
        }
    }

    private IllegalArgumentException unsupportedMimeType(String mime, String fileName) {
        return new IllegalArgumentException(
                "Only text and image files are supported. Detected MIME type: " + mime
                                            + (fileName != null ? " for file: " + fileName : "")
                                            + ". Set the " + OpenAIConstants.MEDIA_TYPE
                                            + " header to override the MIME type detection");
    }

    private ChatCompletionMessageParam createTextMessage(String prompt) {
        return ChatCompletionMessageParam.ofUser(
                ChatCompletionUserMessageParam.builder()
                        .content(ChatCompletionUserMessageParam.Content.ofText(prompt))
                        .build());
    }

    private ChatCompletionMessageParam createSystemMessage(String text) {
        return ChatCompletionMessageParam.ofSystem(
                ChatCompletionSystemMessageParam.builder()
                        .content(ChatCompletionSystemMessageParam.Content.ofText(text))
                        .build());
    }

    private ChatCompletionMessageParam createDeveloperMessage(String text) {
        return ChatCompletionMessageParam.ofDeveloper(
                ChatCompletionDeveloperMessageParam.builder()
                        .content(ChatCompletionDeveloperMessageParam.Content.ofText(text))
                        .build());
    }

    private ChatCompletionMessageParam createImageMessage(byte[] image, String mime, String userPrompt) {
        // image input requires a user prompt to combine with the image
        if (userPrompt == null || userPrompt.isEmpty()) {
            throw new IllegalArgumentException("User message configuration must be set when using an image body");
        }

        ChatCompletionContentPart imageContentPart = createImageContentPart(image, mime);
        ChatCompletionContentPart textContentPart = createTextContentPart(userPrompt);

        return ChatCompletionMessageParam.ofUser(
                ChatCompletionUserMessageParam.builder()
                        .content(ChatCompletionUserMessageParam.Content.ofArrayOfContentParts(
                                List.of(textContentPart, imageContentPart)))
                        .build());
    }

    private ChatCompletionContentPart createImageContentPart(byte[] image, String mime) {
        String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(image);

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

    private void processNonStreaming(Exchange exchange, ChatCompletionCreateParams params, OpenAIConfiguration config)
            throws Exception {
        List<ChatCompletionFunctionTool> mcpTools = getEndpoint().getMcpToolState().tools();
        boolean hasMcpTools = mcpTools != null && !mcpTools.isEmpty();

        if (!hasMcpTools || !config.isAutoToolExecution()) {
            // Path A: No MCP tools or auto-execution disabled -- existing behavior
            processNonStreamingSimple(exchange, params, config);
        } else {
            // Path B: MCP tools with agentic loop
            processNonStreamingAgentic(exchange, params, config);
        }
    }

    private void processNonStreamingSimple(
            Exchange exchange, ChatCompletionCreateParams params, OpenAIConfiguration config)
            throws Exception {
        ChatCompletion response = getEndpoint().getClient().chat().completions().create(params);
        if (config.isStoreFullResponse()) {
            exchange.setProperty(OpenAIConstants.RESPONSE, response);
        }

        ChatCompletion.Choice choice = requireFirstChoice(exchange, response);
        if (isToolCallsFinishReason(choice)) {
            exchange.getMessage().setBody(choice.message().toolCalls());
        } else {
            String content = choice.message().content().orElse("");
            content = processThinkingContent(exchange, content, config);
            exchange.getMessage().setBody(content);
            extractReasoningContent(exchange, choice.message());
            extractAdditionalResponseHeaders(exchange, choice.message());
        }
        setResponseHeaders(exchange.getMessage(), response);
        updateConversationHistory(exchange, params, response);
    }

    private void processNonStreamingAgentic(
            Exchange exchange, ChatCompletionCreateParams params, OpenAIConfiguration config)
            throws Exception {

        int maxIterations = config.getMaxToolIterations();
        LOG.debug("Starting agentic loop with maxToolIterations={}, available tools: {}", maxIterations,
                getEndpoint().getMcpToolState().toolClientMap().keySet());

        // Rebuild the builder from the immutable params so we can accumulate messages
        ChatCompletionCreateParams.Builder paramsBuilder = params.toBuilder();

        List<ChatCompletionMessageParam> agenticMessages = new ArrayList<>();
        List<String> toolCallsLog = new ArrayList<>();
        OpenAIAgenticTokenTracker tokenTracker = new OpenAIAgenticTokenTracker();
        int iteration = 0;

        while (iteration < maxIterations) {
            ChatCompletion response = getEndpoint().getClient().chat().completions().create(paramsBuilder.build());
            tokenTracker.addUsage(response);
            setAgenticTokenHeaders(exchange.getMessage(), tokenTracker);

            ChatCompletion.Choice choice = requireFirstChoice(exchange, response);

            if (!isToolCallsFinishReason(choice)) {
                // Final LLM response
                LOG.debug("Agentic loop completed after {} iterations, finish reason: {}", iteration,
                        getFinishReasonString(choice));
                String content = choice.message().content().orElse("");
                content = processThinkingContent(exchange, content, config);
                exchange.getMessage().setBody(content);
                extractReasoningContent(exchange, choice.message());
                extractAdditionalResponseHeaders(exchange, choice.message());
                setResponseHeaders(exchange.getMessage(), response);
                exchange.getMessage().setHeader(OpenAIConstants.TOOL_ITERATIONS, iteration);
                exchange.getMessage().setHeader(OpenAIConstants.MCP_TOOL_CALLS, toolCallsLog);
                exchange.getMessage().setHeader(OpenAIConstants.MCP_RETURN_DIRECT, false);
                if (config.isStoreFullResponse()) {
                    exchange.setProperty(OpenAIConstants.RESPONSE, response);
                }
                updateConversationHistory(exchange, agenticMessages, response);
                return;
            }

            enforceAgenticTokenBudget(config, tokenTracker, iteration);

            iteration++;
            LOG.debug("Iteration {}: model requested {} tool call(s)", iteration,
                    choice.message().toolCalls().map(List::size).orElse(0));

            // Add assistant message with tool_calls to conversation
            ChatCompletionMessage assistantMsg = choice.message();
            List<ChatCompletionMessageToolCall> toolCalls = assistantMsg.toolCalls().orElse(List.of());
            ChatCompletionMessageParam assistantParam = ChatCompletionMessageParam.ofAssistant(
                    ChatCompletionAssistantMessageParam.builder()
                            .toolCalls(toolCalls)
                            .build());
            paramsBuilder.addMessage(assistantParam);
            agenticMessages.add(assistantParam);

            // Execute all tool calls in this batch
            boolean allReturnDirect = true;
            List<ToolResultEntry> batchResults = new ArrayList<>();

            for (ChatCompletionMessageToolCall toolCall : toolCalls) {
                String toolName = toolCall.asFunction().function().name();
                String argsJson = toolCall.asFunction().function().arguments();
                String toolCallId = toolCall.asFunction().id();
                toolCallsLog.add(toolName);

                McpToolState mcpToolState = getEndpoint().getMcpToolState();
                McpSyncClient mcpClient = mcpToolState.toolClientMap().get(toolName);
                if (mcpClient == null) {
                    throw new IllegalStateException(
                            "Tool '" + toolName + "' not found in any configured MCP server");
                }

                LOG.debug("Executing MCP tool '{}' with args: {}", toolName, argsJson);
                String resultContent;

                try {
                    Map<String, Object> argsMap = OBJECT_MAPPER.readValue(argsJson, Map.class);
                    McpSchema.CallToolResult toolResult
                            = getEndpoint().callTool(mcpClient, toolName, argsMap);

                    if (Boolean.TRUE.equals(toolResult.isError())) {
                        resultContent = "Error: " + extractTextContent(toolResult.content());
                        allReturnDirect = false;
                    } else {
                        resultContent = extractTextContent(toolResult.content());
                        if (!mcpToolState.returnDirectTools().contains(toolName)) {
                            allReturnDirect = false;
                        }
                    }
                } catch (JsonProcessingException e) {
                    LOG.warn("Invalid tool arguments for '{}': {}", toolName, argsJson, e);
                    resultContent = "Error: invalid tool arguments: " + e.getMessage();
                    allReturnDirect = false;
                } catch (Exception e) {
                    LOG.warn("MCP tool '{}' execution failed: {}", toolName, e.getMessage(), e);
                    resultContent = "Error: Tool execution failed: " + e.getMessage();
                    allReturnDirect = false;
                }

                LOG.debug("Tool '{}' result: {}", toolName, resultContent);
                batchResults.add(new ToolResultEntry(toolCallId, resultContent));
            }

            // returnDirect check: if ALL tools in this batch are returnDirect, short-circuit
            if (allReturnDirect && !batchResults.isEmpty()) {
                LOG.debug("All tools in batch have returnDirect=true, short-circuiting agentic loop");
                StringBuilder directResult = new StringBuilder();
                for (ToolResultEntry entry : batchResults) {
                    if (!directResult.isEmpty()) {
                        directResult.append("\n");
                    }
                    directResult.append(entry.content());
                }

                exchange.getMessage().setBody(directResult.toString());
                setResponseHeaders(exchange.getMessage(), response);
                exchange.getMessage().setHeader(OpenAIConstants.TOOL_ITERATIONS, iteration);
                exchange.getMessage().setHeader(OpenAIConstants.MCP_TOOL_CALLS, toolCallsLog);
                exchange.getMessage().setHeader(OpenAIConstants.MCP_RETURN_DIRECT, true);
                updateConversationHistory(exchange, agenticMessages, directResult.toString());
                return;
            }

            // Normal path: feed tool results back to LLM
            LOG.debug("Feeding {} tool result(s) back to the model", batchResults.size());
            for (ToolResultEntry entry : batchResults) {
                ChatCompletionMessageParam toolMsg = ChatCompletionMessageParam.ofTool(
                        ChatCompletionToolMessageParam.builder()
                                .toolCallId(entry.toolCallId())
                                .content(entry.content())
                                .build());
                paramsBuilder.addMessage(toolMsg);
                agenticMessages.add(toolMsg);
            }
        }

        throw new IllegalStateException(
                "Max tool iterations (%d) exceeded. Tools called: %s".formatted(maxIterations, toolCallsLog));
    }

    private void setAgenticTokenHeaders(Message message, OpenAIAgenticTokenTracker tokenTracker) {
        message.setHeader(OpenAIConstants.AGENTIC_PROMPT_TOKENS, tokenTracker.getPromptTokens());
        message.setHeader(OpenAIConstants.AGENTIC_COMPLETION_TOKENS, tokenTracker.getCompletionTokens());
        message.setHeader(OpenAIConstants.AGENTIC_TOTAL_TOKENS, tokenTracker.getTotalTokens());
    }

    private void enforceAgenticTokenBudget(
            OpenAIConfiguration config, OpenAIAgenticTokenTracker tokenTracker, int iteration) {
        long maxAgenticTokens = config.getMaxAgenticTokens();
        if (maxAgenticTokens <= 0 || tokenTracker.getTotalTokens() <= maxAgenticTokens) {
            return;
        }
        throw new IllegalStateException(
                "Max agentic tokens (%d) exceeded at iteration %d. Cumulative usage: prompt=%d, completion=%d, total=%d"
                        .formatted(maxAgenticTokens, iteration, tokenTracker.getPromptTokens(),
                                tokenTracker.getCompletionTokens(), tokenTracker.getTotalTokens()));
    }

    private String extractTextContent(List<McpSchema.Content> contents) {
        if (contents == null || contents.isEmpty()) {
            return "";
        }
        return contents.stream()
                .filter(McpSchema.TextContent.class::isInstance)
                .map(McpSchema.TextContent.class::cast)
                .map(McpSchema.TextContent::text)
                .collect(Collectors.joining());
    }

    private record ToolResultEntry(String toolCallId, String content) {
    }

    private void processStreaming(Exchange exchange, ChatCompletionCreateParams params) {
        // NOTE: the stream is going to be closed after the exchange completes.
        StreamResponse<ChatCompletionChunk> streamResponse = getEndpoint().getClient().chat().completions() // NOSONAR
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
                    LOG.warn("An error happened while processing streaming: ignoring", ignore);
                }
            }
        });

    }

    private static ChatCompletion.Choice requireFirstChoice(Exchange exchange, ChatCompletion response)
            throws CamelExchangeException {
        if (response.choices().isEmpty()) {
            throw new CamelExchangeException("OpenAI response contained no choices", exchange);
        }
        return response.choices().get(0);
    }

    private static boolean isToolCallsFinishReason(ChatCompletion.Choice choice) {
        JsonField<ChatCompletion.Choice.FinishReason> field = choice._finishReason();
        return field.asKnown()
                .map(r -> r.equals(ChatCompletion.Choice.FinishReason.TOOL_CALLS))
                .orElse(false);
    }

    private static String getFinishReasonString(ChatCompletion.Choice choice) {
        JsonField<ChatCompletion.Choice.FinishReason> field = choice._finishReason();
        return field.asKnown()
                .map(ChatCompletion.Choice.FinishReason::toString)
                .orElse("stop");
    }

    private void setResponseHeaders(Message message, ChatCompletion response) {
        message.setHeader(OpenAIConstants.RESPONSE_ID, response.id());
        message.setHeader(OpenAIConstants.RESPONSE_MODEL, response.model());

        if (!response.choices().isEmpty()) {
            ChatCompletion.Choice choice = response.choices().get(0);
            message.setHeader(OpenAIConstants.FINISH_REASON, getFinishReasonString(choice));
        }

        if (response.usage().isPresent()) {
            CompletionUsage usage = response.usage().get();
            message.setHeader(OpenAIConstants.PROMPT_TOKENS, usage.promptTokens());
            message.setHeader(OpenAIConstants.COMPLETION_TOKENS, usage.completionTokens());
            message.setHeader(OpenAIConstants.TOTAL_TOKENS, usage.totalTokens());
        }
    }

    private void appendPendingUserMessageToHistory(Exchange exchange, List<ChatCompletionMessageParam> history) {
        ChatCompletionMessageParam pendingUserMessage
                = exchange.getProperty(PENDING_USER_MESSAGE, ChatCompletionMessageParam.class);
        if (pendingUserMessage != null) {
            history.add(pendingUserMessage);
            exchange.removeProperty(PENDING_USER_MESSAGE);
        }
    }

    private void updateConversationHistory(
            Exchange exchange, ChatCompletionCreateParams params,
            ChatCompletion response) {
        OpenAIConfiguration config = getEndpoint().getConfiguration();
        if (!config.isConversationMemory()) {
            return;
        }

        List<ChatCompletionMessageParam> history = exchange.getProperty(
                config.getConversationHistoryProperty(),
                List.class);

        if (history == null) {
            history = new ArrayList<>();
        }

        appendPendingUserMessageToHistory(exchange, history);

        // Add assistant response to history
        String assistantContent = response.choices().get(0).message().content().orElse("");
        ChatCompletionMessageParam assistantMessage = ChatCompletionMessageParam.ofAssistant(
                ChatCompletionAssistantMessageParam.builder()
                        .content(ChatCompletionAssistantMessageParam.Content.ofText(assistantContent))
                        .build());

        history.add(assistantMessage);
        exchange.setProperty(config.getConversationHistoryProperty(), history);
    }

    private void updateConversationHistory(
            Exchange exchange, List<ChatCompletionMessageParam> agenticMessages,
            ChatCompletion response) {
        OpenAIConfiguration config = getEndpoint().getConfiguration();
        if (!config.isConversationMemory()) {
            return;
        }

        List<ChatCompletionMessageParam> history = exchange.getProperty(
                config.getConversationHistoryProperty(),
                List.class);

        if (history == null) {
            history = new ArrayList<>();
        }

        appendPendingUserMessageToHistory(exchange, history);

        // Add all intermediate agentic messages (assistant+toolCalls, tool responses)
        history.addAll(agenticMessages);

        // Add final assistant response
        String assistantContent = response.choices().get(0).message().content().orElse("");
        ChatCompletionMessageParam assistantMessage = ChatCompletionMessageParam.ofAssistant(
                ChatCompletionAssistantMessageParam.builder()
                        .content(ChatCompletionAssistantMessageParam.Content.ofText(assistantContent))
                        .build());
        history.add(assistantMessage);

        exchange.setProperty(config.getConversationHistoryProperty(), history);
        LOG.debug("Updated conversation history with {} agentic messages + final response, total entries: {}",
                agenticMessages.size(), history.size());
    }

    private void updateConversationHistory(
            Exchange exchange, List<ChatCompletionMessageParam> agenticMessages,
            String directResult) {
        OpenAIConfiguration config = getEndpoint().getConfiguration();
        if (!config.isConversationMemory()) {
            return;
        }

        List<ChatCompletionMessageParam> history = exchange.getProperty(
                config.getConversationHistoryProperty(),
                List.class);

        if (history == null) {
            history = new ArrayList<>();
        }

        appendPendingUserMessageToHistory(exchange, history);

        // Add all intermediate agentic messages
        history.addAll(agenticMessages);

        // Add a synthetic assistant message with the direct tool result
        ChatCompletionMessageParam assistantMessage = ChatCompletionMessageParam.ofAssistant(
                ChatCompletionAssistantMessageParam.builder()
                        .content(ChatCompletionAssistantMessageParam.Content.ofText(directResult))
                        .build());
        history.add(assistantMessage);

        exchange.setProperty(config.getConversationHistoryProperty(), history);
        LOG.debug("Updated conversation history with {} agentic messages + returnDirect result, total entries: {}",
                agenticMessages.size(), history.size());
    }

    private ResponseFormatJsonSchema.JsonSchema.Schema buildSchemaFromJson(String jsonSchemaString) throws Exception {
        Map<String, Object> root = OBJECT_MAPPER.readValue(jsonSchemaString, Map.class);
        if (root == null) {
            throw new IllegalArgumentException("JSON schema string parsed to null");
        }
        if (!(root instanceof Map)) {
            throw new IllegalArgumentException("JSON schema must be a JSON object at the root");
        }
        ResponseFormatJsonSchema.JsonSchema.Schema.Builder sb = ResponseFormatJsonSchema.JsonSchema.Schema.builder();
        for (Map.Entry<String, Object> e : root.entrySet()) {
            sb.putAdditionalProperty(e.getKey(), JsonValue.from(e.getValue()));
        }
        return sb.build();
    }

    private void applyAdditionalBodyProperties(ChatCompletionCreateParams.Builder paramsBuilder, OpenAIConfiguration config) {
        Map<String, Object> additional = config.getAdditionalBodyProperty();
        if (additional == null || additional.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Object> e : additional.entrySet()) {
            String key = e.getKey();
            Object rawValue = e.getValue();
            Object valueToUse = rawValue;
            if (rawValue instanceof String s) {
                valueToUse = parseJsonOrString(s);
            }

            paramsBuilder.putAdditionalBodyProperty(key, JsonValue.from((Object) valueToUse));
        }
    }

    private Object parseJsonOrString(String value) {
        try {
            return OBJECT_MAPPER.readValue(value, Object.class);
        } catch (Exception e) {
            // treat as literal string
            return value;
        }
    }

    private void extractReasoningContent(Exchange exchange, ChatCompletionMessage message) {
        Map<String, JsonValue> additional = message._additionalProperties();
        JsonValue reasoningValue = additional.get("reasoning_content");
        if (reasoningValue != null) {
            String reasoning = (String) reasoningValue.asString().orElse(null);
            if (reasoning != null && !reasoning.isEmpty()) {
                exchange.getMessage().setHeader(OpenAIConstants.REASONING_CONTENT, reasoning);
            }
        }
    }

    private void extractAdditionalResponseHeaders(Exchange exchange, ChatCompletionMessage message) {
        OpenAIConfiguration config = getEndpoint().getConfiguration();
        Map<String, Object> mapping = config.getAdditionalResponseHeader();
        if (mapping == null || mapping.isEmpty()) {
            return;
        }

        Map<String, JsonValue> additional = message._additionalProperties();
        for (Map.Entry<String, Object> entry : mapping.entrySet()) {
            String responseField = entry.getKey();
            String headerName = String.valueOf(entry.getValue());
            JsonValue value = additional.get(responseField);
            if (value != null) {
                String strValue = (String) value.asString().orElse(null);
                if (strValue != null) {
                    exchange.getMessage().setHeader(headerName, strValue);
                } else {
                    exchange.getMessage().setHeader(headerName, value.toString());
                }
            }
        }
    }

    private String processThinkingContent(Exchange exchange, String content, OpenAIConfiguration config) {
        Boolean strip = resolveParameter(
                exchange.getIn(), OpenAIConstants.STRIP_THINKING, config.isStripThinking(), Boolean.class);
        if (!Boolean.TRUE.equals(strip)) {
            return content;
        }
        Matcher matcher = THINK_PATTERN.matcher(content);
        if (matcher.find()) {
            String thinking = matcher.group(1).trim();
            if (!thinking.isEmpty()) {
                exchange.getMessage().setHeader(OpenAIConstants.THINKING_CONTENT, thinking);
            }
            return matcher.replaceFirst("").trim();
        }
        return content;
    }

    private <T> T resolveParameter(Message message, String headerName, T defaultValue, Class<T> type) {
        T headerValue = message.getHeader(headerName, type);
        return ObjectHelper.isNotEmpty(headerValue) ? headerValue : defaultValue;
    }

}
