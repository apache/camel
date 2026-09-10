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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseInputItem;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.ai.observability.GenAiErrorSupport;
import org.apache.camel.component.ai.observability.GenAiObservability;
import org.apache.camel.component.ai.observability.GenAiObservation;
import org.apache.camel.component.ai.observability.GenAiObservationContext;
import org.apache.camel.component.ai.observability.GenAiOperationName;
import org.apache.camel.component.ai.observability.GenAiUsage;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.function.ThrowingSupplier;

/**
 * OpenAI producer for the Responses API (non-streaming).
 */
public class OpenAIResponsesProducer extends DefaultAsyncProducer {

    private Class<?> outputClassResolved;
    private McpToolCallExecutor toolCallExecutor;

    public OpenAIResponsesProducer(OpenAIEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public OpenAIEndpoint getEndpoint() {
        return (OpenAIEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        OpenAIConfiguration config = getEndpoint().getConfiguration();

        toolCallExecutor = new McpToolCallExecutor(getEndpoint());
        ServiceHelper.startService(toolCallExecutor);

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

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(toolCallExecutor);
        super.doStop();
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

        Boolean streaming = resolveParameter(in, OpenAIConstants.STREAMING, config.isStreaming(), Boolean.class);
        if (Boolean.TRUE.equals(streaming)) {
            throw new IllegalArgumentException(
                    "Streaming is not supported for openai:responses. Use openai:chat-completion with streaming=true instead.");
        }

        String model = resolveParameter(in, OpenAIConstants.MODEL, config.getModel(), String.class);
        if (model == null) {
            throw new IllegalArgumentException("Model must be specified via model parameter or CamelOpenAIModel header");
        }

        Double temperature = resolveParameter(in, OpenAIConstants.TEMPERATURE, config.getTemperature(), Double.class);
        Double topP = resolveParameter(in, OpenAIConstants.TOP_P, config.getTopP(), Double.class);
        Integer maxTokens = resolveParameter(in, OpenAIConstants.MAX_TOKENS, config.getMaxTokens(), Integer.class);
        String outputClass = resolveParameter(in, OpenAIConstants.OUTPUT_CLASS, config.getOutputClass(), String.class);
        String jsonSchema = resolveParameter(in, OpenAIConstants.JSON_SCHEMA, config.getJsonSchema(), String.class);
        String previousResponseId = resolveParameter(in, OpenAIConstants.PREVIOUS_RESPONSE_ID,
                config.getPreviousResponseId(), String.class);
        if (ObjectHelper.isEmpty(previousResponseId) && config.isConversationMemory()) {
            previousResponseId = previousResponseIdFromMemory(exchange, config);
        }

        String instructions = in.getHeader(OpenAIConstants.SYSTEM_MESSAGE, String.class);
        if ((instructions == null || instructions.isEmpty()) && ObjectHelper.isNotEmpty(config.getSystemMessage())) {
            instructions = config.getSystemMessage();
        }
        String developerMessage = in.getHeader(OpenAIConstants.DEVELOPER_MESSAGE, String.class);
        if ((developerMessage == null || developerMessage.isEmpty())
                && ObjectHelper.isNotEmpty(config.getDeveloperMessage())) {
            developerMessage = config.getDeveloperMessage();
        }

        OpenAIResponsesInputBuilder.InputSpec inputSpec = OpenAIResponsesInputBuilder.buildInput(in, config);
        if (ObjectHelper.isNotEmpty(developerMessage)) {
            inputSpec = inputSpec.withDeveloperMessage(developerMessage);
        }

        ResponseCreateParams.Builder paramsBuilder = ResponseCreateParams.builder().model(model);
        if (inputSpec.isPlainText()) {
            paramsBuilder.input(inputSpec.plainText());
        } else {
            paramsBuilder.inputOfResponse(inputSpec.structuredItems());
        }
        if (ObjectHelper.isNotEmpty(instructions)) {
            paramsBuilder.instructions(instructions);
        }
        if (temperature != null) {
            paramsBuilder.temperature(temperature);
        }
        if (topP != null) {
            paramsBuilder.topP(topP);
        }
        if (maxTokens != null) {
            paramsBuilder.maxOutputTokens(maxTokens.longValue());
        }
        if (ObjectHelper.isNotEmpty(previousResponseId)) {
            paramsBuilder.previousResponseId(previousResponseId);
        }
        String conversationId = resolveParameter(in, OpenAIConstants.CONVERSATION_ID, config.getConversationId(),
                String.class);
        if (ObjectHelper.isNotEmpty(conversationId)) {
            paramsBuilder.conversation(conversationId);
        }

        OpenAIResponsesSupport.applyBuiltinTools(paramsBuilder, config.getBuiltinTools(),
                config.getFileSearchVectorStoreIds());
        OpenAIResponsesSupport.applyHostedMcpTools(paramsBuilder, config.getHostedMcpTools());
        OpenAIResponsesSupport.applyAdditionalBodyProperties(paramsBuilder, config.getAdditionalBodyProperty());

        // the MCP servers and route tools of the endpoint are sent as function tools
        List<ChatCompletionFunctionTool> tools = getEndpoint().getMcpToolState().tools();
        tools.forEach(tool -> paramsBuilder.addTool(OpenAIResponsesSupport.toFunctionTool(tool)));

        ResponseCreateParams params;
        Class<?> responseClass = resolveOutputClass(in, outputClass);
        if (responseClass != null) {
            params = paramsBuilder.text(responseClass).build().rawParams();
        } else {
            if (ObjectHelper.isNotEmpty(jsonSchema)) {
                OpenAIResponsesSupport.applyJsonSchemaTextFormat(paramsBuilder, jsonSchema);
            }
            params = paramsBuilder.build();
        }

        if (!tools.isEmpty() && config.isAutoToolExecution()) {
            processToolLoop(exchange, config, params, inputSpec.items(), model);
            return;
        }

        Response response = createResponse(exchange, model, params);
        List<ResponseFunctionToolCall> functionCalls = OpenAIResponsesSupport.extractFunctionCalls(response);
        // with autoToolExecution=false the route handles the function calls requested by the model
        Object body = functionCalls.isEmpty() ? OpenAIResponsesSupport.extractAssistantText(response) : functionCalls;
        finishExchange(exchange, config, response, body);
    }

    /**
     * Runs the tool loop: the function calls requested by the model are executed on the MCP servers and route tools of
     * the endpoint, and their results are sent back with the conversation so far until the model answers.
     */
    private void processToolLoop(
            Exchange exchange, OpenAIConfiguration config, ResponseCreateParams params, List<ResponseInputItem> input,
            String model)
            throws Exception {
        List<ResponseInputItem> conversation = new ArrayList<>(input);
        List<String> toolCallsLog = new ArrayList<>();
        int iteration = 0;

        while (true) {
            Response response = createResponse(exchange, model, params.toBuilder().inputOfResponse(conversation).build());
            List<ResponseFunctionToolCall> functionCalls = OpenAIResponsesSupport.extractFunctionCalls(response);
            if (functionCalls.isEmpty()) {
                finishExchange(exchange, config, response, OpenAIResponsesSupport.extractAssistantText(response));
                setToolHeaders(exchange.getMessage(), iteration, toolCallsLog, false);
                return;
            }
            if (iteration == config.getMaxToolIterations()) {
                throw new IllegalStateException(
                        "Max tool iterations (%d) exceeded. Tools called: %s"
                                .formatted(config.getMaxToolIterations(), toolCallsLog));
            }
            iteration++;

            // the function calls, and the reasoning that led to them, must precede their results
            conversation.addAll(OpenAIResponsesSupport.toInputItems(response));
            functionCalls.forEach(call -> toolCallsLog.add(call.name()));
            List<McpToolCallExecutor.ToolResult> results
                    = toolCallExecutor.execute(OpenAIResponsesSupport.toChatToolCalls(functionCalls));

            if (results.stream().allMatch(McpToolCallExecutor.ToolResult::returnDirect)) {
                // the results are not sent back, so conversation memory is not moved to this response
                Message out = exchange.getMessage();
                out.setBody(results.stream()
                        .map(McpToolCallExecutor.ToolResult::content)
                        .collect(Collectors.joining("\n")));
                setResponseHeaders(out, response);
                setToolHeaders(out, iteration, toolCallsLog, true);
                return;
            }
            for (McpToolCallExecutor.ToolResult result : results) {
                conversation.add(ResponseInputItem.ofFunctionCallOutput(ResponseInputItem.FunctionCallOutput.builder()
                        .callId(result.toolCallId())
                        .output(result.content())
                        .build()));
            }
        }
    }

    private Response createResponse(Exchange exchange, String model, ResponseCreateParams params) throws Exception {
        return observedCall(exchange, model, () -> getEndpoint().getClient().responses().create(params));
    }

    private Response observedCall(Exchange exchange, String model, ThrowingSupplier<Response, Exception> call)
            throws Exception {
        GenAiObservationContext observationContext = GenAiObservationContext.builder()
                .operationName(GenAiOperationName.CHAT)
                .system("openai")
                .requestModel(model)
                .componentScheme("openai")
                .build();
        GenAiObservation observation = GenAiObservability.start(exchange, observationContext);
        try {
            Response response = call.get();
            recordResponseSuccess(observation, response);
            return response;
        } catch (Exception e) {
            GenAiErrorSupport.apply(exchange, e);
            observation.recordError(e);
            throw e;
        } finally {
            observation.close();
        }
    }

    private static void recordResponseSuccess(GenAiObservation observation, Response response) {
        String finishReason = OpenAIResponsesSupport.extractFinishStatus(response)
                .map(OpenAIResponsesProducer::mapFinishReason)
                .orElse(null);
        response.usage().ifPresentOrElse(
                usage -> observation.recordSuccess(GenAiUsage.of(
                        usage.inputTokens(),
                        usage.outputTokens(),
                        finishReason,
                        OpenAIResponsesSupport.modelName(response.model()))),
                () -> observation.recordSuccess(
                        GenAiUsage.of((Long) null, null, finishReason, OpenAIResponsesSupport.modelName(response.model()))));
    }

    private void finishExchange(Exchange exchange, OpenAIConfiguration config, Response response, Object body)
            throws Exception {
        if (config.isStoreFullResponse()) {
            exchange.setProperty(OpenAIConstants.RESPONSES_RESPONSE, response);
        }
        OpenAIResponsesSupport.requireNoPendingMcpApprovals(exchange, response);
        if (config.isConversationMemory()) {
            // the conversation stays on the server, so the next call of the exchange only needs this response id
            exchange.setProperty(config.getConversationHistoryProperty(), response.id());
        }
        Message out = exchange.getMessage();
        out.setBody(body);
        setResponseHeaders(out, response);
    }

    /**
     * Returns the response id stored by conversation memory. The chat-completion operation stores its message history
     * under the same property, which is not a response id and is ignored.
     */
    private static String previousResponseIdFromMemory(Exchange exchange, OpenAIConfiguration config) {
        return exchange.getProperty(config.getConversationHistoryProperty()) instanceof String id ? id : null;
    }

    private void setResponseHeaders(Message message, Response response) {
        message.setHeader(OpenAIConstants.RESPONSE_ID, response.id());
        message.setHeader(OpenAIConstants.RESPONSE_MODEL, OpenAIResponsesSupport.modelName(response.model()));
        OpenAIResponsesSupport.extractFinishStatus(response)
                .ifPresent(status -> message.setHeader(OpenAIConstants.FINISH_REASON, mapFinishReason(status)));
        response.usage().ifPresent(usage -> {
            message.setHeader(OpenAIConstants.PROMPT_TOKENS, usage.inputTokens());
            message.setHeader(OpenAIConstants.COMPLETION_TOKENS, usage.outputTokens());
            message.setHeader(OpenAIConstants.TOTAL_TOKENS, usage.totalTokens());
        });
        response.previousResponseId()
                .ifPresent(id -> message.setHeader(OpenAIConstants.PREVIOUS_RESPONSE_ID, id));
        var annotations = OpenAIResponsesSupport.extractAnnotations(response);
        if (!annotations.isEmpty()) {
            message.setHeader(OpenAIConstants.RESPONSE_ANNOTATIONS, annotations);
        }
    }

    private static void setToolHeaders(Message message, int iterations, List<String> toolCalls, boolean returnDirect) {
        message.setHeader(OpenAIConstants.TOOL_ITERATIONS, iterations);
        message.setHeader(OpenAIConstants.MCP_TOOL_CALLS, toolCalls);
        message.setHeader(OpenAIConstants.MCP_RETURN_DIRECT, returnDirect);
    }

    private Class<?> resolveOutputClass(Message in, String outputClass) throws ClassNotFoundException {
        if (ObjectHelper.isNotEmpty(in.getHeader(OpenAIConstants.OUTPUT_CLASS, String.class))) {
            return getEndpoint().getCamelContext().getClassResolver()
                    .resolveMandatoryClass(in.getHeader(OpenAIConstants.OUTPUT_CLASS, String.class));
        }
        if (ObjectHelper.isNotEmpty(outputClass)) {
            return outputClassResolved;
        }
        return null;
    }

    private String resolveResourceContent(String property) {
        try (InputStream is = ResourceHelper.resolveResourceAsInputStream(getEndpoint().getCamelContext(), property)) {
            if (is != null) {
                return getEndpoint().getCamelContext().getTypeConverter().convertTo(String.class, is);
            }
        } catch (Exception e) {
            // treat as inline schema
        }
        return null;
    }

    private <T> T resolveParameter(Message message, String headerName, T defaultValue, Class<T> type) {
        T headerValue = message.getHeader(headerName, type);
        return ObjectHelper.isNotEmpty(headerValue) ? headerValue : defaultValue;
    }

    private static String mapFinishReason(String status) {
        if ("completed".equalsIgnoreCase(status)) {
            return "stop";
        }
        return status;
    }
}
