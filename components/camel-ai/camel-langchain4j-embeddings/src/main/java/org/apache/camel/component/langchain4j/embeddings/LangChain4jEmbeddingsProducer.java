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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.StreamCache;
import org.apache.camel.TypeConverter;
import org.apache.camel.WrappedFile;
import org.apache.camel.component.ai.observability.GenAiErrorSupport;
import org.apache.camel.component.ai.observability.GenAiModelResolver;
import org.apache.camel.component.ai.observability.GenAiObservability;
import org.apache.camel.component.ai.observability.GenAiObservation;
import org.apache.camel.component.ai.observability.GenAiObservationContext;
import org.apache.camel.component.ai.observability.GenAiOperationName;
import org.apache.camel.component.ai.observability.GenAiUsage;
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
            List<Object> bodyList = (List<Object>) body;
            if (bodyList.isEmpty()) {
                throw new IllegalArgumentException(
                        "Batch embedding requires a non-empty List body");
            }
            processBatch(exchange, model, message, bodyList);
        } else {
            processSingle(exchange, model, message);
        }
    }

    private void processSingle(Exchange exchange, EmbeddingModel model, Message message) throws Exception {
        final TextSegment in = toTextSegment(exchange, message.getMandatoryBody(), -1);
        GenAiObservationContext observationContext = GenAiObservationContext.builder()
                .operationName(GenAiOperationName.EMBEDDINGS)
                .system(GenAiModelResolver.resolveSystem(exchange.getContext().getClassResolver(), model))
                .requestModel(GenAiModelResolver.resolveModelName(exchange.getContext().getClassResolver(), model))
                .componentScheme("langchain4j-embeddings")
                .build();
        GenAiObservation observation = GenAiObservability.start(exchange, observationContext);
        try {
            final Response<Embedding> result = model.embed(in);
            populateHeaders(message, result, in, observationContext.requestModel());
            observation.recordSuccess(GenAiUsage.of(
                    result.tokenUsage() != null ? result.tokenUsage().inputTokenCount() : null,
                    result.tokenUsage() != null ? result.tokenUsage().outputTokenCount() : null,
                    result.finishReason(),
                    null));
        } catch (RuntimeException e) {
            GenAiErrorSupport.apply(exchange, e);
            observation.recordError(e);
            throw e;
        } finally {
            observation.close();
        }
    }

    private void processBatch(Exchange exchange, EmbeddingModel model, Message message, List<Object> bodyList)
            throws Exception {
        List<TextSegment> segments = new ArrayList<>(bodyList.size());
        int index = 0;
        for (Object item : bodyList) {
            segments.add(toTextSegment(exchange, item, index++));
        }

        GenAiObservationContext observationContext = GenAiObservationContext.builder()
                .operationName(GenAiOperationName.EMBEDDINGS)
                .system(GenAiModelResolver.resolveSystem(exchange.getContext().getClassResolver(), model))
                .requestModel(GenAiModelResolver.resolveModelName(exchange.getContext().getClassResolver(), model))
                .componentScheme("langchain4j-embeddings")
                .build();
        GenAiObservation observation = GenAiObservability.start(exchange, observationContext);
        try {
            final Response<List<Embedding>> result = model.embedAll(segments);

            populateBatchHeaders(message, result, segments, observationContext.requestModel());
            observation.recordSuccess(GenAiUsage.of(
                    result.tokenUsage() != null ? result.tokenUsage().inputTokenCount() : null,
                    result.tokenUsage() != null ? result.tokenUsage().outputTokenCount() : null,
                    result.finishReason(),
                    null));
        } catch (RuntimeException e) {
            GenAiErrorSupport.apply(exchange, e);
            observation.recordError(e);
            throw e;
        } finally {
            observation.close();
        }
    }

    /**
     * Converts a body, or an element of a List body, to the TextSegment to embed. A TextSegment is used as is, a type
     * converter to TextSegment is used when there is one, and text, bytes, a stream, a Reader or a file is read as
     * text. Any other value, such as a Map or a POJO, fails as an InvalidPayloadException instead of being embedded as
     * its toString().
     *
     * @param index the index of the element in a List body, or -1 for the body itself
     */
    private static TextSegment toTextSegment(Exchange exchange, Object value, int index)
            throws InvalidPayloadException, NoTypeConversionAvailableException {
        if (value instanceof TextSegment segment) {
            return segment;
        }
        TypeConverter tc = exchange.getContext().getTypeConverter();
        TextSegment segment = value != null ? tc.tryConvertTo(TextSegment.class, exchange, value) : null;
        if (segment != null) {
            return segment;
        }
        if (!isReadableAsText(value)) {
            String what = index < 0
                    ? "" : "the element at index " + index + " of the List body is " + typeOf(value) + "; ";
            throw new InvalidPayloadException(
                    exchange, TextSegment.class, exchange.getMessage(),
                    what + "langchain4j-embeddings embeds text, bytes, a stream, a Reader, a file or a TextSegment;"
                                                                        + " convert the body to text before the endpoint,"
                                                                        + " such as with convertBodyTo(String) or a marshal"
                                                                        + " to JSON");
        }
        return TextSegment.from(tc.mandatoryConvertTo(String.class, exchange, value));
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

    /**
     * Removes the headers of an earlier call, so a header that this call does not set does not keep a stale value: an
     * EMBEDDINGS header from an earlier batch would make a later embedding store ADD store the old batch.
     */
    private static void removeResultHeaders(Message message) {
        message.removeHeader(LangChain4jEmbeddingsHeaders.REQUEST_MODEL);
        message.removeHeader(LangChain4jEmbeddingsHeaders.FINISH_REASON);
        message.removeHeader(LangChain4jEmbeddingsHeaders.INPUT_TOKEN_COUNT);
        message.removeHeader(LangChain4jEmbeddingsHeaders.OUTPUT_TOKEN_COUNT);
        message.removeHeader(LangChain4jEmbeddingsHeaders.TOTAL_TOKEN_COUNT);
        message.removeHeader(LangChain4jEmbeddingsHeaders.VECTOR);
        message.removeHeader(LangChain4jEmbeddingsHeaders.TEXT_SEGMENT);
        message.removeHeader(LangChain4jEmbeddingsHeaders.EMBEDDING);
        message.removeHeader(LangChain4jEmbeddingsHeaders.EMBEDDINGS);
        message.removeHeader(LangChain4jEmbeddingsHeaders.TEXT_SEGMENTS);
    }

    private static void populateHeaders(
            Message message, Response<Embedding> result, TextSegment textSegment, String requestModel) {
        removeResultHeaders(message);
        if (requestModel != null) {
            message.setHeader(LangChain4jEmbeddingsHeaders.REQUEST_MODEL, requestModel);
        }

        if (result.finishReason() != null) {
            message.setHeader(LangChain4jEmbeddingsHeaders.FINISH_REASON, result.finishReason());
        }

        if (result.tokenUsage() != null) {
            message.setHeader(LangChain4jEmbeddingsHeaders.INPUT_TOKEN_COUNT, result.tokenUsage().inputTokenCount());
            message.setHeader(LangChain4jEmbeddingsHeaders.OUTPUT_TOKEN_COUNT, result.tokenUsage().outputTokenCount());
            message.setHeader(LangChain4jEmbeddingsHeaders.TOTAL_TOKEN_COUNT, result.tokenUsage().totalTokenCount());
        }

        message.setHeader(LangChain4jEmbeddingsHeaders.VECTOR, result.content().vector());
        message.setHeader(LangChain4jEmbeddingsHeaders.TEXT_SEGMENT, textSegment);
        message.setHeader(LangChain4jEmbeddingsHeaders.EMBEDDING, result.content());
    }

    private static void populateBatchHeaders(
            Message message, Response<List<Embedding>> result, List<TextSegment> segments, String requestModel) {
        removeResultHeaders(message);
        if (requestModel != null) {
            message.setHeader(LangChain4jEmbeddingsHeaders.REQUEST_MODEL, requestModel);
        }

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
        // Preserve text segments so downstream embedding store can associate them with embeddings
        message.setHeader(LangChain4jEmbeddingsHeaders.TEXT_SEGMENTS, segments);
    }
}
