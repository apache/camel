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
import java.util.ArrayList;
import java.util.List;

import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.Embedding;
import com.openai.models.embeddings.EmbeddingCreateParams;
import com.openai.models.embeddings.EmbeddingCreateParams.EncodingFormat;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.util.ObjectHelper;

/**
 * OpenAI producer for generating text embeddings.
 */
public class OpenAIEmbeddingsProducer extends DefaultAsyncProducer {

    public OpenAIEmbeddingsProducer(OpenAIEndpoint endpoint) {
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

        String model = resolveParameter(in, OpenAIConstants.EMBEDDING_MODEL,
                config.getEmbeddingModel(), String.class);

        if (model == null) {
            throw new IllegalArgumentException("Embedding model must be specified via embeddingModel parameter");
        }

        Integer dimensions = resolveParameter(in, OpenAIConstants.EMBEDDING_DIMENSIONS,
                config.getDimensions(), Integer.class);

        // Get input texts
        List<String> inputs = extractInputs(in);
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("No input text provided for embedding");
        }

        // Build request
        EmbeddingCreateParams.Builder paramsBuilder = EmbeddingCreateParams.builder()
                .model(model);

        if (inputs.size() == 1) {
            paramsBuilder.input(inputs.get(0));
        } else {
            paramsBuilder.inputOfArrayOfStrings(inputs);
        }

        if (dimensions != null) {
            paramsBuilder.dimensions(dimensions.longValue());
        }

        if (ObjectHelper.isNotEmpty(config.getEncodingFormat())) {
            paramsBuilder.encodingFormat(EncodingFormat.of(config.getEncodingFormat()));
        }

        EmbeddingCreateParams params = paramsBuilder.build();

        // Execute request
        CreateEmbeddingResponse response = getEndpoint().getClient()
                .embeddings().create(params);

        // Extract embeddings
        List<List<Float>> embeddings = new ArrayList<>();
        for (Embedding embedding : response.data()) {
            embeddings.add(embedding.embedding());
        }

        // Set output body (metadata is exposed via headers)
        Message out = exchange.getMessage();
        if (inputs.size() == 1) {
            out.setBody(embeddings.isEmpty() ? List.of() : embeddings.get(0));
            out.setHeader(OpenAIConstants.ORIGINAL_TEXT, inputs.get(0));
        } else {
            out.setBody(embeddings);
            out.setHeader(OpenAIConstants.ORIGINAL_TEXT, inputs);
        }

        setResponseHeaders(out, response, embeddings);
        calculateSimilarityIfRequested(exchange, embeddings);
    }

    @SuppressWarnings("unchecked")
    private List<String> extractInputs(Message in) throws Exception {
        Object body = in.getBody();
        List<String> inputs = new ArrayList<>();

        if (body instanceof String text) {
            inputs.add(text);
        } else if (body instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String s) {
                    inputs.add(s);
                } else {
                    inputs.add(String.valueOf(item));
                }
            }
        } else if (body instanceof File file) {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            inputs.add(content);
        } else if (body != null) {
            inputs.add(in.getBody(String.class));
        }

        return inputs;
    }

    private void setResponseHeaders(Message message, CreateEmbeddingResponse response, List<List<Float>> embeddings) {
        if (response != null) {
            message.setHeader(OpenAIConstants.EMBEDDING_RESPONSE_MODEL, response.model());
            if (response.usage() != null) {
                message.setHeader(OpenAIConstants.PROMPT_TOKENS, (int) response.usage().promptTokens());
                message.setHeader(OpenAIConstants.TOTAL_TOKENS, (int) response.usage().totalTokens());
            }
        }
        message.setHeader(OpenAIConstants.EMBEDDING_COUNT, embeddings.size());
        int dimensions = embeddings.isEmpty() || embeddings.get(0) == null ? 0 : embeddings.get(0).size();
        message.setHeader(OpenAIConstants.EMBEDDING_VECTOR_SIZE, dimensions);
    }

    @SuppressWarnings("unchecked")
    private void calculateSimilarityIfRequested(Exchange exchange, List<List<Float>> embeddings) {
        List<Float> reference = exchange.getMessage()
                .getHeader(OpenAIConstants.REFERENCE_EMBEDDING, List.class);

        if (reference != null && !embeddings.isEmpty() && embeddings.get(0) != null) {
            double similarity = SimilarityUtils.cosineSimilarity(
                    reference, embeddings.get(0));
            exchange.getMessage().setHeader(OpenAIConstants.SIMILARITY_SCORE, similarity);
        }
    }

    private <T> T resolveParameter(
            Message message, String headerName,
            T defaultValue, Class<T> type) {
        T headerValue = message.getHeader(headerName, type);
        return ObjectHelper.isNotEmpty(headerValue) ? headerValue : defaultValue;
    }
}
