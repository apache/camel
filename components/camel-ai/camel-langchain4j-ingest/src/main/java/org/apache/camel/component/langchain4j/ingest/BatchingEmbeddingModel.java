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

import java.util.ArrayList;
import java.util.List;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

/**
 * Chunks {@code embedAll} into batches: {@code EmbeddingStoreIngestor} embeds a document's full segment list in one
 * call, which can exceed an embedding provider's per-request limits. Also counts the segments embedded, since the
 * ingestor's {@code IngestionResult} reports only token usage — one instance serves one ingestion, so the count needs
 * no synchronisation.
 */
final class BatchingEmbeddingModel implements EmbeddingModel {

    private final EmbeddingModel delegate;
    private final int batchSize;
    private int segmentsEmbedded;

    BatchingEmbeddingModel(EmbeddingModel delegate, int batchSize) {
        this.delegate = delegate;
        this.batchSize = batchSize;
    }

    // the delegate responses' token usage is deliberately not aggregated into the rebuilt
    // response: IngestResult does not report token usage, so there is no consumer for it
    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
        segmentsEmbedded += segments.size();
        List<Embedding> embeddings = new ArrayList<>(segments.size());
        for (int from = 0; from < segments.size(); from += batchSize) {
            embeddings.addAll(
                    delegate.embedAll(segments.subList(from, Math.min(from + batchSize, segments.size()))).content());
        }
        return Response.from(embeddings);
    }

    @Override
    public int dimension() {
        return delegate.dimension();
    }

    int segmentsEmbedded() {
        return segmentsEmbedded;
    }
}
