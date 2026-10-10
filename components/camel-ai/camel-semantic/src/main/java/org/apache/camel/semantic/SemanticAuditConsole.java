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

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.semantic.internal.SemanticAuditService;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonObject;

/** Read-only history. Querying never invokes an expert or needs its current declaration. */
@DevConsole(name = "semantic-audit", displayName = "Semantic Audit", description = "Browse retained semantic audit records")
public class SemanticAuditConsole extends AbstractDevConsole {
    @Metadata(label = "query", description = "Event ID to inspect with its linked evaluation records",
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
    @Metadata(label = "query", description = "Filter by explicit route action", javaType = "java.lang.String")
    public static final String ACTION = "action";
    @Metadata(label = "query", description = "Filter by expert bean reference", javaType = "java.lang.String")
    public static final String EXPERT = "expert";
    @Metadata(label = "query", description = "Filter by route", javaType = "java.lang.String")
    public static final String ROUTE = "routeId";
    @Metadata(label = "query", description = "Filter by application namespace", javaType = "java.lang.String")
    public static final String NAMESPACE = "namespace";
    @Metadata(label = "query", description = "Filter by application correlation ID", javaType = "java.lang.String")
    public static final String CORRELATION = "correlationId";

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
        response.put("audit", audit.status());
        try {
            SemanticAuditReader reader = audit.getReader();
            String eventId = optionString(options, EVENT_ID);
            if (eventId != null) {
                if (eventId.length() > 256) {
                    throw new IllegalArgumentException("Invalid event ID");
                }
                SemanticAuditRecord record = reader.get(eventId).orElse(null);
                response.put("record", record == null ? null : record.toMap());
                List<Map<String, Object>> evidence = new ArrayList<>();
                if (record != null) {
                    List<String> ids = record.getEvidence();
                    Map<String, SemanticAuditRecord> linkedRecords = reader.getAll(ids);
                    for (String id : ids) {
                        SemanticAuditRecord linked = linkedRecords.get(id);
                        evidence.add(linked == null ? Map.of("eventId", id, "unavailable", true) : linked.toMap());
                    }
                }
                response.put("evidence", evidence);
            } else {
                Map<String, String> filters = new LinkedHashMap<>();
                for (String key : List.of(CATEGORY, ACTION, EXPERT, ROUTE, NAMESPACE, CORRELATION)) {
                    String value = optionString(options, key);
                    if (value != null && !value.isBlank()) {
                        filters.put(key, value);
                    }
                }
                String since = optionString(options, SINCE);
                Object requestedLimit = options.get(LIMIT);
                long limit = requestedLimit == null ? 50 : Long.parseLong(requestedLimit.toString());
                if (limit < 1 || limit > 200) {
                    throw new IllegalArgumentException("Invalid audit page size");
                }
                SemanticAuditPage page = reader.query(new SemanticAuditQuery(
                        filters,
                        since == null || since.isBlank() ? null : Instant.parse(since), optionString(options, CURSOR),
                        (int) limit));
                response.put("records", page.getRecords().stream().map(SemanticAuditRecord::toMap).toList());
                response.put("nextCursor", page.getNextCursor());
                response.put("evicted", page.getEvicted());
                response.put("cursorExpired", page.isCursorExpired());
            }
        } catch (Exception | AssertionError | LinkageError failure) {
            // Backend messages may contain queries or credentials. Export only a stable failure code.
            response.put("error", "audit_query_failed");
        }
        return response;
    }
}
