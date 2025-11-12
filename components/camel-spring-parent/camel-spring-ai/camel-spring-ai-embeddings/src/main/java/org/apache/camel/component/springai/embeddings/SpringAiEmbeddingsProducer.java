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
package org.apache.camel.component.springai.embeddings;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

public class SpringAiEmbeddingsProducer extends DefaultProducer {
    public SpringAiEmbeddingsProducer(SpringAiEmbeddingsEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public SpringAiEmbeddingsEndpoint getEndpoint() {
        return (SpringAiEmbeddingsEndpoint) super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        final Message message = exchange.getMessage();
        final EmbeddingModel model = getEndpoint().getConfiguration().getEmbeddingModel();

        // Support both single String and List<String> as input
        List<String> inputTexts;
        Object body = message.getBody();

        if (body instanceof String) {
            inputTexts = List.of((String) body);
        } else if (body instanceof List) {
            inputTexts = (List<String>) body;
        } else {
            throw new IllegalArgumentException(
                    "Message body must be a String or List<String>, but was: "
                                               + (body != null ? body.getClass().getName() : "null"));
        }

        final EmbeddingResponse response = model.call(new EmbeddingRequest(
                inputTexts,
                EmbeddingOptions.builder().build()));

        if (response.getResults() != null && !response.getResults().isEmpty()) {
            if (inputTexts.size() == 1) {
                Embedding embedding = response.getResults().get(0);
                message.setBody(embedding.getOutput());
                message.setHeader(SpringAiEmbeddingsHeaders.EMBEDDING_INDEX, embedding.getIndex());
                message.setHeader(SpringAiEmbeddingsHeaders.EMBEDDING, embedding);
                message.setHeader(SpringAiEmbeddingsHeaders.INPUT_TEXT, inputTexts.get(0));
            } else {
                List<float[]> vectors = new ArrayList<>();
                List<Embedding> embeddings = new ArrayList<>();

                for (Embedding embedding : response.getResults()) {
                    vectors.add(embedding.getOutput());
                    embeddings.add(embedding);
                }

                message.setBody(vectors);
                message.setHeader(SpringAiEmbeddingsHeaders.EMBEDDINGS, embeddings);
                message.setHeader(SpringAiEmbeddingsHeaders.INPUT_TEXTS, inputTexts);
            }
        }

        if (response.getMetadata() != null) {
            message.setHeader(SpringAiEmbeddingsHeaders.EMBEDDING_METADATA, response.getMetadata());
        }
    }
}
