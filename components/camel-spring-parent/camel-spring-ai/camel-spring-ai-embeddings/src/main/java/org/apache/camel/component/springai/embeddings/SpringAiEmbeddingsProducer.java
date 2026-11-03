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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.StreamCache;
import org.apache.camel.WrappedFile;
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
     * The text to embed of a body, or of an element of a List body: a Spring AI Document as its text, and text, bytes,
     * a stream, a Reader or a file read as text. Any other value, such as a Map or a POJO, fails as an
     * InvalidPayloadException instead of being embedded as its toString().
     *
     * @param index the index of the element in a List body, or -1 for the body itself
     */
    private static String toText(Exchange exchange, Object value, int index)
            throws InvalidPayloadException, NoTypeConversionAvailableException {
        String what = index < 0 ? "" : "the element at index " + index + " of the List body is " + typeOf(value) + "; ";
        if (value instanceof Document document) {
            if (document.getText() == null) {
                throw new InvalidPayloadException(
                        exchange, String.class, exchange.getMessage(),
                        what + "spring-ai-embeddings embeds the text of a Document, but the Document has no text");
            }
            return document.getText();
        }
        if (!isReadableAsText(value)) {
            throw new InvalidPayloadException(
                    exchange, String.class, exchange.getMessage(),
                    what + "spring-ai-embeddings embeds text, bytes, a stream, a Reader, a file or a Spring AI Document;"
                                                                   + " convert the body to text before the endpoint, such"
                                                                   + " as with convertBodyTo(String) or a marshal to JSON");
        }
        return exchange.getContext().getTypeConverter().mandatoryConvertTo(String.class, exchange, value);
    }

    /** A value Camel converts to its text, rather than to the toString() of an object. */
    private static boolean isReadableAsText(Object value) {
        return value instanceof CharSequence || value instanceof byte[] || value instanceof ByteBuffer
                || value instanceof InputStream || value instanceof Reader || value instanceof StreamCache
                || value instanceof ByteArrayOutputStream
                || value instanceof File || value instanceof Path || value instanceof WrappedFile;
    }

    private static String typeOf(Object value) {
        return value == null ? "null" : value.getClass().getName();
    }
}
