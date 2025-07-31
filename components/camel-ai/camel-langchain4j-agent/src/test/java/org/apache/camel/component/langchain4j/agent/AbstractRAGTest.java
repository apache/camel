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

import java.util.List;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.apache.camel.test.junit5.CamelTestSupport;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static java.time.Duration.ofSeconds;

public abstract class AbstractRAGTest extends CamelTestSupport {

    // Company knowledge base for RAG
    private static final String COMPANY_KNOWLEDGE_BASE = """
            Miles of Camels Car Rental - Company Information

            BUSINESS HOURS:
            Monday-Friday: 8:00 AM - 6:00 PM
            Saturday: 9:00 AM - 4:00 PM
            Sunday: Closed

            RENTAL AGREEMENT
            - This agreement is between Miles of Camels Car Rental ("Company") and the customer ("Renter").

            RENTAL POLICIES:
            - Minimum age: 21 years old
            - Valid driver's license required
            - Credit card required for security deposit
            - Full tank of gas required at return

            VEHICLE FLEET:
            - Economy cars: Starting at $29/day
            - Mid-size cars: Starting at $39/day
            - SUVs: Starting at $59/day
            - Luxury vehicles: Starting at $89/day

            CANCELLATION POLICY
            - Cancellations made 24 hours before pickup: Full refund
            - Cancellations made 12-24 hours before pickup: 50% refund
            - Cancellations made less than 12 hours before pickup: No refund

            VEHICLE RETURN
            - Vehicles must be returned with the same fuel level as at pickup.
            - Late returns incur a fee of $25 per hour or fraction thereof.

            DAMAGE POLICY
            - Minor damages under $200: Covered by insurance
            - Major damages over $200: Customer responsibility

            INSURANCE
            - Basic insurance is included. Premium insurance available for $15/day.

            AGE REQUIREMENTS
            - Minimum age: 21 years old
            - Drivers under 25: Additional surcharge of $20/day

            """;

    protected ChatModel chatModel;
    private String openAiApiKey;
    protected RetrievalAugmentor retrievalAugmentor;

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();

        openAiApiKey = System.getenv("OPENAI_API_KEY");
        if (openAiApiKey == null || openAiApiKey.trim().isEmpty()) {
            throw new IllegalStateException("OPENAI_API_KEY system property is required for testing");
        }

        // Setup components
        chatModel = createChatModel();
        retrievalAugmentor = createRetrievalAugmentor();
    }

    protected ChatModel createChatModel() {
        return OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName(GPT_4_O_MINI)
                .temperature(1.0)
                .timeout(ofSeconds(60))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    private RetrievalAugmentor createRetrievalAugmentor() {
        // Create document from knowledge base
        Document document = Document.from(COMPANY_KNOWLEDGE_BASE);

        // Split into chunks
        List<TextSegment> segments = DocumentSplitters.recursive(300, 100).split(document);

        // Create embeddings
        EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(openAiApiKey)
                .modelName("text-embedding-ada-002")
                .timeout(ofSeconds(30))
                .build();

        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

        // Store in embedding store
        EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
        embeddingStore.addAll(embeddings, segments);

        // Create content retriever
        EmbeddingStoreContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(3)
                .minScore(0.6)
                .build();

        // Create a RetreivalAugmentor that uses only a content retriever : naive rag scenario
        return DefaultRetrievalAugmentor.builder()
                .contentRetriever(contentRetriever)
                .build();
    }
}
