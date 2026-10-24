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
package org.apache.camel.component.langchain4j.agent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.tool.search.ToolSearchRequest;
import dev.langchain4j.service.tool.search.ToolSearchResult;
import org.apache.camel.component.ai.tool.AiToolSearch;
import org.apache.camel.component.ai.tool.AiToolSearchStrategy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CamelToolSearchStrategyTest {

    @Test
    void testEmbeddingCacheAndBatchingBehavior() {
        CountingEmbeddingModel embeddingModel = new CountingEmbeddingModel();
        CamelToolSearchStrategy strategy = new CamelToolSearchStrategy(
                AiToolSearchStrategy.VECTOR, 5, embeddingModel);

        // An invalid query should return an error without embedding any text.
        ToolSearchResult invalidResult = strategy.search(createRequest("{}"));

        assertThat(invalidResult.foundToolNames()).isEmpty();
        assertThat(invalidResult.toolResultMessageText())
                .contains(AiToolSearch.QUERY_ARGUMENT);
        assertThat(embeddingModel.embedAllCount()).isZero();
        assertThat(embeddingModel.embedCount()).isZero();

        // The first search should batch-embed both searchable tools.
        ToolSearchRequest validRequest = createRequest("{\"query\":\"weather\"}");
        ToolSearchResult firstResult = strategy.search(validRequest);

        assertThat(firstResult.foundToolNames()).isNotEmpty();
        assertThat(embeddingModel.embedAllCount()).isEqualTo(1);
        assertThat(embeddingModel.lastBatchSize()).isEqualTo(2);

        int initialEmbedAllCalls = embeddingModel.embedAllCount();
        int initialEmbedCalls = embeddingModel.embedCount();

        // Repeating the same search should reuse cached tool and query embeddings.
        ToolSearchResult secondResult = strategy.search(validRequest);

        assertThat(secondResult.foundToolNames())
                .isEqualTo(firstResult.foundToolNames());
        assertThat(embeddingModel.embedAllCount()).isEqualTo(initialEmbedAllCalls);
        assertThat(embeddingModel.embedCount()).isEqualTo(initialEmbedCalls);
    }

    @Test
    void testChangedToolDescriptionIsReembedded() {
        CountingEmbeddingModel embeddingModel = new CountingEmbeddingModel();
        CamelToolSearchStrategy strategy = new CamelToolSearchStrategy(
                AiToolSearchStrategy.VECTOR, 5, embeddingModel);

        String originalWeatherText = "get_weather\nGet weather for a city";
        String updatedWeatherText = "get_weather\nUpdated weather description";
        String forecastText = "get_forecast\nGet forecast for a city";

        strategy.search(createRequest("{\"query\":\"weather\"}"));

        assertThat(embeddingModel.embedAllCount()).isEqualTo(1);
        assertThat(embeddingModel.lastBatchSize()).isEqualTo(2);

        // Tool vectors were produced in a batch, not through embed(text).
        assertThat(embeddingModel.embedCountFor(originalWeatherText)).isZero();
        assertThat(embeddingModel.embedCountFor(forecastText)).isZero();

        strategy.search(createRequest(
                "{\"query\":\"weather\"}", "Updated weather description"));

        // Only the changed tool content should require a new batch embedding.
        assertThat(embeddingModel.embedAllCount()).isEqualTo(2);
        assertThat(embeddingModel.lastBatchSize()).isEqualTo(1);

        // Repeating the updated catalogue must reuse both tool embeddings.
        int batchCallsAfterChange = embeddingModel.embedAllCount();
        strategy.search(createRequest(
                "{\"query\":\"weather\"}", "Updated weather description"));

        assertThat(embeddingModel.embedAllCount()).isEqualTo(batchCallsAfterChange);
    }

    @Test
    void concurrentSearchesDoNotSerializeEmbeddingCalls() throws Exception {
        ConcurrentEmbeddingModel embeddingModel = new ConcurrentEmbeddingModel();
        CamelToolSearchStrategy strategy = new CamelToolSearchStrategy(
                AiToolSearchStrategy.VECTOR, 5, embeddingModel);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<ToolSearchResult> first = executor.submit(
                    () -> strategy.search(createRequest("{\"query\":\"weather\"}")));
            Future<ToolSearchResult> second = executor.submit(
                    () -> strategy.search(createRequest("{\"query\":\"forecast\"}")));

            boolean bothEnteredEmbedding = embeddingModel.awaitBothCalls();
            embeddingModel.releaseCalls();

            assertThat(first.get(5, TimeUnit.SECONDS).foundToolNames()).isNotEmpty();
            assertThat(second.get(5, TimeUnit.SECONDS).foundToolNames()).isNotEmpty();
            assertThat(bothEnteredEmbedding).isTrue();
            assertThat(embeddingModel.maxConcurrentBatchCalls()).isGreaterThan(1);
        } finally {
            embeddingModel.releaseCalls();
            executor.shutdownNow();
        }
    }

    private ToolSearchRequest createRequest(String arguments) {
        return createRequest(arguments, "Get weather for a city");
    }

    private ToolSearchRequest createRequest(String arguments, String weatherDescription) {
        ToolSpecification weatherTool = ToolSpecification.builder()
                .name("get_weather")
                .description(weatherDescription)
                .build();

        ToolSpecification forecastTool = ToolSpecification.builder()
                .name("get_forecast")
                .description("Get forecast for a city")
                .build();

        ToolExecutionRequest executionRequest = ToolExecutionRequest.builder()
                .name(AiToolSearch.TOOL_NAME)
                .arguments(arguments)
                .build();

        InvocationContext invocationContext = InvocationContext.builder()
                .invocationId(UUID.randomUUID())
                .interfaceName("CamelToolSearchStrategyTest")
                .methodName("testEmbeddingCacheAndBatchingBehavior")
                .methodArguments(List.of(arguments))
                .chatMemoryId("test")
                .invocationParameters(new InvocationParameters())
                .timestamp(Instant.now())
                .build();

        return ToolSearchRequest.builder()
                .searchableTools(List.of(weatherTool, forecastTool))
                .toolExecutionRequest(executionRequest)
                .invocationContext(invocationContext)
                .build();
    }

    private static class ConcurrentEmbeddingModel extends CountingEmbeddingModel {

        private final CountDownLatch bothCallsEntered = new CountDownLatch(2);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicInteger activeBatchCalls = new AtomicInteger();
        private final AtomicInteger maximumConcurrentBatchCalls = new AtomicInteger();

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
            int active = activeBatchCalls.incrementAndGet();
            maximumConcurrentBatchCalls.accumulateAndGet(active, Math::max);
            bothCallsEntered.countDown();

            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Timed out waiting for concurrent embedding call");
                }
                return super.embedAll(textSegments);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted during test embedding", e);
            } finally {
                activeBatchCalls.decrementAndGet();
            }
        }

        boolean awaitBothCalls() throws InterruptedException {
            return bothCallsEntered.await(2, TimeUnit.SECONDS);
        }

        void releaseCalls() {
            release.countDown();
        }

        int maxConcurrentBatchCalls() {
            return maximumConcurrentBatchCalls.get();
        }
    }

    private static class CountingEmbeddingModel implements EmbeddingModel {

        private final AtomicInteger embedCalls = new AtomicInteger();
        private final AtomicInteger embedAllCalls = new AtomicInteger();
        private final ConcurrentMap<String, AtomicInteger> textEmbedCounts = new ConcurrentHashMap<>();
        private volatile int lastBatchSize;

        @Override
        public Response<Embedding> embed(String text) {
            embedCalls.incrementAndGet();
            textEmbedCounts.computeIfAbsent(text, key -> new AtomicInteger()).incrementAndGet();
            return Response.from(new Embedding(vectorFor(text)));
        }

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
            embedAllCalls.incrementAndGet();
            lastBatchSize = textSegments.size();

            List<Embedding> embeddings = new ArrayList<>(textSegments.size());
            for (TextSegment segment : textSegments) {
                embeddings.add(new Embedding(vectorFor(segment.text())));
            }

            return Response.from(embeddings);
        }

        private static float[] vectorFor(String text) {
            try {
                byte[] hash = MessageDigest.getInstance("SHA-256")
                        .digest(text.getBytes(StandardCharsets.UTF_8));
                float[] vector = new float[8];
                for (int i = 0; i < vector.length; i++) {
                    vector[i] = ((hash[i] & 0xff) / 127.5f) - 1.0f;
                }
                return vector;
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 is unavailable", e);
            }
        }

        int embedCount() {
            return embedCalls.get();
        }

        int embedAllCount() {
            return embedAllCalls.get();
        }

        int lastBatchSize() {
            return lastBatchSize;
        }

        int embedCountFor(String text) {
            AtomicInteger count = textEmbedCounts.get(text);
            return count == null ? 0 : count.get();
        }
    }
}
