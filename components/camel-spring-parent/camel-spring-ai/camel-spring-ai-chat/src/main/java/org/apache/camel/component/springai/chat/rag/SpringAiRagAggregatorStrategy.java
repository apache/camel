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

package org.apache.camel.component.springai.chat.rag;

import static org.apache.camel.component.springai.chat.SpringAiChatConstants.AUGMENTED_DATA;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.springframework.ai.document.Document;

/**
 * Aggregation strategy for Retrieval Augmented Generation (RAG) with Spring AI.
 * <p>
 * This strategy can be used with Camel's Content Enricher EIP to augment prompts with relevant context from external
 * data sources.
 * </p>
 * <p>
 * Usage example:
 *
 * <pre>
 * SpringAiRagAggregatorStrategy aggregatorStrategy = new SpringAiRagAggregatorStrategy();
 *
 * from("direct:chat")
 *         .enrich("direct:rag", aggregatorStrategy)
 *         .to("spring-ai-chat:test?chatOperation=CHAT_SINGLE_MESSAGE");
 *
 * from("direct:rag")
 *         .process(exchange -> {
 *             // Retrieve relevant documents
 *             List&lt;String&gt; documents = retrieveDocuments(exchange.getIn().getBody(String.class));
 *             exchange.getIn().setBody(documents);
 *         });
 * </pre>
 * </p>
 */
public class SpringAiRagAggregatorStrategy implements AggregationStrategy {

    @Override
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        if (oldExchange == null) {
            return newExchange;
        }

        // Extract new data from enricher response
        Optional<List> newAugmentedData =
                Optional.ofNullable(newExchange.getIn().getBody(List.class));
        if (newAugmentedData.isEmpty()) {
            return oldExchange;
        }

        // Convert to Spring AI Documents
        List<Document> newDocuments = new ArrayList<>();
        for (Object item : newAugmentedData.get()) {
            if (item instanceof String) {
                newDocuments.add(new Document((String) item));
            } else if (item instanceof Document) {
                newDocuments.add((Document) item);
            } else {
                // Try to convert to string
                newDocuments.add(new Document(item.toString()));
            }
        }

        // Get or create augmented data list in header
        List<Document> augmentedData = Optional.ofNullable(oldExchange.getIn().getHeader(AUGMENTED_DATA, List.class))
                .orElse(new ArrayList<>());
        augmentedData.addAll(newDocuments);

        oldExchange.getIn().setHeader(AUGMENTED_DATA, augmentedData);
        return oldExchange;
    }
}
