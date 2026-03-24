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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.HttpBody;
import com.google.api.gax.rpc.ServerStream;
import com.google.cloud.aiplatform.v1.PredictionServiceClient;
import com.google.cloud.aiplatform.v1.StreamRawPredictRequest;
import com.google.genai.Client;
import com.google.genai.ResponseStream;
import com.google.genai.types.Content;
import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateImagesConfig;
import com.google.genai.types.GenerateImagesResponse;
import com.google.genai.types.Image;
import com.google.genai.types.Part;
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
        String prompt = getPrompt(exchange);
        GenerateContentConfig config = buildConfig(exchange);

        Client client = endpoint.getClient();
        String modelId = endpoint.getConfiguration().getModelId();

        LOG.debug("Generating streaming content with model: {} and prompt: {}", modelId, prompt);

        try (ResponseStream<GenerateContentResponse> stream
                = client.models.generateContentStream(modelId, prompt, config)) {

            StringBuilder fullText = new StringBuilder();
            int chunkCount = 0;
            GenerateContentResponse lastResponse = null;

            for (GenerateContentResponse chunk : stream) {
                chunkCount++;
                lastResponse = chunk;
                String chunkText = chunk.text();
                if (chunkText != null) {
                    fullText.append(chunkText);
                }
            }

            Message message = getMessageForResponse(exchange);
            message.setBody(fullText.toString());
            message.setHeader(GoogleVertexAIConstants.CHUNK_COUNT, chunkCount);

            if (lastResponse != null) {
                setMetadataHeaders(exchange, lastResponse);
            }
        }
    }

    private void generateImage(Exchange exchange) throws Exception {
        String prompt = getPrompt(exchange);

        Client client = endpoint.getClient();
        String modelId = endpoint.getConfiguration().getModelId();

        LOG.debug("Generating image with model: {} and prompt: {}", modelId, prompt);

        GenerateImagesConfig.Builder configBuilder = GenerateImagesConfig.builder();

        Integer numberOfImages = exchange.getIn().getHeader(GoogleVertexAIConstants.IMAGE_NUMBER_OF_IMAGES, Integer.class);
        if (numberOfImages != null) {
            configBuilder.numberOfImages(numberOfImages);
        }

        String aspectRatio = exchange.getIn().getHeader(GoogleVertexAIConstants.IMAGE_ASPECT_RATIO, String.class);
        if (aspectRatio != null) {
            configBuilder.aspectRatio(aspectRatio);
        }

        GenerateImagesResponse response = client.models.generateImages(modelId, prompt, configBuilder.build());

        Message message = getMessageForResponse(exchange);

        List<Image> images = response.images();
        message.setBody(images);
        message.setHeader(GoogleVertexAIConstants.GENERATED_IMAGES, images);
        message.setHeader(GoogleVertexAIConstants.MODEL_ID, modelId);
    }

    private void generateEmbeddings(Exchange exchange) throws Exception {
        Client client = endpoint.getClient();
        String modelId = endpoint.getConfiguration().getModelId();

        LOG.debug("Generating embeddings with model: {}", modelId);

        EmbedContentConfig.Builder configBuilder = EmbedContentConfig.builder();

        String taskType = exchange.getIn().getHeader(GoogleVertexAIConstants.EMBEDDING_TASK_TYPE, String.class);
        if (taskType != null) {
            configBuilder.taskType(taskType);
        }

        Integer outputDimensionality
                = exchange.getIn().getHeader(GoogleVertexAIConstants.EMBEDDING_OUTPUT_DIMENSIONALITY, Integer.class);
        if (outputDimensionality != null) {
            configBuilder.outputDimensionality(outputDimensionality);
        }

        EmbedContentConfig embedConfig = configBuilder.build();
        Object body = exchange.getIn().getBody();
        EmbedContentResponse response;

        if (body instanceof List<?> list) {
            // Batch embeddings — convert all elements to String
            List<String> texts = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item instanceof String s) {
                    texts.add(s);
                } else {
                    texts.add(String.valueOf(item));
                }
            }
            response = client.models.embedContent(modelId, texts, embedConfig);
        } else {
            // Single text embedding
            String text = exchange.getIn().getBody(String.class);
            if (text == null) {
                throw new IllegalArgumentException("Text must be provided in body for embeddings generation");
            }
            response = client.models.embedContent(modelId, text, embedConfig);
        }

        Message message = getMessageForResponse(exchange);

        List<List<Float>> embeddingValues = new ArrayList<>();
        response.embeddings().ifPresent(embeddings -> {
            for (ContentEmbedding embedding : embeddings) {
                embedding.values().ifPresent(embeddingValues::add);
            }
        });

        message.setBody(embeddingValues);
        message.setHeader(GoogleVertexAIConstants.MODEL_ID, modelId);
    }

    private void generateCode(Exchange exchange) throws Exception {
        // For code generation, we use the same API with code-specific prompts
        generateText(exchange);
    }

    private void generateMultimodal(Exchange exchange) throws Exception {
        GenerateContentConfig config = buildConfig(exchange);

        Client client = endpoint.getClient();
        String modelId = endpoint.getConfiguration().getModelId();

        LOG.debug("Generating multimodal content with model: {}", modelId);

        List<Part> parts = new ArrayList<>();

        // Add text prompt as a part
        String prompt = exchange.getIn().getHeader(GoogleVertexAIConstants.PROMPT, String.class);
        if (prompt != null) {
            parts.add(Part.fromText(prompt));
        }

        // Add inline media data from header if provided
        byte[] mediaData = exchange.getIn().getHeader(GoogleVertexAIConstants.MEDIA_DATA, byte[].class);
        String mediaMimeType = exchange.getIn().getHeader(GoogleVertexAIConstants.MEDIA_MIME_TYPE, String.class);
        if (mediaData != null && mediaMimeType != null) {
            parts.add(Part.fromBytes(mediaData, mediaMimeType));
        }

        // Add GCS URI media from header if provided
        String gcsUri = exchange.getIn().getHeader(GoogleVertexAIConstants.MEDIA_GCS_URI, String.class);
        if (gcsUri != null) {
            String mimeType = mediaMimeType != null ? mediaMimeType : "application/octet-stream";
            parts.add(Part.fromUri(gcsUri, mimeType));
        }

        // Fall back to body content when no media headers are set
        if (mediaData == null && gcsUri == null) {
            Object body = exchange.getIn().getBody();
            if (body instanceof byte[] bodyBytes && mediaMimeType != null) {
                parts.add(Part.fromBytes(bodyBytes, mediaMimeType));
            } else if (body instanceof String bodyStr && prompt == null) {
                parts.add(Part.fromText(bodyStr));
            }
        }

        if (parts.isEmpty()) {
            throw new IllegalArgumentException(
                    "Multimodal generation requires at least one input part (text prompt, media data, or GCS URI)");
        }

        Content content = Content.fromParts(parts.toArray(new Part[0]));
        GenerateContentResponse response = client.models.generateContent(modelId, content, config);

        String responseText = response.text();

        Message message = getMessageForResponse(exchange);
        message.setBody(responseText);

        setMetadataHeaders(exchange, response);
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

        // "global" is not supported by the gRPC-based Prediction Service; partner models
        // (Anthropic, Meta, Mistral) are available in specific regions only, with us-east5
        // being the most commonly supported across all partners.
        String location = config.getLocation();
        if ("global".equalsIgnoreCase(location)) {
            location = "us-east5";
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
        GoogleVertexAIConfiguration config = endpoint.getConfiguration();
        PredictionServiceClient predictionClient = endpoint.getPredictionServiceClient();

        String publisher = config.getPublisher();
        if (ObjectHelper.isEmpty(publisher)) {
            throw new IllegalArgumentException("Publisher must be specified for streamRawPredict operation");
        }

        // "global" is not supported by the gRPC-based Prediction Service; partner models
        // (Anthropic, Meta, Mistral) are available in specific regions only, with us-east5
        // being the most commonly supported across all partners.
        String location = config.getLocation();
        if ("global".equalsIgnoreCase(location)) {
            location = "us-east5";
        }

        String endpointName = PredictionServiceClientFactory.buildPublisherModelEndpoint(
                config.getProjectId(),
                location,
                publisher,
                config.getModelId());

        LOG.debug("Sending streamRawPredict request to endpoint: {}", endpointName);

        String requestJson = buildRawPredictRequestBody(exchange, config);

        // Add stream: true for Anthropic
        if ("anthropic".equals(publisher) && !requestJson.contains("\"stream\"")) {
            Map<String, Object> jsonMap = OBJECT_MAPPER.readValue(requestJson, Map.class);
            jsonMap.put("stream", true);
            requestJson = OBJECT_MAPPER.writeValueAsString(jsonMap);
        }

        LOG.debug("Request body: {}", requestJson);

        HttpBody httpBody = HttpBody.newBuilder()
                .setData(ByteString.copyFromUtf8(requestJson))
                .setContentType("application/json")
                .build();

        StreamRawPredictRequest request = StreamRawPredictRequest.newBuilder()
                .setEndpoint(endpointName)
                .setHttpBody(httpBody)
                .build();

        ServerStream<HttpBody> stream = predictionClient.streamRawPredictCallable().call(request);

        StringBuilder fullResponse = new StringBuilder();
        int chunkCount = 0;

        try {
            for (HttpBody responseChunk : stream) {
                chunkCount++;
                String chunkData = responseChunk.getData().toString(StandardCharsets.UTF_8);
                fullResponse.append(chunkData);
            }
        } finally {
            stream.cancel();
        }

        Message message = getMessageForResponse(exchange);

        String responseJson = fullResponse.toString();

        if ("anthropic".equals(publisher)) {
            String textContent = extractAnthropicStreamingTextContent(responseJson);
            message.setBody(textContent);
            message.setHeader(GoogleVertexAIConstants.RAW_RESPONSE, responseJson);
        } else {
            message.setBody(responseJson);
        }

        message.setHeader(GoogleVertexAIConstants.PUBLISHER, publisher);
        message.setHeader(GoogleVertexAIConstants.MODEL_ID, config.getModelId());
        message.setHeader(GoogleVertexAIConstants.CHUNK_COUNT, chunkCount);
    }

    /**
     * Builds the JSON request body for rawPredict based on the exchange body and publisher.
     */
    @SuppressWarnings("unchecked")
    private String buildRawPredictRequestBody(Exchange exchange, GoogleVertexAIConfiguration config) throws Exception {
        Object body = exchange.getIn().getBody();
        String publisher = config.getPublisher();

        // If body is already a JSON string, use it as-is (but add anthropic_version if needed)
        if (body instanceof String bodyStr) {
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

    /**
     * Extracts text content from Anthropic SSE streaming response. Each SSE event is on a separate line prefixed with
     * "data: ". We look for content_block_delta events containing text deltas.
     */
    @SuppressWarnings("unchecked")
    private String extractAnthropicStreamingTextContent(String sseResponse) {
        StringBuilder text = new StringBuilder();
        for (String line : sseResponse.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("data: ")) {
                String jsonStr = trimmed.substring(6);
                try {
                    Map<String, Object> event = OBJECT_MAPPER.readValue(jsonStr, Map.class);
                    if ("content_block_delta".equals(event.get("type"))) {
                        Map<String, Object> delta = (Map<String, Object>) event.get("delta");
                        if (delta != null && "text_delta".equals(delta.get("type"))) {
                            Object textValue = delta.get("text");
                            if (textValue != null) {
                                text.append(textValue);
                            }
                        }
                    }
                } catch (Exception e) {
                    LOG.trace("Skipping unparseable SSE line: {}", jsonStr, e);
                }
            }
        }
        return text.length() > 0 ? text.toString() : sseResponse;
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
