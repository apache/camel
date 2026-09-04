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

import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseCreateParams;
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
import org.apache.camel.util.ObjectHelper;

/**
 * OpenAI producer for the Responses API (non-streaming).
 */
public class OpenAIResponsesProducer extends DefaultAsyncProducer {

    private Class<?> outputClassResolved;

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

        String instructions = in.getHeader(OpenAIConstants.SYSTEM_MESSAGE, String.class);
        if ((instructions == null || instructions.isEmpty()) && ObjectHelper.isNotEmpty(config.getSystemMessage())) {
            instructions = config.getSystemMessage();
        }

        OpenAIResponsesInputBuilder.InputSpec inputSpec = OpenAIResponsesInputBuilder.buildInput(in, config);

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

        OpenAIResponsesSupport.applyBuiltinTools(paramsBuilder, config.getBuiltinTools(),
                config.getFileSearchVectorStoreIds());
        OpenAIResponsesSupport.applyHostedMcpTools(paramsBuilder, config.getHostedMcpTools());
        OpenAIResponsesSupport.applyAdditionalBodyProperties(paramsBuilder, config.getAdditionalBodyProperty());

        Class<?> responseClass = resolveOutputClass(in, outputClass);
        if (responseClass != null) {
            processStructured(exchange, config, paramsBuilder, responseClass, model);
            return;
        }
        if (ObjectHelper.isNotEmpty(jsonSchema)) {
            OpenAIResponsesSupport.applyJsonSchemaTextFormat(paramsBuilder, jsonSchema);
        }

        ResponseCreateParams params = paramsBuilder.build();
        Response response = createResponse(exchange, model, params);
        finishExchange(exchange, config, response, OpenAIResponsesSupport.extractAssistantText(response));
    }

    private void processStructured(
            Exchange exchange, OpenAIConfiguration config, ResponseCreateParams.Builder paramsBuilder,
            Class<?> responseClass, String model)
            throws Exception {
        StructuredResponseCreateParams<?> structuredParams = paramsBuilder.text(responseClass).build();
        Response raw = createStructuredResponse(exchange, model, structuredParams);
        finishExchange(exchange, config, raw, OpenAIResponsesSupport.extractAssistantText(raw));
    }

    private Response createResponse(Exchange exchange, String model, ResponseCreateParams params) throws Exception {
        GenAiObservationContext observationContext = GenAiObservationContext.builder()
                .operationName(GenAiOperationName.CHAT)
                .system("openai")
                .requestModel(model)
                .componentScheme("openai")
                .build();
        GenAiObservation observation = GenAiObservability.start(exchange, observationContext);
        try {
            Response response = getEndpoint().getClient().responses().create(params);
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

    private Response createStructuredResponse(
            Exchange exchange, String model, StructuredResponseCreateParams<?> structuredParams)
            throws Exception {
        GenAiObservationContext observationContext = GenAiObservationContext.builder()
                .operationName(GenAiOperationName.CHAT)
                .system("openai")
                .requestModel(model)
                .componentScheme("openai")
                .build();
        GenAiObservation observation = GenAiObservability.start(exchange, observationContext);
        try {
            StructuredResponse<?> structured = getEndpoint().getClient().responses().create(structuredParams);
            Response raw = structured.rawResponse();
            recordResponseSuccess(observation, raw);
            return raw;
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
                        toTokenCount(usage.inputTokens()),
                        toTokenCount(usage.outputTokens()),
                        finishReason,
                        response.model().toString())),
                () -> observation.recordSuccess(GenAiUsage.of(null, null, finishReason, response.model().toString())));
    }

    private static Integer toTokenCount(long tokens) {
        return Math.toIntExact(tokens);
    }

    private void finishExchange(Exchange exchange, OpenAIConfiguration config, Response response, String body) {
        if (config.isStoreFullResponse()) {
            exchange.setProperty(OpenAIConstants.RESPONSES_RESPONSE, response);
        }
        Message out = exchange.getMessage();
        out.setBody(body);
        setResponseHeaders(out, response);
    }

    private void setResponseHeaders(Message message, Response response) {
        message.setHeader(OpenAIConstants.RESPONSE_ID, response.id());
        message.setHeader(OpenAIConstants.RESPONSE_MODEL, response.model().toString());
        OpenAIResponsesSupport.extractFinishStatus(response)
                .ifPresent(status -> message.setHeader(OpenAIConstants.FINISH_REASON, mapFinishReason(status)));
        response.usage().ifPresent(usage -> {
            message.setHeader(OpenAIConstants.PROMPT_TOKENS, usage.inputTokens());
            message.setHeader(OpenAIConstants.COMPLETION_TOKENS, usage.outputTokens());
            message.setHeader(OpenAIConstants.TOTAL_TOKENS, usage.totalTokens());
        });
        response.previousResponseId()
                .ifPresent(id -> message.setHeader(OpenAIConstants.PREVIOUS_RESPONSE_ID, id));
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
