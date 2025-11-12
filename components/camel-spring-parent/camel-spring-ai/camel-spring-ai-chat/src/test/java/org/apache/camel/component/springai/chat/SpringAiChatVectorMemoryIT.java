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

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test demonstrating how to use Spring AI's VectorStoreChatMemoryAdvisor with Camel.
 *
 * This test shows how to configure and use VectorStoreChatMemoryAdvisor to maintain conversation history using vector
 * embeddings for long-term memory with semantic retrieval capabilities.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Disabled unless running in CI")
public class SpringAiChatVectorMemoryIT extends OllamaTestSupport {

    private VectorStore chatMemoryVectorStore;

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

        // Create vector store for chat memory
        chatMemoryVectorStore = SimpleVectorStore.builder(embeddingModel)
                .build();
    }

    @Test
    public void testVectorMemoryRetainsConversationHistory() {
        String conversationId = "vector-memory-test-1";

        // First interaction - tell the assistant something
        var exchange1 = template().request("direct:chat-with-vector-memory", e -> {
            e.getIn().setBody("My favorite programming language is Java.");
            e.getIn().setHeader(SpringAiChatConstants.CONVERSATION_ID, conversationId);
        });

        String response1 = exchange1.getMessage().getBody(String.class);
        assertThat(response1).isNotNull();

        // Second interaction - ask about what we told it before
        // The VectorStoreChatMemoryAdvisor should retrieve relevant context using semantic search
        var exchange2 = template().request("direct:chat-with-vector-memory", e -> {
            e.getIn().setBody("What programming language did I mention? Answer in one word.");
            e.getIn().setHeader(SpringAiChatConstants.CONVERSATION_ID, conversationId);
        });

        String response2 = exchange2.getMessage().getBody(String.class);
        assertThat(response2).isNotNull();
        assertThat(response2.toLowerCase()).contains("java");
    }

    // NOTE: This test is disabled because SimpleVectorStore does not support metadata filtering.
    // Conversation isolation requires a VectorStore that supports filter expressions
    // (e.g., Qdrant, Weaviate, Pinecone, Milvus, ChromaDB).
    // For production use with multiple conversations, use a VectorStore implementation that supports filtering.
    //
    // @Test
    // public void testVectorMemoryWithMultipleConversations() {
    //     // This test would verify conversation isolation if using a filtering-capable VectorStore
    // }

    @Test
    public void testVectorMemorySemanticRetrieval() {
        String conversationId = "semantic-test";

        // Add multiple pieces of information
        template().request("direct:chat-with-vector-memory", e -> {
            e.getIn().setBody("I work as a software engineer at a tech company.");
            e.getIn().setHeader(SpringAiChatConstants.CONVERSATION_ID, conversationId);
        });

        template().request("direct:chat-with-vector-memory", e -> {
            e.getIn().setBody("My hobbies include hiking and photography.");
            e.getIn().setHeader(SpringAiChatConstants.CONVERSATION_ID, conversationId);
        });

        template().request("direct:chat-with-vector-memory", e -> {
            e.getIn().setBody("I graduated from university in 2015.");
            e.getIn().setHeader(SpringAiChatConstants.CONVERSATION_ID, conversationId);
        });

        // Ask a semantic question that should retrieve relevant context
        var exchange = template().request("direct:chat-with-vector-memory", e -> {
            e.getIn().setBody("What is my profession? Answer in 2-3 words.");
            e.getIn().setHeader(SpringAiChatConstants.CONVERSATION_ID, conversationId);
        });

        String response = exchange.getMessage().getBody(String.class);
        assertThat(response).isNotNull();
        assertThat(response.toLowerCase()).containsAnyOf("software", "engineer", "developer");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                bindChatModel(this.getCamelContext());

                // Register VectorStore for chat memory
                this.getCamelContext().getRegistry().bind("chatMemoryVectorStore", chatMemoryVectorStore);

                // Route with VectorStoreChatMemoryAdvisor
                from("direct:chat-with-vector-memory")
                        .to("spring-ai-chat:vector-memory?chatModel=#chatModel&chatMemoryVectorStore=#chatMemoryVectorStore");
            }
        };
    }
}
