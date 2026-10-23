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
package org.apache.camel.component.ai.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ranks route and remote MCP tools in one search space.
 * <p>
 * Callers apply producer, tag, and MCP filters before building the index. Only candidates marked
 * {@link AiToolSearchCandidate#searchable()} are ranked. Duplicate names keep the first candidate. The index does not
 * store request discovery state and does not invoke tools.
 * <p>
 * Keyword scoring matches a deterministic contains match: each distinct term adds 2 when it occurs in the name and 1
 * when it occurs in the description. A tool needs a score of at least 1. Equal scores keep catalogue order.
 * <p>
 * Vector scoring is cosine similarity of embeddings of {@code name} and {@code description}. A missing embedder is
 * rejected and is never treated as keyword search.
 *
 * @since 4.24
 */
public final class AiToolSearchIndex {

    private final AiToolSearchStrategy strategy;
    private final int maxResults;
    private final AiToolSearchEmbedder embedder;
    private final List<Entry> entries;

    private AiToolSearchIndex(
                              AiToolSearchStrategy strategy, int maxResults, AiToolSearchEmbedder embedder,
                              List<Entry> entries) {
        this.strategy = strategy;
        this.maxResults = maxResults;
        this.embedder = embedder;
        this.entries = List.copyOf(entries);
    }

    /**
     * Builds an index over an already filtered catalogue.
     *
     * @param  strategy   keyword or vector
     * @param  candidates route and MCP candidates in catalogue order; may include directly visible tools
     * @param  maxResults maximum number of hits, at least 1
     * @param  embedder   required for {@link AiToolSearchStrategy#VECTOR}; ignored for keyword search
     * @return            an immutable index of the searchable candidates
     */
    public static AiToolSearchIndex create(
            AiToolSearchStrategy strategy, List<AiToolSearchCandidate> candidates, int maxResults,
            AiToolSearchEmbedder embedder) {
        if (strategy == null) {
            throw new IllegalArgumentException("Tool search strategy is required");
        }
        if (maxResults < 1) {
            throw new IllegalArgumentException("toolSearchMaxResults must be at least 1");
        }
        if (strategy == AiToolSearchStrategy.VECTOR && embedder == null) {
            throw new IllegalArgumentException(
                    "VECTOR tool search requires an embedding model and does not fall back to keyword search");
        }
        List<AiToolSearchCandidate> searchable = searchableInOrder(candidates);
        List<Entry> entries = new ArrayList<>(searchable.size());
        if (strategy == AiToolSearchStrategy.VECTOR) {
            List<String> texts = new ArrayList<>(searchable.size());
            for (AiToolSearchCandidate candidate : searchable) {
                texts.add(embeddingText(candidate));
            }
            for (int i = 0; i < searchable.size(); i++) {
                float[] vector = embedder.embed(texts.get(i));
                if (vector == null || vector.length == 0) {
                    throw new IllegalArgumentException(
                            "Embedding model returned an empty vector for tool '" + searchable.get(i).name() + "'");
                }
                entries.add(new Entry(searchable.get(i), null, null, vector));
            }
        } else {
            for (AiToolSearchCandidate candidate : searchable) {
                entries.add(new Entry(candidate, lower(candidate.name()), lower(candidate.description()), null));
            }
        }
        return new AiToolSearchIndex(
                strategy, maxResults, strategy == AiToolSearchStrategy.VECTOR ? embedder : null,
                entries);
    }

    public AiToolSearchStrategy strategy() {
        return strategy;
    }

    public int size() {
        return entries.size();
    }

    /**
     * Keyword search. Each term is split on whitespace. Blank input and no-match queries return an empty list.
     */
    public List<AiToolSearchCandidate> searchKeyword(List<String> terms) {
        if (strategy != AiToolSearchStrategy.KEYWORD) {
            throw new IllegalStateException("This index uses " + strategy + " search, not keyword search");
        }
        List<String> cleaned = clean(terms);
        if (cleaned.isEmpty() || entries.isEmpty()) {
            return List.of();
        }
        List<Scored> scored = new ArrayList<>();
        for (Entry entry : entries) {
            int score = 0;
            for (String term : cleaned) {
                if (entry.name.contains(term)) {
                    score += 2;
                }
                if (entry.description != null && entry.description.contains(term)) {
                    score += 1;
                }
            }
            if (score >= 1) {
                scored.add(new Scored(entry.candidate, score));
            }
        }
        return limit(scored);
    }

    /**
     * Vector search. The closest tools are returned, up to the configured limit. An empty catalogue or a blank query
     * returns an empty list.
     */
    public List<AiToolSearchCandidate> searchVector(String query) {
        if (strategy != AiToolSearchStrategy.VECTOR) {
            throw new IllegalStateException("This index uses " + strategy + " search, not vector search");
        }
        if (embedder == null) {
            throw new IllegalArgumentException(
                    "VECTOR tool search requires an embedding model and does not fall back to keyword search");
        }
        if (entries.isEmpty() || query == null || query.isBlank()) {
            return List.of();
        }
        float[] queryVector = embedder.embed(query);
        if (queryVector == null || queryVector.length == 0) {
            throw new IllegalArgumentException("Embedding model returned an empty vector for the search query");
        }
        List<Scored> scored = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            scored.add(new Scored(entry.candidate, cosine(queryVector, entry.vector)));
        }
        return limit(scored);
    }

    private List<AiToolSearchCandidate> limit(List<Scored> scored) {
        scored.sort((left, right) -> Double.compare(right.score, left.score));
        int count = Math.min(maxResults, scored.size());
        List<AiToolSearchCandidate> hits = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            hits.add(scored.get(i).candidate);
        }
        return List.copyOf(hits);
    }

    private static List<AiToolSearchCandidate> searchableInOrder(List<AiToolSearchCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        Map<String, AiToolSearchCandidate> byName = new LinkedHashMap<>();
        for (AiToolSearchCandidate candidate : candidates) {
            if (candidate == null || !candidate.searchable() || candidate.name().isBlank()) {
                continue;
            }
            byName.putIfAbsent(candidate.name(), candidate);
        }
        return List.copyOf(byName.values());
    }

    private static List<String> clean(List<String> terms) {
        if (terms == null || terms.isEmpty()) {
            return List.of();
        }
        Map<String, String> distinct = new LinkedHashMap<>();
        for (String term : terms) {
            if (term == null) {
                continue;
            }
            for (String part : term.trim().split("\\s+")) {
                String lowered = lower(part);
                if (lowered != null && !lowered.isEmpty()) {
                    distinct.putIfAbsent(lowered, lowered);
                }
            }
        }
        return List.copyOf(distinct.values());
    }

    private static String embeddingText(AiToolSearchCandidate candidate) {
        String description = candidate.description();
        if (description == null || description.isBlank()) {
            return candidate.name();
        }
        return candidate.name() + "\n" + description;
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private static double cosine(float[] left, float[] right) {
        if (left.length != right.length) {
            throw new IllegalArgumentException(
                    "Cannot compare embeddings with different dimensions: "
                     + left.length + " and " + right.length);
        }
        int length = left.length;
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < length; i++) {
            dot += left[i] * (double) right[i];
            leftNorm += left[i] * (double) left[i];
            rightNorm += right[i] * (double) right[i];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private record Entry(AiToolSearchCandidate candidate, String name, String description, float[] vector) {
    }

    private record Scored(AiToolSearchCandidate candidate, double score) {
    }
}
