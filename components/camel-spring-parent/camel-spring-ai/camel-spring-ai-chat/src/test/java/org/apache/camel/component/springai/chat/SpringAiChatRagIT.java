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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.springai.chat.rag.SpringAiRagAggregatorStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.ai.document.Document;

/**
 * Integration test for RAG (Retrieval Augmented Generation) features.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Disabled unless running in CI")
public class SpringAiChatRagIT extends OllamaTestSupport {

    @Test
    public void testRagWithManualAugmentedData() {
        List<Document> context = new ArrayList<>();
        context.add(new Document(
                "Apache Camel is an open-source integration framework based on Enterprise Integration Patterns."));
        context.add(new Document("Camel was created in 2007 and is part of the Apache Software Foundation."));

        var exchange = template().request("direct:rag", e -> {
            e.getIn().setBody("When was Camel created? Answer with just the year.");
            e.getIn().setHeader(SpringAiChatConstants.AUGMENTED_DATA, context);
        });

        String response = exchange.getMessage().getBody(String.class);
        assertThat(response).isNotNull();
        assertThat(response).contains("2007");
    }

    @Test
    public void testRagWithAggregatorStrategy() {
        String response = template()
                .requestBody("direct:rag-enricher", "What framework is mentioned? Answer in two words.", String.class);

        assertThat(response).isNotNull();
        assertThat(response.toLowerCase()).containsAnyOf("camel", "apache");
    }

    @Test
    public void testRagWithSystemMessage() {
        List<Document> context = new ArrayList<>();
        context.add(new Document("The product price is $99.99"));

        var exchange = template().request("direct:rag", e -> {
            e.getIn().setBody("What is the price?");
            e.getIn().setHeader(SpringAiChatConstants.AUGMENTED_DATA, context);
            e.getIn()
                    .setHeader(
                            SpringAiChatConstants.SYSTEM_MESSAGE,
                            "You are a helpful assistant. Answer based only on the provided context.");
        });

        String response = exchange.getMessage().getBody(String.class);
        assertThat(response).isNotNull();
        assertThat(response).containsAnyOf("99", "$");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                bindChatModel(this.getCamelContext());

                SpringAiRagAggregatorStrategy aggregatorStrategy = new SpringAiRagAggregatorStrategy();

                from("direct:rag").to("spring-ai-chat:rag?chatModel=#chatModel");

                from("direct:rag-enricher")
                        .enrich("direct:retrieve-context", aggregatorStrategy)
                        .to("spring-ai-chat:rag?chatModel=#chatModel");

                from("direct:retrieve-context").process(exchange -> {
                    // Simulate document retrieval
                    List<String> documents = new ArrayList<>();
                    documents.add("Apache Camel is a powerful integration framework.");
                    documents.add("Camel supports over 300 components for integration.");
                    exchange.getIn().setBody(documents);
                });
            }
        };
    }
}
