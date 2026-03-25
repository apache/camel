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
package org.apache.camel.component.google.vertexai.integration;

import java.util.Arrays;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.vertexai.GoogleVertexAIConstants;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the new Vertex AI operations: streaming, image generation, embeddings, multimodal, and
 * streamRawPredict.
 *
 * To run: mvn test -Dtest=GoogleVertexAINewOperationsIT \ -Dgoogle.vertexai.serviceAccountKey=/path/to/key.json \
 * -Dgoogle.vertexai.project=my-project
 */
@EnabledIfSystemProperty(named = "google.vertexai.serviceAccountKey", matches = ".*",
                         disabledReason = "System property google.vertexai.serviceAccountKey not provided")
public class GoogleVertexAINewOperationsIT extends CamelTestSupport {

    final String serviceAccountKeyFile = System.getProperty("google.vertexai.serviceAccountKey");
    final String project = System.getProperty("google.vertexai.project");
    final String location = System.getProperty("google.vertexai.location", "us-central1");

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:streaming")
                        .to("google-vertexai:" + project + ":" + location + ":gemini-2.0-flash"
                            + "?serviceAccountKey=file:" + serviceAccountKeyFile
                            + "&operation=generateChatStreaming&maxOutputTokens=100");

                from("direct:image")
                        .to("google-vertexai:" + project + ":" + location + ":imagen-3.0-generate-002"
                            + "?serviceAccountKey=file:" + serviceAccountKeyFile
                            + "&operation=generateImage");

                from("direct:embeddings")
                        .to("google-vertexai:" + project + ":" + location + ":text-embedding-005"
                            + "?serviceAccountKey=file:" + serviceAccountKeyFile
                            + "&operation=generateEmbeddings");

                from("direct:multimodal")
                        .to("google-vertexai:" + project + ":" + location + ":gemini-2.0-flash"
                            + "?serviceAccountKey=file:" + serviceAccountKeyFile
                            + "&operation=generateMultimodal&maxOutputTokens=100");
            }
        };
    }

    @Test
    public void testGenerateChatStreaming() {
        Exchange exchange = template.request("direct:streaming", e -> {
            e.getMessage().setBody("What is 2+2? Answer in one word.");
        });

        String response = exchange.getMessage().getBody(String.class);
        assertNotNull(response, "Streaming response should not be null");
        assertFalse(response.isEmpty(), "Streaming response should not be empty");

        Integer chunkCount = exchange.getMessage().getHeader(GoogleVertexAIConstants.CHUNK_COUNT, Integer.class);
        assertNotNull(chunkCount, "Chunk count header should be set");
        assertTrue(chunkCount > 0, "Should have received at least one chunk");
    }

    @Test
    public void testGenerateImage() {
        Exchange exchange = template.request("direct:image", e -> {
            e.getMessage().setBody("A simple red circle on a white background");
            e.getMessage().setHeader(GoogleVertexAIConstants.IMAGE_NUMBER_OF_IMAGES, 1);
        });

        List<?> images = exchange.getMessage().getBody(List.class);
        assertNotNull(images, "Generated images should not be null");
        assertFalse(images.isEmpty(), "Should have generated at least one image");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGenerateEmbeddings() {
        Exchange exchange = template.request("direct:embeddings", e -> {
            e.getMessage().setBody("Hello world");
        });

        List<List<Float>> embeddings = exchange.getMessage().getBody(List.class);
        assertNotNull(embeddings, "Embeddings should not be null");
        assertFalse(embeddings.isEmpty(), "Should have at least one embedding");
        assertFalse(embeddings.get(0).isEmpty(), "Embedding vector should not be empty");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGenerateEmbeddingsBatch() {
        Exchange exchange = template.request("direct:embeddings", e -> {
            e.getMessage().setBody(Arrays.asList("Hello world", "Goodbye world"));
        });

        List<List<Float>> embeddings = exchange.getMessage().getBody(List.class);
        assertNotNull(embeddings, "Embeddings should not be null");
        assertTrue(embeddings.size() >= 2, "Should have embeddings for each input text");
    }

    @Test
    public void testGenerateMultimodal() {
        Exchange exchange = template.request("direct:multimodal", e -> {
            e.getMessage().setHeader(GoogleVertexAIConstants.PROMPT, "Describe what you see in one sentence.");
            e.getMessage().setHeader(GoogleVertexAIConstants.MEDIA_GCS_URI,
                    "gs://cloud-samples-data/generative-ai/image/scones.jpg");
            e.getMessage().setHeader(GoogleVertexAIConstants.MEDIA_MIME_TYPE, "image/jpeg");
        });

        String response = exchange.getMessage().getBody(String.class);
        assertNotNull(response, "Multimodal response should not be null");
        assertFalse(response.isEmpty(), "Multimodal response should not be empty");
    }
}
