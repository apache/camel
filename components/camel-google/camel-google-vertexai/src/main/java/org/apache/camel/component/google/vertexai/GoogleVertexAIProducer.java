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
package org.apache.camel.component.google.vertexai;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.HttpBody;
import com.google.cloud.aiplatform.v1.PredictionServiceClient;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.protobuf.ByteString;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogleVertexAIProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleVertexAIProducer.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final GoogleVertexAIEndpoint endpoint;

    public GoogleVertexAIProducer(GoogleVertexAIEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        GoogleVertexAIOperations operation = determineOperation(exchange);

        if (ObjectHelper.isEmpty(operation)) {
            operation = endpoint.getConfiguration().getOperation();
        }

        if (ObjectHelper.isEmpty(operation)) {
            // Default to generateText if no operation specified
            operation = GoogleVertexAIOperations.generateText;
        }

        switch (operation) {
            case generateText:
                generateText(exchange);
                break;
            case generateChat:
                generateChat(exchange);
                break;
            case generateChatStreaming:
                generateChatStreaming(exchange);
                break;
            case generateImage:
                generateImage(exchange);
                break;
            case generateEmbeddings:
                generateEmbeddings(exchange);
                break;
            case generateCode:
                generateCode(exchange);
                break;
            case generateMultimodal:
                generateMultimodal(exchange);
                break;
            case rawPredict:
                rawPredict(exchange);
                break;
            case streamRawPredict:
                streamRawPredict(exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation: " + operation);
        }
    }

    private GoogleVertexAIOperations determineOperation(Exchange exchange) {
        GoogleVertexAIOperations operation
                = exchange.getIn().getHeader(GoogleVertexAIConstants.OPERATION, GoogleVertexAIOperations.class);
        if (operation == null) {
            String operationString = exchange.getIn().getHeader(GoogleVertexAIConstants.OPERATION, String.class);
            if (operationString != null) {
                operation = GoogleVertexAIOperations.valueOf(operationString);
            }
        }
        return operation;
    }

    private void generateText(Exchange exchange) throws Exception {
        String prompt = getPrompt(exchange);
        GenerateContentConfig config = buildConfig(exchange);

        Client client = endpoint.getClient();
        String modelId = endpoint.getConfiguration().getModelId();

        LOG.debug("Generating text with model: {} and prompt: {}", modelId, prompt);

        GenerateContentResponse response = client.models.generateContent(modelId, prompt, config);

        String responseText = response.text();

        Message message = getMessageForResponse(exchange);
        message.setBody(responseText);

        // Set metadata headers
        setMetadataHeaders(exchange, response);
    }

    private void generateChat(Exchange exchange) throws Exception {
        // For chat, we use the same API but could extend with conversation history
        generateText(exchange);
    }

    private void generateChatStreaming(Exchange exchange) throws Exception {
        // TODO: Implement streaming support using client.models.generateContentStream
        throw new UnsupportedOperationException("Streaming is not yet implemented");
    }

    private void generateImage(Exchange exchange) throws Exception {
        // TODO: Implement image generation using Imagen models
        throw new UnsupportedOperationException("Image generation is not yet implemented");
    }

    private void generateEmbeddings(Exchange exchange) throws Exception {
        // TODO: Implement embeddings generation
        throw new UnsupportedOperationException("Embeddings generation is not yet implemented");
    }

    private void generateCode(Exchange exchange) throws Exception {
        // For code generation, we use the same API with code-specific prompts
        generateText(exchange);
    }

    private void generateMultimodal(Exchange exchange) throws Exception {
        // TODO: Implement multimodal generation with images/video input
        throw new UnsupportedOperationException("Multimodal generation is not yet implemented");
    }

    /**
     * Sends a raw prediction request to partner models (Claude, Llama, Mistral) or custom deployed models.
     * <p>
     * The request body can be:
     * <ul>
     * <li>A JSON String - sent as-is to the model</li>
     * <li>A Map - converted to JSON and sent to the model</li>
     * <li>For Anthropic models, if the body is just a prompt string, it will be wrapped in the required format</li>
     * </ul>
     */
    private void rawPredict(Exchange exchange) throws Exception {
        GoogleVertexAIConfiguration config = endpoint.getConfiguration();
        PredictionServiceClient predictionClient = endpoint.getPredictionServiceClient();

        String publisher = config.getPublisher();
        if (ObjectHelper.isEmpty(publisher)) {
            throw new IllegalArgumentException("Publisher must be specified for rawPredict operation");
        }

        // Resolve location - "global" is mapped to a regional endpoint for gRPC
        String location = config.getLocation();
        if ("global".equalsIgnoreCase(location)) {
            location = "us-east5"; // Default regional endpoint for partner models
        }

        String endpointName = PredictionServiceClientFactory.buildPublisherModelEndpoint(
                config.getProjectId(),
                location,
                publisher,
                config.getModelId());

        LOG.debug("Sending rawPredict request to endpoint: {}", endpointName);

        // Build the request body
        String requestJson = buildRawPredictRequestBody(exchange, config);
        LOG.debug("Request body: {}", requestJson);

        HttpBody httpBody = HttpBody.newBuilder()
                .setData(ByteString.copyFromUtf8(requestJson))
                .setContentType("application/json")
                .build();

        // Execute the rawPredict call
        HttpBody responseBody = predictionClient.rawPredict(endpointName, httpBody);

        // Parse and set the response
        String responseJson = responseBody.getData().toString(StandardCharsets.UTF_8);
        LOG.debug("Response: {}", responseJson);

        Message message = getMessageForResponse(exchange);

        // Try to extract text content from Anthropic response format
        if ("anthropic".equals(publisher)) {
            String textContent = extractAnthropicTextContent(responseJson);
            message.setBody(textContent);
            message.setHeader(GoogleVertexAIConstants.RAW_RESPONSE, responseJson);
        } else {
            message.setBody(responseJson);
        }

        message.setHeader(GoogleVertexAIConstants.PUBLISHER, publisher);
        message.setHeader(GoogleVertexAIConstants.MODEL_ID, config.getModelId());
    }

    /**
     * Sends a streaming raw prediction request to partner models.
     */
    private void streamRawPredict(Exchange exchange) throws Exception {
        // TODO: Implement streaming rawPredict using streamRawPredict API
        throw new UnsupportedOperationException("Streaming rawPredict is not yet implemented");
    }

    /**
     * Builds the JSON request body for rawPredict based on the exchange body and publisher.
     */
    @SuppressWarnings("unchecked")
    private String buildRawPredictRequestBody(Exchange exchange, GoogleVertexAIConfiguration config) throws Exception {
        Object body = exchange.getIn().getBody();
        String publisher = config.getPublisher();

        // If body is already a JSON string, use it as-is (but add anthropic_version if needed)
        if (body instanceof String) {
            String bodyStr = (String) body;
            if (bodyStr.trim().startsWith("{")) {
                // It's JSON - add anthropic_version if Anthropic publisher and not already present
                if ("anthropic".equals(publisher) && !bodyStr.contains("anthropic_version")) {
                    Map<String, Object> jsonMap = OBJECT_MAPPER.readValue(bodyStr, Map.class);
                    jsonMap.put("anthropic_version", config.getAnthropicVersion());
                    return OBJECT_MAPPER.writeValueAsString(jsonMap);
                }
                return bodyStr;
            } else {
                // It's a plain text prompt - wrap it appropriately
                return buildPromptRequest(bodyStr, config, publisher);
            }
        }

        // If body is a Map, convert to JSON (adding anthropic_version if needed)
        if (body instanceof Map) {
            Map<String, Object> bodyMap = new HashMap<>((Map<String, Object>) body);
            if ("anthropic".equals(publisher) && !bodyMap.containsKey("anthropic_version")) {
                bodyMap.put("anthropic_version", config.getAnthropicVersion());
            }
            return OBJECT_MAPPER.writeValueAsString(bodyMap);
        }

        throw new IllegalArgumentException(
                "Request body must be a JSON String, Map, or plain text prompt. Got: " + body.getClass().getName());
    }

    /**
     * Builds a request body from a plain text prompt based on the publisher format.
     */
    private String buildPromptRequest(String prompt, GoogleVertexAIConfiguration config, String publisher)
            throws Exception {
        Map<String, Object> request = new HashMap<>();

        if ("anthropic".equals(publisher)) {
            // Anthropic Claude format
            request.put("anthropic_version", config.getAnthropicVersion());
            request.put("max_tokens", config.getMaxOutputTokens());
            request.put("messages", new Object[] {
                    Map.of("role", "user", "content", prompt)
            });

            // Optional parameters
            if (config.getTemperature() != null) {
                request.put("temperature", config.getTemperature());
            }
            if (config.getTopP() != null) {
                request.put("top_p", config.getTopP());
            }
            if (config.getTopK() != null) {
                request.put("top_k", config.getTopK());
            }
        } else if ("meta".equals(publisher)) {
            // Meta Llama format
            request.put("instances", new Object[] {
                    Map.of("prompt", prompt)
            });
            request.put("parameters", Map.of(
                    "maxOutputTokens", config.getMaxOutputTokens(),
                    "temperature", config.getTemperature(),
                    "topP", config.getTopP(),
                    "topK", config.getTopK()));
        } else if ("mistralai".equals(publisher)) {
            // Mistral format
            request.put("messages", new Object[] {
                    Map.of("role", "user", "content", prompt)
            });
            request.put("max_tokens", config.getMaxOutputTokens());
            if (config.getTemperature() != null) {
                request.put("temperature", config.getTemperature());
            }
        } else {
            // Generic format
            request.put("instances", new Object[] {
                    Map.of("content", prompt)
            });
            request.put("parameters", Map.of(
                    "maxOutputTokens", config.getMaxOutputTokens(),
                    "temperature", config.getTemperature()));
        }

        return OBJECT_MAPPER.writeValueAsString(request);
    }

    /**
     * Extracts the text content from an Anthropic Claude response.
     */
    @SuppressWarnings("unchecked")
    private String extractAnthropicTextContent(String responseJson) {
        try {
            Map<String, Object> response = OBJECT_MAPPER.readValue(responseJson, Map.class);
            Object content = response.get("content");
            if (content instanceof java.util.List) {
                java.util.List<Map<String, Object>> contentList = (java.util.List<Map<String, Object>>) content;
                StringBuilder sb = new StringBuilder();
                for (Map<String, Object> block : contentList) {
                    if ("text".equals(block.get("type"))) {
                        sb.append(block.get("text"));
                    }
                }
                return sb.toString();
            }
        } catch (Exception e) {
            LOG.warn("Failed to extract text from Anthropic response, returning raw JSON", e);
        }
        return responseJson;
    }

    private String getPrompt(Exchange exchange) {
        String prompt = exchange.getIn().getHeader(GoogleVertexAIConstants.PROMPT, String.class);
        if (prompt == null) {
            prompt = exchange.getIn().getBody(String.class);
        }
        if (prompt == null) {
            throw new IllegalArgumentException("Prompt must be provided in body or header");
        }
        return prompt;
    }

    private GenerateContentConfig buildConfig(Exchange exchange) {
        GoogleVertexAIConfiguration config = endpoint.getConfiguration();

        GenerateContentConfig.Builder configBuilder = GenerateContentConfig.builder();

        // Get from headers or configuration
        Float temperature = exchange.getIn().getHeader(GoogleVertexAIConstants.TEMPERATURE, Float.class);
        if (temperature == null) {
            temperature = config.getTemperature();
        }
        if (temperature != null) {
            configBuilder.temperature(temperature);
        }

        Float topP = exchange.getIn().getHeader(GoogleVertexAIConstants.TOP_P, Float.class);
        if (topP == null) {
            topP = config.getTopP();
        }
        if (topP != null) {
            configBuilder.topP(topP);
        }

        Integer topK = exchange.getIn().getHeader(GoogleVertexAIConstants.TOP_K, Integer.class);
        if (topK == null) {
            topK = config.getTopK();
        }
        if (topK != null) {
            configBuilder.topK(topK.floatValue());
        }

        Integer maxOutputTokens = exchange.getIn().getHeader(GoogleVertexAIConstants.MAX_OUTPUT_TOKENS, Integer.class);
        if (maxOutputTokens == null) {
            maxOutputTokens = config.getMaxOutputTokens();
        }
        if (maxOutputTokens != null) {
            configBuilder.maxOutputTokens(maxOutputTokens);
        }

        Integer candidateCount = exchange.getIn().getHeader(GoogleVertexAIConstants.CANDIDATE_COUNT, Integer.class);
        if (candidateCount == null) {
            candidateCount = config.getCandidateCount();
        }
        if (candidateCount != null) {
            configBuilder.candidateCount(candidateCount);
        }

        return configBuilder.build();
    }

    private void setMetadataHeaders(Exchange exchange, GenerateContentResponse response) {
        Message message = getMessageForResponse(exchange);

        // Set usage metadata if available
        response.usageMetadata().ifPresent(usageMetadata -> {
            usageMetadata.promptTokenCount().ifPresent(
                    count -> message.setHeader(GoogleVertexAIConstants.PROMPT_TOKEN_COUNT, count));
            usageMetadata.candidatesTokenCount().ifPresent(
                    count -> message.setHeader(GoogleVertexAIConstants.CANDIDATES_TOKEN_COUNT, count));
            usageMetadata.totalTokenCount().ifPresent(
                    count -> message.setHeader(GoogleVertexAIConstants.TOTAL_TOKEN_COUNT, count));
        });

        // Set finish reason if available from first candidate
        response.candidates().ifPresent(candidates -> {
            if (!candidates.isEmpty()) {
                candidates.get(0).finishReason().ifPresent(
                        reason -> message.setHeader(GoogleVertexAIConstants.FINISH_REASON, reason.toString()));
            }
        });
    }

    private Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }
}
