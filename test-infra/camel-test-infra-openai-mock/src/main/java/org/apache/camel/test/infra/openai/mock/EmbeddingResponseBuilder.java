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
package org.apache.camel.test.infra.openai.mock;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Random;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Builder class for creating OpenAI embeddings API mock responses.
 */
public class EmbeddingResponseBuilder {
    private final ObjectMapper objectMapper;

    public EmbeddingResponseBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String createEmbeddingResponse(String input, EmbeddingExpectation expectation) throws Exception {
        return createBatchEmbeddingResponse(List.of(input), List.of(expectation), "float");
    }

    public String createBatchEmbeddingResponse(
            List<String> inputs, List<EmbeddingExpectation> expectations, String encodingFormat)
            throws Exception {
        int totalTokens = 0;
        String model = expectations.isEmpty() ? "camel-embedding" : expectations.get(0).getModel();
        boolean useBase64 = "base64".equals(encodingFormat);

        if (useBase64) {
            List<EmbeddingDataBase64> embeddingDataList = new ArrayList<>();
            for (int i = 0; i < inputs.size(); i++) {
                String input = inputs.get(i);
                EmbeddingExpectation expectation = expectations.get(i);

                List<Float> embedding;
                if (expectation.hasExplicitVector()) {
                    embedding = expectation.getEmbeddingVector();
                } else {
                    embedding = generateDeterministicEmbedding(input, expectation.getEmbeddingSize());
                }

                String base64Embedding = encodeEmbeddingToBase64(embedding);
                embeddingDataList.add(new EmbeddingDataBase64("embedding", base64Embedding, i));
                totalTokens += input.length();
            }

            Usage usage = new Usage(totalTokens, totalTokens);
            EmbeddingResponseBase64 response = new EmbeddingResponseBase64("list", embeddingDataList, model, usage);
            return objectMapper.writeValueAsString(response);
        } else {
            List<EmbeddingData> embeddingDataList = new ArrayList<>();
            for (int i = 0; i < inputs.size(); i++) {
                String input = inputs.get(i);
                EmbeddingExpectation expectation = expectations.get(i);

                List<Float> embedding;
                if (expectation.hasExplicitVector()) {
                    embedding = expectation.getEmbeddingVector();
                } else {
                    embedding = generateDeterministicEmbedding(input, expectation.getEmbeddingSize());
                }

                embeddingDataList.add(new EmbeddingData("embedding", embedding, i));
                totalTokens += input.length();
            }

            Usage usage = new Usage(totalTokens, totalTokens);
            EmbeddingResponse response = new EmbeddingResponse("list", embeddingDataList, model, usage);
            return objectMapper.writeValueAsString(response);
        }
    }

    public String createEmbeddingResponse(List<Float> embedding, String model, int promptTokens) throws Exception {
        EmbeddingData embeddingData = new EmbeddingData("embedding", embedding, 0);
        Usage usage = new Usage(promptTokens, promptTokens);
        EmbeddingResponse response = new EmbeddingResponse("list", List.of(embeddingData), model, usage);

        return objectMapper.writeValueAsString(response);
    }

    /**
     * Generates a deterministic embedding vector based on the input string. Uses the input's hashCode as a seed for
     * reproducible tests.
     *
     * @param  input the input text
     * @param  size  the desired embedding vector size
     * @return       a list of floats representing the embedding
     */
    public List<Float> generateDeterministicEmbedding(String input, int size) {
        Random random = new Random(input.hashCode());
        List<Float> embedding = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            // Generate values between -1.0 and 1.0, typical for embeddings
            embedding.add(random.nextFloat() * 2 - 1);
        }
        return embedding;
    }

    /**
     * Encodes a list of floats to base64 format (little-endian float32 bytes).
     */
    private String encodeEmbeddingToBase64(List<Float> embedding) {
        ByteBuffer buffer = ByteBuffer.allocate(embedding.size() * 4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (Float value : embedding) {
            buffer.putFloat(value);
        }
        return Base64.getEncoder().encodeToString(buffer.array());
    }

    record EmbeddingData(
            String object,
            List<Float> embedding,
            int index) {
    }

    record EmbeddingDataBase64(
            String object,
            String embedding,
            int index) {
    }

    record Usage(
            @JsonProperty("prompt_tokens") int promptTokens,
            @JsonProperty("total_tokens") int totalTokens) {
    }

    record EmbeddingResponse(
            String object,
            List<EmbeddingData> data,
            String model,
            Usage usage) {
    }

    record EmbeddingResponseBase64(
            String object,
            List<EmbeddingDataBase64> data,
            String model,
            Usage usage) {
    }
}
