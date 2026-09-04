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

import java.util.LinkedHashMap;
import java.util.Map;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Splits a document, embeds the segments and writes them to the store, orchestrated by LangChain4j's own
 * {@link EmbeddingStoreIngestor} — always through its fully explicit builder, so the easy-rag module's ServiceLoader
 * defaults can never leak in. What wraps the ingestor is what it lacks: a {@link BatchingEmbeddingModel} decorator (the
 * ingestor embeds a document's full segment list in one call, which can exceed a provider's per-request limits, and its
 * result reports no segment counts), a segment transformer re-stamping the identity metadata a custom splitter might
 * drop, and the blank-document check, which answers {@code EMPTY} before the ingestor is even built.
 *
 * <p>
 * Deliberately naive: it writes whatever it is given and remembers nothing, so ingesting a document twice leaves two
 * copies. Keeping a store in step with a changing source — skipping unchanged documents, replacing changed ones,
 * removing deleted ones — needs a record of what was written, and that engine replaces this class.
 *
 * <p>
 * The store is written in a single call after all segments are embedded, so a mid-embedding failure writes nothing.
 * Whether that one write is atomic is the store provider's business: a store that fails half-way through it leaves the
 * written half behind, and a retried delivery then duplicates it — deduplication tracks document ids, not segments. The
 * synchronising engine, which can find and replace a document's segments by their identity metadata, is the eventual
 * answer.
 */
class IngestService {

    private static final Logger LOG = LoggerFactory.getLogger(IngestService.class);

    private final String pipeline;
    private final EmbeddingStore<TextSegment> store;
    private final EmbeddingModel model;
    private final DocumentSplitter splitter;
    private final int embeddingBatchSize;
    private final int maxDocumentSize;

    public IngestService(String pipeline, EmbeddingStore<TextSegment> store, EmbeddingModel model,
                         int maxSegmentSize, int maxOverlapSize, int embeddingBatchSize, int maxDocumentSize) {
        this(pipeline, store, model, DocumentSplitters.recursive(maxSegmentSize, maxOverlapSize), embeddingBatchSize,
             maxDocumentSize);
    }

    public IngestService(String pipeline, EmbeddingStore<TextSegment> store, EmbeddingModel model,
                         DocumentSplitter splitter, int embeddingBatchSize, int maxDocumentSize) {
        this.pipeline = pipeline;
        this.store = store;
        this.model = model;
        this.splitter = splitter;
        this.embeddingBatchSize = embeddingBatchSize;
        this.maxDocumentSize = maxDocumentSize;
    }

    public IngestResult ingest(String documentId, String text) {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("Ingestion pipeline '" + pipeline + "': documentId is required");
        }
        if (text == null || text.isBlank()) {
            return new IngestResult(pipeline, documentId, 0, IngestResult.Outcome.EMPTY);
        }
        // the pipeline is whole-document-in-memory by design, so the cap is the protection
        // against oversized - and, on a consumer-fed pipeline, attacker-sized - payloads. The
        // failure releases a dedup claim like any other, so a trimmed re-delivery still ingests
        if (maxDocumentSize > 0 && text.length() > maxDocumentSize) {
            throw new IllegalArgumentException(
                    "Ingestion pipeline '" + pipeline + "': document '" + documentId + "' exceeds maxDocumentSize ("
                                               + text.length() + " > " + maxDocumentSize + " characters)");
        }

        // the document id travels with every segment: retrieval can cite it, and the engine that
        // replaces this one needs it to find a document's vectors again
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put(LangChain4jIngest.METADATA_PIPELINE, pipeline);
        identity.put(LangChain4jIngest.METADATA_DOCUMENT_ID, documentId);

        // one ingestor per call: the batching decorator carries this ingestion's segment count
        BatchingEmbeddingModel batching = new BatchingEmbeddingModel(model, embeddingBatchSize);
        EmbeddingStoreIngestor.builder()
                .documentSplitter(splitter)
                .textSegmentTransformer(segment -> ensureIdentityMetadata(segment, identity))
                .embeddingModel(batching)
                .embeddingStore(store)
                .build()
                .ingest(Document.from(text, Metadata.from(identity)));

        if (batching.segmentsEmbedded() == 0) {
            // a custom splitter may produce no segments for non-blank text; nothing was
            // written, so the outcome is EMPTY - which also releases a dedup claim, keeping
            // the invariant that only a delivery that wrote segments keeps its claim
            return new IngestResult(pipeline, documentId, 0, IngestResult.Outcome.EMPTY);
        }

        LOG.debug("Ingestion pipeline '{}': wrote {} segment(s) of document '{}'", pipeline,
                batching.segmentsEmbedded(), documentId);
        return new IngestResult(pipeline, documentId, batching.segmentsEmbedded(), IngestResult.Outcome.INGESTED);
    }

    public String pipeline() {
        return pipeline;
    }

    /**
     * Every LangChain4j-provided splitter propagates the document metadata onto its segments, but a custom
     * {@code DocumentSplitter} might not — and a segment without the identity stamps would break citation and the
     * future synchronising engine. A segment lacking the stamps is rebuilt with them; splitter-added metadata (such as
     * the segment index) is kept.
     */
    private static TextSegment ensureIdentityMetadata(TextSegment segment, Map<String, Object> identity) {
        if (segment.metadata().getString(LangChain4jIngest.METADATA_DOCUMENT_ID) != null) {
            return segment;
        }
        Map<String, Object> merged = new LinkedHashMap<>(segment.metadata().toMap());
        merged.putAll(identity);
        return TextSegment.from(segment.text(), Metadata.from(merged));
    }
}
