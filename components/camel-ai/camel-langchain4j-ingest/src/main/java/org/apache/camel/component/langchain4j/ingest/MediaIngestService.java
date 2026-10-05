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
package org.apache.camel.component.langchain4j.ingest;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Embeds a media document — audio, an image, video or a PDF, told by the MIME type — whole, as one vector, and stores
 * it with a placeholder segment: its text is the document id and it carries the same identity metadata as text segments
 * plus the MIME type, so retrieval cites it the same way and can tell it from text. Nothing is split or batched, which
 * is why this is a class of its own beside {@link IngestService} rather than a mode of it; the size checks count bytes.
 * Embeds through LangChain4j's {@code EmbeddingRequest} API, still experimental in 1.20.
 */
final class MediaIngestService {

    private static final Logger LOG = LoggerFactory.getLogger(MediaIngestService.class);

    private final String pipeline;
    private final EmbeddingStore<TextSegment> store;
    private final EmbeddingModel model;
    private final int maxDocumentSize;
    private final int minDocumentSize;

    MediaIngestService(String pipeline, EmbeddingStore<TextSegment> store, EmbeddingModel model, int maxDocumentSize,
                       int minDocumentSize) {
        this.pipeline = pipeline;
        this.store = store;
        this.model = model;
        this.maxDocumentSize = maxDocumentSize;
        this.minDocumentSize = minDocumentSize;
    }

    /**
     * @param documentId never null or blank: the producer resolves and checks it before calling
     * @param medium     the LangChain4j content type the MIME type belongs to, resolved and checked against the model
     *                   by the producer before the bytes were read
     */
    IngestResult ingest(String documentId, byte[] bytes, String mimeType, ContentType medium) {
        if (bytes == null || bytes.length == 0) {
            return new IngestResult(pipeline, documentId, 0, IngestResult.Outcome.EMPTY);
        }
        if (minDocumentSize > 0 && bytes.length < minDocumentSize) {
            return new IngestResult(pipeline, documentId, 0, IngestResult.Outcome.FILTERED);
        }
        checkSize(documentId, bytes.length);

        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put(LangChain4jIngest.METADATA_PIPELINE, pipeline);
        identity.put(LangChain4jIngest.METADATA_DOCUMENT_ID, documentId);
        identity.put(LangChain4jIngest.METADATA_CONTENT_TYPE, mimeType);

        Content content = MediaTypes.contentOf(medium, Base64.getEncoder().encodeToString(bytes), mimeType);
        // one input, so one embedding is expected back. EmbeddingRequest and EmbeddingResponse are
        // experimental API in LangChain4j 1.20, hence the shape is asserted rather than assumed
        List<Embedding> embeddings = model.embed(EmbeddingRequest.builder().input(content).build()).embeddings();
        if (embeddings == null || embeddings.size() != 1) {
            throw new IllegalStateException(
                    "Ingestion pipeline '" + pipeline + "': the embedding model returned "
                                            + (embeddings == null ? 0 : embeddings.size())
                                            + " embeddings for the single media document '" + documentId + "'");
        }
        store.add(embeddings.get(0), TextSegment.from(documentId, Metadata.from(identity)));

        LOG.debug("Ingestion pipeline '{}': wrote 1 vector of {} document '{}'", pipeline, mimeType, documentId);
        return new IngestResult(pipeline, documentId, 1, IngestResult.Outcome.INGESTED);
    }

    /**
     * Fails an oversized document; the producer also applies it to the size a file consumer announces before the read.
     */
    void checkSize(String documentId, long size) {
        if (maxDocumentSize > 0 && size > maxDocumentSize) {
            throw new IllegalArgumentException(
                    "Ingestion pipeline '" + pipeline + "': document '" + documentId + "' exceeds maxDocumentSize ("
                                               + size + " > " + maxDocumentSize + " bytes)");
        }
    }
}
