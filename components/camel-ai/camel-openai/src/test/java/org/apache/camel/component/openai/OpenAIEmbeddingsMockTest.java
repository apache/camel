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

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.openai.mock.OpenAIMock;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OpenAIEmbeddingsMockTest extends CamelTestSupport {

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            .whenEmbedding("What is Apache Camel?")
            .replyWithEmbedding(new float[] { 0.1f, 0.2f, 0.3f, 0.4f })
            .end()
            .whenEmbedding("Hello world")
            .replyWithEmbedding(4)
            .end()
            .whenEmbedding("Goodbye world")
            .replyWithEmbedding(4)
            .end()
            .whenEmbedding("Apache Camel is an integration framework")
            .replyWithEmbedding(new float[] { 0.1f, 0.2f, 0.3f, 0.4f })
            .end()
            .whenEmbedding("Something completely different")
            .replyWithEmbedding(new float[] { -0.5f, -0.5f, -0.5f, -0.5f })
            .build();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:embedding")
                        .to("openai:embeddings?embeddingModel=text-embedding-ada-002&apiKey=dummy&baseUrl="
                            + openAIMock.getBaseUrl() + "/v1");
            }
        };
    }

    @Test
    void testSingleEmbedding() {
        Exchange result = template.request("direct:embedding", e -> e.getIn().setBody("What is Apache Camel?"));

        assertNotNull(result);
        Object body = result.getMessage().getBody();
        assertNotNull(body);

        // Single input returns List<Float>
        assertTrue(body instanceof List, "Body should be a List");
        @SuppressWarnings("unchecked")
        List<Float> embedding = (List<Float>) body;
        assertEquals(4, embedding.size());
        assertEquals(0.1f, embedding.get(0), 0.001);
        assertEquals(0.2f, embedding.get(1), 0.001);
        assertEquals(0.3f, embedding.get(2), 0.001);
        assertEquals(0.4f, embedding.get(3), 0.001);

        // Verify headers
        assertEquals(1, result.getMessage().getHeader(OpenAIConstants.EMBEDDING_COUNT));
        assertEquals(4, result.getMessage().getHeader(OpenAIConstants.EMBEDDING_VECTOR_SIZE));
        assertEquals("What is Apache Camel?", result.getMessage().getHeader(OpenAIConstants.ORIGINAL_TEXT));
        assertNotNull(result.getMessage().getHeader(OpenAIConstants.EMBEDDING_RESPONSE_MODEL));
    }

    @Test
    void testBatchEmbeddings() {
        List<String> inputs = List.of("Hello world", "Goodbye world");

        Exchange result = template.request("direct:embedding", e -> e.getIn().setBody(inputs));

        assertNotNull(result);
        Object body = result.getMessage().getBody();
        assertNotNull(body);

        // Batch input returns List<List<Float>>
        assertTrue(body instanceof List, "Body should be a List");
        @SuppressWarnings("unchecked")
        List<List<Float>> embeddings = (List<List<Float>>) body;
        assertEquals(2, embeddings.size());
        assertEquals(4, embeddings.get(0).size());
        assertEquals(4, embeddings.get(1).size());

        // Verify headers
        assertEquals(2, result.getMessage().getHeader(OpenAIConstants.EMBEDDING_COUNT));
        assertEquals(4, result.getMessage().getHeader(OpenAIConstants.EMBEDDING_VECTOR_SIZE));

        // Original text header should be a list for batch
        @SuppressWarnings("unchecked")
        List<String> originalTexts = (List<String>) result.getMessage().getHeader(OpenAIConstants.ORIGINAL_TEXT);
        assertEquals(2, originalTexts.size());
        assertEquals("Hello world", originalTexts.get(0));
        assertEquals("Goodbye world", originalTexts.get(1));
    }

    @Test
    void testSimilarityCalculation() {
        // First, get a reference embedding
        Exchange referenceResult = template.request("direct:embedding",
                e -> e.getIn().setBody("Apache Camel is an integration framework"));

        @SuppressWarnings("unchecked")
        List<Float> referenceEmbedding = (List<Float>) referenceResult.getMessage().getBody();
        assertNotNull(referenceEmbedding);

        // Now test with similar content - should have high similarity (same vector)
        Exchange similarResult = template.request("direct:embedding", e -> {
            e.getIn().setBody("What is Apache Camel?");
            e.getIn().setHeader(OpenAIConstants.REFERENCE_EMBEDDING, referenceEmbedding);
        });

        Double similarityScore = similarResult.getMessage().getHeader(OpenAIConstants.SIMILARITY_SCORE, Double.class);
        assertNotNull(similarityScore, "Similarity score should be calculated");
        assertEquals(1.0, similarityScore, 0.001, "Identical vectors should have similarity of 1.0");

        // Test with different content - should have lower/negative similarity
        Exchange differentResult = template.request("direct:embedding", e -> {
            e.getIn().setBody("Something completely different");
            e.getIn().setHeader(OpenAIConstants.REFERENCE_EMBEDDING, referenceEmbedding);
        });

        Double differentScore = differentResult.getMessage().getHeader(OpenAIConstants.SIMILARITY_SCORE, Double.class);
        assertNotNull(differentScore, "Similarity score should be calculated");
        assertTrue(differentScore < 0, "Different vectors should have negative similarity");
    }

    @Test
    void testEmbeddingsOutputHeaders() {
        Exchange result = template.request("direct:embedding", e -> e.getIn().setBody("What is Apache Camel?"));

        assertNotNull(result);

        // Verify all output headers are present
        assertNotNull(result.getMessage().getHeader(OpenAIConstants.EMBEDDING_RESPONSE_MODEL),
                "EMBEDDING_RESPONSE_MODEL header should be present");
        assertNotNull(result.getMessage().getHeader(OpenAIConstants.EMBEDDING_COUNT),
                "EMBEDDING_COUNT header should be present");
        assertNotNull(result.getMessage().getHeader(OpenAIConstants.EMBEDDING_VECTOR_SIZE),
                "EMBEDDING_VECTOR_SIZE header should be present");
        assertNotNull(result.getMessage().getHeader(OpenAIConstants.ORIGINAL_TEXT),
                "ORIGINAL_TEXT header should be present");
        assertNotNull(result.getMessage().getHeader(OpenAIConstants.PROMPT_TOKENS),
                "PROMPT_TOKENS header should be present");
        assertNotNull(result.getMessage().getHeader(OpenAIConstants.TOTAL_TOKENS),
                "TOTAL_TOKENS header should be present");

        // Verify header values
        assertEquals(1, result.getMessage().getHeader(OpenAIConstants.EMBEDDING_COUNT));
        assertEquals(4, result.getMessage().getHeader(OpenAIConstants.EMBEDDING_VECTOR_SIZE));
        assertTrue(result.getMessage().getHeader(OpenAIConstants.PROMPT_TOKENS, Integer.class) > 0);
        assertTrue(result.getMessage().getHeader(OpenAIConstants.TOTAL_TOKENS, Integer.class) > 0);
    }
}
