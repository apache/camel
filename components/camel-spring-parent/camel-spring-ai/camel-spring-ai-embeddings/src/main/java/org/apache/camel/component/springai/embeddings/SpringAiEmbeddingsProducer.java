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
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.support.BodyFacts;
import org.apache.camel.support.DefaultProducer;
import org.springframework.ai.document.Document;
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

        // Support a single text and a List of texts as input
        List<String> inputTexts;
        Object body = message.getBody();
        boolean batch = body instanceof List;

        if (body instanceof List<?> list) {
            inputTexts = new ArrayList<>(list.size());
            for (int i = 0; i < list.size(); i++) {
                Object element = list.get(i);
                if (element == null) {
                    throw new IllegalArgumentException("Element " + i + " of the List body must be text, but was: null");
                }
                inputTexts.add(toText(exchange, element, i));
            }
            if (inputTexts.isEmpty()) {
                // nothing to embed, so do not call the model
                message.setBody(new ArrayList<float[]>());
                message.setHeader(SpringAiEmbeddingsHeaders.EMBEDDINGS, new ArrayList<Embedding>());
                message.setHeader(SpringAiEmbeddingsHeaders.INPUT_TEXTS, inputTexts);
                return;
            }
        } else {
            if (body == null) {
                throw new IllegalArgumentException("Message body must be a String or List<String>, but was: null");
            }
            inputTexts = List.of(toText(exchange, body, -1));
        }

        final EmbeddingResponse response = model.call(new EmbeddingRequest(
                inputTexts,
                EmbeddingOptions.builder().build()));

        if (response.getResults() != null && !response.getResults().isEmpty()) {
            if (!batch) {
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

    /**
     * The text to embed of a body, or of an element of a List body: a Spring AI Document as its text, and any other
     * value as the text Camel converts it to, such as text, bytes, a stream, a file, a char[] or a Netty ByteBuf. A
     * value Camel reads only as its toString(), such as a Map or a POJO, fails as an InvalidPayloadException instead of
     * being embedded as that toString() (CAMEL-26512, CAMEL-26553).
     *
     * @param index the index of the element in a List body, or -1 for the body itself
     */
    private static String toText(Exchange exchange, Object value, int index)
            throws InvalidPayloadException, NoTypeConversionAvailableException {
        if (value instanceof Document document) {
            if (document.getText() == null) {
                throw new InvalidPayloadException(
                        exchange, String.class, exchange.getMessage(),
                        listElement(value,
                                index) + "spring-ai-embeddings embeds the text of a Document, but the Document has no text");
            }
            return document.getText();
        }
        String text = exchange.getContext().getTypeConverter().mandatoryConvertTo(String.class, exchange, value);
        if (BodyFacts.isOnlyToString(value, text)) {
            throw new InvalidPayloadException(
                    exchange, String.class, exchange.getMessage(),
                    listElement(value, index) + "spring-ai-embeddings embeds text, bytes, a stream, a file, a Spring AI"
                                                                   + " Document or another value Camel converts to its text,"
                                                                   + " such as a char[], but Camel reads this value only as its"
                                                                   + " toString(); marshal it first, such as with"
                                                                   + " marshal().json(), or, when its toString() is the text to"
                                                                   + " embed, convert the body to text before the endpoint with"
                                                                   + " convertBodyTo(String.class)");
        }
        return text;
    }

    /**
     * Where a value that fails is in the body, for the message of the exception: the index and the type of an element
     * of a List body, or nothing for the body itself. Only built when the value fails.
     */
    private static String listElement(Object value, int index) {
        if (index < 0) {
            return "";
        }
        return "the element at index " + index + " of the List body is "
               + (value == null ? "null" : value.getClass().getName()) + "; ";
    }
}
