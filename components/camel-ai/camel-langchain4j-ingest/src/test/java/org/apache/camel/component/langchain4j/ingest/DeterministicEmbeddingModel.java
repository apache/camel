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
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

/**
 * Deterministic fake: same text, same vector — no network, no model download.
 */
class DeterministicEmbeddingModel implements EmbeddingModel {

    /** Test hook: a segment carrying this marker fails the embedding, so the exchange fails. */
    public static final String POISON = "POISON-PILL";

    private final int dimension;
    private final AtomicInteger embedAllCalls = new AtomicInteger();

    public DeterministicEmbeddingModel(int dimension) {
        this.dimension = dimension;
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
        embedAllCalls.incrementAndGet();
        List<Embedding> embeddings = new ArrayList<>(segments.size());
        for (TextSegment segment : segments) {
            if (segment.text().contains(POISON)) {
                throw new IllegalStateException("test-induced embedding failure: " + POISON);
            }
            Random random = new Random(segment.text().hashCode());
            float[] vector = new float[dimension];
            for (int i = 0; i < dimension; i++) {
                vector[i] = random.nextFloat();
            }
            embeddings.add(Embedding.from(vector));
        }
        return Response.from(embeddings);
    }

    @Override
    public int dimension() {
        return dimension;
    }

    /** How many batches were embedded. */
    public int embedAllCalls() {
        return embedAllCalls.get();
    }
}
