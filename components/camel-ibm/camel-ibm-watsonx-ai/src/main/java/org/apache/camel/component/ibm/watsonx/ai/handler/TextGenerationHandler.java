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
package org.apache.camel.component.ibm.watsonx.ai.handler;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.ibm.watsonx.ai.textgeneration.TextGenerationParameters;
import com.ibm.watsonx.ai.textgeneration.TextGenerationResponse;
import com.ibm.watsonx.ai.textgeneration.TextGenerationService;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiConstants;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiEndpoint;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for text generation operations.
 */
public class TextGenerationHandler extends AbstractWatsonxAiHandler {

    private static final Logger LOG = LoggerFactory.getLogger(TextGenerationHandler.class);

    public TextGenerationHandler(WatsonxAiEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public WatsonxAiOperationResponse handle(Exchange exchange, WatsonxAiOperations operation) throws Exception {
        switch (operation) {
            case textGeneration:
                return processTextGeneration(exchange);
            case textGenerationStreaming:
                return processTextGenerationStreaming(exchange);
            default:
                throw new IllegalArgumentException("Unsupported operation: " + operation);
        }
    }

    @Override
    public WatsonxAiOperations[] getSupportedOperations() {
        return new WatsonxAiOperations[] {
                WatsonxAiOperations.textGeneration,
                WatsonxAiOperations.textGenerationStreaming
        };
    }

    private WatsonxAiOperationResponse processTextGeneration(Exchange exchange) {
        // Get input from body or header
        String input = getInput(exchange);

        // Build parameters from configuration and headers
        TextGenerationParameters.Builder paramsBuilder = TextGenerationParameters.builder();
        applyTextGenerationParameters(paramsBuilder, exchange);

        // Call the service
        TextGenerationService service = endpoint.getTextGenerationService();
        TextGenerationResponse response = service.generate(input, paramsBuilder.build());

        // Build response headers
        Map<String, Object> headers = new HashMap<>();
        headers.put(WatsonxAiConstants.GENERATED_TEXT, response.toText());
        if (!response.results().isEmpty()) {
            TextGenerationResponse.Result result = response.results().get(0);
            headers.put(WatsonxAiConstants.INPUT_TOKEN_COUNT, result.inputTokenCount());
            headers.put(WatsonxAiConstants.OUTPUT_TOKEN_COUNT, result.generatedTokenCount());
            headers.put(WatsonxAiConstants.STOP_REASON, result.stopReason());
        }

        return WatsonxAiOperationResponse.create(response.toText(), headers);
    }

    @SuppressWarnings("unchecked")
    private WatsonxAiOperationResponse processTextGenerationStreaming(Exchange exchange) throws Exception {
        Message in = exchange.getIn();

        // Get input from body or header
        String input = getInput(exchange);

        // Get the stream consumer from header
        Consumer<String> consumer = in.getHeader(WatsonxAiConstants.STREAM_CONSUMER, Consumer.class);
        if (consumer == null) {
            consumer = endpoint.getStreamEndpointConsumer();
        }
        if (consumer == null) {
            throw new IllegalArgumentException(
                    "Stream consumer must be provided via header '" + WatsonxAiConstants.STREAM_CONSUMER
                                               + "' or the streamEndpoint option for streaming operations");
        }
        Consumer<String> streamConsumer = consumer;

        // Build parameters from configuration and headers
        TextGenerationParameters.Builder paramsBuilder = TextGenerationParameters.builder();
        applyTextGenerationParameters(paramsBuilder, exchange);

        // Call the streaming service
        TextGenerationService service = endpoint.getTextGenerationService();
        StringBuilder fullResponse = new StringBuilder();
        final TextGenerationResponse[] finalResponse = new TextGenerationResponse[1];
        final Throwable[] errorHolder = new Throwable[1];

        com.ibm.watsonx.ai.textgeneration.TextGenerationHandler handler
                = new com.ibm.watsonx.ai.textgeneration.TextGenerationHandler() {
                    @Override
                    public void onPartialResponse(String partialResponse) {
                        if (partialResponse != null && !partialResponse.isEmpty()) {
                            fullResponse.append(partialResponse);
                            streamConsumer.accept(partialResponse);
                        }
                    }

                    @Override
                    public void onCompleteResponse(TextGenerationResponse completeResponse) {
                        finalResponse[0] = completeResponse;
                    }

                    @Override
                    public void onError(Throwable error) {
                        errorHolder[0] = error;
                        LOG.debug("Error during streaming text generation", error);
                    }

                    @Override
                    public boolean failOnFirstError() {
                        return true;
                    }
                };

        // Wait for completion, at most the time limit, and stop the stream otherwise
        awaitStream(service.generateStreaming(input, paramsBuilder.build(), handler));

        // Check for errors
        if (errorHolder[0] != null) {
            throw new RuntimeCamelException("Error during streaming text generation", errorHolder[0]);
        }

        // Build response headers
        Map<String, Object> headers = new HashMap<>();
        headers.put(WatsonxAiConstants.GENERATED_TEXT, fullResponse.toString());

        // Set additional headers from complete response if available
        if (finalResponse[0] != null && !finalResponse[0].results().isEmpty()) {
            TextGenerationResponse.Result result = finalResponse[0].results().get(0);
            headers.put(WatsonxAiConstants.INPUT_TOKEN_COUNT, result.inputTokenCount());
            headers.put(WatsonxAiConstants.OUTPUT_TOKEN_COUNT, result.generatedTokenCount());
            headers.put(WatsonxAiConstants.STOP_REASON, result.stopReason());
        }

        return WatsonxAiOperationResponse.create(fullResponse.toString(), headers);
    }
}
