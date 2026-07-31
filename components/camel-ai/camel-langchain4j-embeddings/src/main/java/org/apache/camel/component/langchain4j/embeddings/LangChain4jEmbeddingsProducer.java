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
package org.apache.camel.component.langchain4j.embeddings;

import java.util.List;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;

public class LangChain4jEmbeddingsProducer extends DefaultProducer {
    public LangChain4jEmbeddingsProducer(LangChain4jEmbeddingsEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public LangChain4jEmbeddingsEndpoint getEndpoint() {
        return (LangChain4jEmbeddingsEndpoint) super.getEndpoint();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void process(Exchange exchange) throws Exception {
        final EmbeddingModel model = getEndpoint().getConfiguration().getEmbeddingModel();
        final Message message = exchange.getMessage();
        Object body = message.getBody();

        if (body instanceof List) {
            processBatch(exchange, model, message, (List<Object>) body);
        } else {
            processSingle(exchange, model, message);
        }
    }

    private void processSingle(Exchange exchange, EmbeddingModel model, Message message) throws Exception {
        final TextSegment in = exchange.getMessage().getMandatoryBody(TextSegment.class);
        final Response<Embedding> result = model.embed(in);

        if (result.finishReason() != null) {
            message.setHeader(LangChain4jEmbeddingsHeaders.FINISH_REASON, result.finishReason());
        }

        if (result.tokenUsage() != null) {
            message.setHeader(LangChain4jEmbeddingsHeaders.INPUT_TOKEN_COUNT, result.tokenUsage().inputTokenCount());
            message.setHeader(LangChain4jEmbeddingsHeaders.OUTPUT_TOKEN_COUNT, result.tokenUsage().outputTokenCount());
            message.setHeader(LangChain4jEmbeddingsHeaders.TOTAL_TOKEN_COUNT, result.tokenUsage().totalTokenCount());
        }

        message.setHeader(LangChain4jEmbeddingsHeaders.VECTOR, result.content().vector());
        message.setHeader(LangChain4jEmbeddingsHeaders.TEXT_SEGMENT, in);
        message.setHeader(LangChain4jEmbeddingsHeaders.EMBEDDING, result.content());
    }

    private void processBatch(Exchange exchange, EmbeddingModel model, Message message, List<Object> bodyList)
            throws Exception {
        // Convert each element to TextSegment using the type converter
        List<TextSegment> segments = new java.util.ArrayList<>(bodyList.size());
        for (Object item : bodyList) {
            TextSegment segment = exchange.getContext().getTypeConverter().mandatoryConvertTo(TextSegment.class, item);
            segments.add(segment);
        }

        final Response<List<Embedding>> result = model.embedAll(segments);

        if (result.finishReason() != null) {
            message.setHeader(LangChain4jEmbeddingsHeaders.FINISH_REASON, result.finishReason());
        }

        if (result.tokenUsage() != null) {
            message.setHeader(LangChain4jEmbeddingsHeaders.INPUT_TOKEN_COUNT, result.tokenUsage().inputTokenCount());
            message.setHeader(LangChain4jEmbeddingsHeaders.OUTPUT_TOKEN_COUNT, result.tokenUsage().outputTokenCount());
            message.setHeader(LangChain4jEmbeddingsHeaders.TOTAL_TOKEN_COUNT, result.tokenUsage().totalTokenCount());
        }

        List<Embedding> embeddings = result.content();
        message.setHeader(LangChain4jEmbeddingsHeaders.EMBEDDINGS, embeddings);
        message.setBody(embeddings);
    }
}
