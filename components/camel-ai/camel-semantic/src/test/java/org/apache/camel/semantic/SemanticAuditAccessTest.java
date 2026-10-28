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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import org.apache.camel.console.DevConsole;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.semantic.internal.SemanticAuditAccess;
import org.apache.camel.semantic.internal.SemanticAuditService;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticAuditAccessTest {
    @Test
    void chunksForOldReadersAndDistinguishesMissingFromFailedRetrieval() throws Exception {
        var reader = new LegacyReader();
        var ids = new ArrayList<>(IntStream.range(0, 150).mapToObj(i -> "event-" + i).toList());
        ids.add("missing");
        ids.add("event-0");
        var answer = new SemanticAuditAccess(reader, 200, false).getAll(ids);
        assertThat(reader.batches).containsExactly(100, 51);
        assertThat((List<?>) answer.get("records")).hasSize(150);
        assertThat(answer.get("missingIds")).isEqualTo(List.of("missing"));
        reader.fail = true;
        assertThatThrownBy(() -> new SemanticAuditAccess(reader, 200, false).getAll(ids))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> new SemanticAuditAccess(reader, 1000, false)
                .getAll(IntStream.range(0, 1001).mapToObj(Integer::toString).toList()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void inputIsExcludedByDefaultAndExplicitExportHasAResponseBudget() throws Exception {
        var store = new MemorySemanticAuditStore(100);
        var ids = IntStream.range(0, 50).mapToObj(i -> "event-" + i).toList();
        for (String id : ids) {
            store.append(SemanticAuditRecord.fromMap(Map.of("schemaVersion", 1, "eventId", id,
                    "category", "evaluation", "timestamp", "2026-10-13T00:00:00Z", "input", "x".repeat(100000))));
        }
        var summary = new SemanticAuditAccess(store, 100, false).getAll(ids);
        assertThat((List<?>) summary.get("records")).hasSize(50)
                .allSatisfy(record -> assertThat(((Map<?, ?>) record).containsKey("input")).isFalse());
        var detail = new SemanticAuditAccess(store, 100, true).getAll(List.of(ids.get(0)));
        assertThat(detail).containsEntry("inputWarning", SemanticAuditAccess.INPUT_WARNING);
        assertThatThrownBy(() -> new SemanticAuditAccess(store, 100, true).getAll(ids))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("response budget");
    }

    @Test
    void responseBudgetCountsUtf8ArraySeparatorsAndTheFinalEnvelope() throws Exception {
        var store = new MemorySemanticAuditStore(100);
        var ids = IntStream.range(0, 43).mapToObj(i -> "event-" + i).toList();
        for (int i = 0; i < 42; i++) {
            store.append(inputRecord(ids.get(i), "x".repeat(99000)));
        }
        String tail = "é\n";
        store.append(inputRecord(ids.get(42), tail));
        var initial = new SemanticAuditAccess(store, 100, true).getAll(ids);
        int remaining = SemanticAuditAccess.MAX_RESPONSE_BYTES
                        - new JsonObject(initial).toJson().getBytes(StandardCharsets.UTF_8).length;
        tail += "x".repeat(remaining);
        store.append(inputRecord(ids.get(42), tail));
        var full = new SemanticAuditAccess(store, 100, true).getAll(ids);
        assertThat(new JsonObject(full).toJson().getBytes(StandardCharsets.UTF_8))
                .hasSize(SemanticAuditAccess.MAX_RESPONSE_BYTES);
        try (var context = new DefaultCamelContext()) {
            context.disableJMX();
            context.getRegistry().bind("reader", store);
            SemanticAuditService.get(context).configure("test",
                    new SemanticAuditConfiguration(false, Map.of(), List.of("memory"), "reader", 10, 10));
            context.start();
            var console = new SemanticAuditConsole();
            console.setCamelContext(context);
            assertThat((JsonObject) console.call(DevConsole.MediaType.JSON, Map.of("eventIds", ids, "includeInput", true,
                    "maxRecords", 100))).containsEntry("error", "audit_response_limit").containsEntry("viewVersion", 2)
                    .doesNotContainKey("records");
            console.stop();
        }
        store.append(inputRecord(ids.get(42), tail + "x"));
        assertThatThrownBy(() -> new SemanticAuditAccess(store, 100, true).getAll(ids))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("response budget");
    }

    private static SemanticAuditRecord inputRecord(String id, String input) {
        return SemanticAuditRecord.fromMap(Map.of("schemaVersion", 1, "eventId", id, "category", "evaluation",
                "timestamp", "2026-10-13T00:00:00Z", "input", input));
    }

    @Test
    void associatesRequestLifecycleWithoutInventingCompletenessOrAnExchange() throws Exception {
        var store = new MemorySemanticAuditStore(10);
        store.append(record("member", "evaluation", "invoke-1", "request-1", "batch-1"));
        store.append(record("other-member", "evaluation", "invoke-2", "request-1", "batch-1"));
        store.append(record("cancelled", "request", "request-1", "request-1", null));
        var answer = new SemanticAuditAccess(store, 10, false).caseFor("member", null);
        assertThat(answer).containsEntry("association", Map.of("field", "invocationId", "value", "invoke-1"))
                .containsEntry("relationshipCompleteness", "unknown").containsEntry("retrievalLimited", false);
        assertThat((List<?>) answer.get("records")).hasSize(3)
                .allSatisfy(record -> assertThat(((Map<?, ?>) record).containsKey("exchangeId")).isFalse());
        assertThat(new SemanticAuditAccess(store, 1, false).caseFor("member", null))
                .containsEntry("retrievalLimited", true);
        assertThat(new SemanticAuditAccess(store, 10, false).caseFor("missing", null))
                .containsEntry("unavailable", true).containsEntry("missingIds", List.of("missing"));
    }

    private static SemanticAuditRecord record(String id, String category, String invocation, String request, String batch) {
        var fields = new LinkedHashMap<String, Object>(
                Map.of("schemaVersion", 1, "eventId", id,
                        "category", category, "timestamp", "2026-10-13T00:00:00Z", "invocationId", invocation, "requestId",
                        request));
        if (batch != null) {
            fields.put("batchId", batch);
        }
        return SemanticAuditRecord.fromMap(fields);
    }

    static class LegacyReader implements SemanticAuditReader {
        final List<Integer> batches = new ArrayList<>();
        boolean fail;

        @Override
        public SemanticAuditPage query(SemanticAuditQuery query) {
            throw new AssertionError("Bulk retrieval does not query pages");
        }

        @Override
        public Optional<SemanticAuditRecord> get(String id) throws IOException {
            if (fail) {
                throw new IOException("Backend unavailable");
            }
            return "missing".equals(id) ? Optional.empty() : Optional.of(record(id, "evaluation", id, id, null));
        }

        @Override
        public Map<String, SemanticAuditRecord> getAll(List<String> ids) throws Exception {
            if (ids.size() > 100) {
                throw new IllegalArgumentException("Legacy evidence limit");
            }
            batches.add(ids.size());
            return SemanticAuditReader.super.getAll(ids);
        }
    }
}
