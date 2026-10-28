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
package org.apache.camel.semantic;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Bounded query over recorded event/completion time, in the half-open interval [since, until). All filters are ANDed.
 * Text filters (including definition and operation) use exact, case-sensitive equality. Result predicates require an
 * explicit resultType (BOOLEAN, CHOICE, SCORE or CLASSIFICATION). resultValue matches Boolean true/false, an exact
 * Choice label, or numeric Score equality. resultLabel tests Classification membership. scoreMin/scoreMax are inclusive
 * numeric Score bounds. Type-only queries match recorded semantics even without a value; value predicates exclude
 * missing or omitted results. No-match is an expert-defined Choice label, not an inferred status. These predicates do
 * not inspect probability, confidence, interpretation text, current definitions or input. Cursors are backend-specific
 * and must not be reused with different filters or time bounds.
 */
public final class SemanticAuditQuery {
    private static final Set<String> FIELDS
            = Set.of("category", "action", "expert", "routeId", "namespace", "correlationId", "breadcrumbId",
                    "executingRouteId", "exchangeId", "invocationId", "requestId", "batchId", "definition", "operation");
    private final Map<String, String> filters;
    private final Instant since;
    private final Instant until;
    private final SemanticAuditResultFilter resultFilter;
    private final String cursor;
    private final int limit;

    public SemanticAuditQuery(Map<String, String> filters, Instant since, String cursor, int limit) {
        this(filters, since, null, cursor, limit);
    }

    public SemanticAuditQuery(Map<String, String> filters, Instant since, Instant until, String cursor, int limit) {
        if (limit < 1 || limit > 200
                || filters.keySet().stream()
                        .anyMatch(key -> !FIELDS.contains(key) && !SemanticAuditResultFilter.FIELDS.contains(key))
                || filters.values().stream().anyMatch(v -> v == null || v.length() > 256)
                || cursor != null && cursor.length() > 512
                || since != null && until != null && !since.isBefore(until)) {
            throw new IllegalArgumentException("Invalid semantic audit query");
        }
        this.filters = Map.copyOf(filters);
        this.since = since;
        this.until = until;
        this.resultFilter = new SemanticAuditResultFilter(filters);
        this.cursor = cursor;
        this.limit = limit;
    }

    public Map<String, String> getFilters() {
        return filters;
    }

    public Instant getSince() {
        return since;
    }

    /** Exclusive upper bound on event/completion time, or null for an open-ended query. */
    public Instant getUntil() {
        return until;
    }

    public String getCursor() {
        return cursor;
    }

    String fingerprint() {
        StringBuilder canonical = new StringBuilder(since == null ? "" : since.toString()).append(';');
        if (until != null) {
            canonical.append("until:").append(until).append(';');
        }
        new TreeMap<>(filters).forEach((key, value) -> canonical.append(key.length()).append(':').append(key)
                .append(value.length()).append(':').append(value));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public int getLimit() {
        return limit;
    }

    /** Exact, case-sensitive text filters and typed result predicates, applied before pagination. */
    public boolean matches(SemanticAuditRecord record) {
        return (since == null || !record.getTimestamp().isBefore(since))
                && (until == null || record.getTimestamp().isBefore(until))
                && filters.entrySet().stream().filter(e -> FIELDS.contains(e.getKey()))
                        .allMatch(e -> e.getValue().equals(record.text(e.getKey())))
                && resultFilter.matches(record);
    }
}
