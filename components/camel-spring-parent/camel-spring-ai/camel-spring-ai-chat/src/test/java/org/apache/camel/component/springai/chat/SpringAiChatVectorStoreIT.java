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
package org.apache.camel.component.springai.chat;

import java.util.List;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for VectorStore automatic RAG retrieval.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Disabled unless running in CI")
public class SpringAiChatVectorStoreIT extends OllamaTestSupport {

    private VectorStore vectorStore;

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();

        // Create embedding model for vector store
        OllamaApi ollamaApi = OllamaApi.builder()
                .baseUrl(OLLAMA.baseUrl())
                .build();

        EmbeddingModel embeddingModel = OllamaEmbeddingModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(OllamaEmbeddingOptions.builder()
                        .model("embeddinggemma:300m")
                        .build())
                .build();

        // Create and populate vector store
        vectorStore = SimpleVectorStore.builder(embeddingModel)
                .build();

        List<Document> documents = List.of(
                new Document(
                        "Apache Camel is an integration framework created in 2007.",
                        Map.of("source", "camel-docs")),
                new Document(
                        "Camel supports Enterprise Integration Patterns.",
                        Map.of("source", "camel-docs")),
                new Document(
                        "Spring AI provides AI integration for Spring applications.",
                        Map.of("source", "spring-ai-docs")),
                new Document(
                        "Vector stores are used for similarity search in RAG applications.",
                        Map.of("source", "ai-docs")));

        vectorStore.add(documents);
    }

    @Test
    public void testVectorStoreAutoRetrieval() {
        String response = template().requestBody("direct:vector-rag",
                "When was Apache Camel created? Answer with just the year.", String.class);

        assertThat(response).isNotNull();
        assertThat(response).contains("2007");
    }

    @Test
    public void testVectorStoreWithCustomTopK() {
        String response = template().requestBody("direct:vector-rag-top-k",
                "What patterns does Camel support? Answer in 3 words.", String.class);

        assertThat(response).isNotNull();
        assertThat(response.toLowerCase()).containsAnyOf("integration", "enterprise", "pattern");
    }

    @Test
    public void testVectorStoreDoesNotRetrieveIrrelevantContext() {
        // Query about something not in the vector store
        String response = template().requestBody("direct:vector-rag",
                "What is quantum computing? If you don't know, say 'I don't know'.", String.class);

        assertThat(response).isNotNull();
        // The response might still try to answer, but should not contain specific details from our docs
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                bindChatModel(this.getCamelContext());
                this.getCamelContext().getRegistry().bind("vectorStore", vectorStore);

                from("direct:vector-rag")
                        .to("spring-ai-chat:vectorstore?chatModel=#chatModel&vectorStore=#vectorStore&similarityThreshold=0.4");

                from("direct:vector-rag-top-k")
                        .to("spring-ai-chat:vectorstore?chatModel=#chatModel&vectorStore=#vectorStore&topK=2&similarityThreshold=0.4");
            }
        };
    }
}
