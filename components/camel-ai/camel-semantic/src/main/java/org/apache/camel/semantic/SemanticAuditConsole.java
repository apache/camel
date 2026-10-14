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
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.semantic.internal.SemanticAuditAccess;
import org.apache.camel.semantic.internal.SemanticAuditService;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonObject;

/** Read-only history. Querying never invokes an expert or needs its current declaration. */
@DevConsole(name = "semantic-audit", displayName = "Semantic Audit", description = "Browse retained semantic audit records")
public class SemanticAuditConsole extends AbstractDevConsole {
    @Metadata(label = "query", description = "Event ID to inspect; legacy records remain exportable",
              javaType = "java.lang.String")
    public static final String EVENT_ID = "eventId";
    @Metadata(label = "query", description = "Backend cursor for an older page", javaType = "java.lang.String")
    public static final String CURSOR = "cursor";
    @Metadata(label = "query", description = "Maximum records, between 1 and 200", javaType = "java.lang.Integer",
              defaultValue = "50")
    public static final String LIMIT = "limit";
    @Metadata(label = "query", description = "UTC earliest timestamp in ISO-8601 format", javaType = "java.lang.String")
    public static final String SINCE = "since";
    @Metadata(label = "query", description = "Filter by category", javaType = "java.lang.String")
    public static final String CATEGORY = "category";
    @Metadata(label = "query", description = "Filter by expert bean reference", javaType = "java.lang.String")
    public static final String EXPERT = "expert";
    @Metadata(label = "query", description = "Filter by route", javaType = "java.lang.String")
    public static final String ROUTE = "routeId";
    @Metadata(label = "query", description = "Filter by Camel breadcrumb ID", javaType = "java.lang.String")
    public static final String BREADCRUMB = "breadcrumbId";

    @Metadata(label = "query", description = "Event IDs for bounded bulk export; at most 1000", javaType = "java.util.List")
    public static final String EVENT_IDS = "eventIds";
    @Metadata(label = "query", description = "Anchor event ID for a source-local case", javaType = "java.lang.String")
    public static final String CASE_EVENT_ID = "caseEventId";
    @Metadata(label = "query", description = "Case or bulk retrieval record budget, between 1 and 1000",
              javaType = "java.lang.Integer", defaultValue = "200")
    public static final String MAX_RECORDS = "maxRecords";
    @Metadata(label = "query", description = "Include untrusted captured input in explicit export",
              javaType = "java.lang.Boolean", defaultValue = "false")
    public static final String INCLUDE_INPUT = "includeInput";
    @Metadata(label = "query", description = "Filter by executing route when supported by the reader",
              javaType = "java.lang.String")
    public static final String EXECUTING_ROUTE = "executingRouteId";
    @Metadata(label = "query", description = "Filter by recorded exchange ID when supported by the reader",
              javaType = "java.lang.String")
    public static final String EXCHANGE = "exchangeId";

    public SemanticAuditConsole() {
        super("camel", "semantic-audit", "Semantic Audit", "Browse retained semantic audit records");
    }

    @Override
    public Object call(MediaType mediaType, Map<String, Object> options) {
        // A slow reader must not serialize independent connector requests on the console lock.
        return mediaType == MediaType.JSON ? doCallJson(options) : doCallText(options);
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        return doCallJson(options).toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        SemanticAuditService audit = SemanticAuditService.get(getCamelContext());
        JsonObject response = new JsonObject();
        response.put("viewVersion", 2);
        try {
            long selectors = List.of(EVENT_ID, CASE_EVENT_ID, EVENT_IDS).stream().filter(options::containsKey).count();
            boolean bulk = options.containsKey(CASE_EVENT_ID) || options.containsKey(EVENT_IDS);
            if (selectors > 1 || options.containsKey(EVENT_ID) && options.size() > 1
                    || bulk && options.keySet().stream().anyMatch(
                            key -> !List.of(CASE_EVENT_ID, EVENT_IDS, MAX_RECORDS, INCLUDE_INPUT, SINCE).contains(key))
                    || !bulk && (options.containsKey(MAX_RECORDS) || options.containsKey(INCLUDE_INPUT))) {
                throw new IllegalArgumentException("Conflicting audit query options");
            }
            SemanticAuditReader reader = audit.getReader();
            response.put("audit", audit.status());
            response.put("source",
                    Map.of("contextId", getCamelContext().getName(), "reader", audit.getConfiguration().getReader()));
            String eventId = optionString(options, EVENT_ID);
            String caseId = optionString(options, CASE_EVENT_ID);
            if (options.containsKey(EVENT_IDS) || caseId != null) {
                int limit = Integer.parseInt(options.getOrDefault(MAX_RECORDS, 200).toString());
                boolean includeInput = Boolean.parseBoolean(options.getOrDefault(INCLUDE_INPUT, false).toString());
                var access = new SemanticAuditAccess(reader, limit, includeInput);
                if (options.containsKey(EVENT_IDS)) {
                    if (!(options.get(EVENT_IDS) instanceof List<?> ids)
                            || ids.stream().anyMatch(id -> !(id instanceof String))) {
                        throw new IllegalArgumentException("Audit IDs must be a list of strings");
                    }
                    response.putAll(access.getAll(ids.stream().map(String.class::cast).toList()));
                } else {
                    response.putAll(access.caseFor(caseId, since(options)));
                }
            } else if (eventId != null) {
                if (eventId.isBlank() || eventId.length() > 256) {
                    throw new IllegalArgumentException("Invalid event ID");
                }
                SemanticAuditRecord record = reader.get(eventId).orElse(null);
                response.put("record", record == null ? null : record.toMap());
                if (record != null) {
                    response.put("legacy", "decision".equals(record.getCategory()));
                    response.put("interpretationState", record.toMap().containsKey("interpretation")
                            ? "recorded"
                            : record.toMap().getOrDefault("interpretationOmitted", "not_recorded"));
                    if (record.toMap().containsKey("input")) {
                        response.put("inputWarning", SemanticAuditAccess.INPUT_WARNING);
                    }
                }
            } else {
                if (options.containsKey("action") || options.containsKey("namespace") || options.containsKey("correlationId")
                        || "decision".equals(options.get(CATEGORY))) {
                    throw new IllegalArgumentException("Route action browsing was removed");
                }
                Map<String, String> filters = new LinkedHashMap<>();
                for (String key : List.of(CATEGORY, EXPERT, ROUTE, EXECUTING_ROUTE, EXCHANGE, BREADCRUMB)) {
                    String value = optionString(options, key);
                    if (value != null && !value.isBlank()) {
                        filters.put(key, value);
                    }
                }
                if (!reader.getSupportedFilters().containsAll(filters.keySet())) {
                    response.put("error", "audit_filter_unsupported");
                    return response;
                }
                int limit = Integer.parseInt(options.getOrDefault(LIMIT, 50).toString());
                SemanticAuditPage page
                        = reader.query(new SemanticAuditQuery(filters, since(options), optionString(options, CURSOR), limit));
                response.put("records", page.getRecords().stream().filter(record -> !"decision".equals(record.getCategory()))
                        .map(SemanticAuditAccess::summary).toList());
                response.put("nextCursor", page.getNextCursor());
                response.put("evicted", page.getEvicted());
                response.put("cursorExpired", page.isCursorExpired());
                response.put("relationshipCompleteness", "unknown");
            }
        } catch (Exception | AssertionError | LinkageError failure) {
            // Never turn failed retrieval into missing records, or export a partial response/backend exception text.
            response.clear();
            response.put("viewVersion", 2);
            response.put("audit", audit.status());
            response.put("error", "audit_query_failed");
        }
        if (response.toJson().getBytes(StandardCharsets.UTF_8).length > SemanticAuditAccess.MAX_RESPONSE_BYTES) {
            response.clear();
            response.put("viewVersion", 2);
            response.put("error", "audit_response_limit");
        }
        return response;
    }

    private static Instant since(Map<String, Object> options) {
        Object since = options.get(SINCE);
        return since == null || since.toString().isBlank() ? null : Instant.parse(since.toString());
    }
}
