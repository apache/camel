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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticAuditBulkRetrievalTest {
    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void retrievesMoreThanOneHundredIdsWithoutTruncation(boolean memory) throws Exception {
        var store = new MemorySemanticAuditStore(200);
        List<String> ids = new ArrayList<>(IntStream.range(0, 150).mapToObj(i -> "event-" + i).toList());
        for (String id : ids) {
            store.append(record(id));
        }
        ids.add("missing");
        ids.add("missing");
        ids.add("event-0");
        AtomicInteger calls = new AtomicInteger();
        SemanticAuditReader reader = memory ? store : new SemanticAuditReader() {
            @Override
            public SemanticAuditPage query(SemanticAuditQuery query) {
                throw new AssertionError("Bulk retrieval must not scan through query pages");
            }

            @Override
            public Optional<SemanticAuditRecord> get(String eventId) {
                calls.incrementAndGet();
                return store.get(eventId);
            }
        };
        var records = reader.getAll(ids);
        assertThat(records).hasSize(150).containsKeys("event-0", "event-149").doesNotContainKey("missing");
        assertThat(records.keySet()).containsExactlyInAnyOrderElementsOf(ids.subList(0, 150));
        if (!memory) {
            assertThat(calls).hasValue(151);
        }
    }

    @Test
    void retrievalFailureDoesNotBecomeAMissingRecord() {
        SemanticAuditReader reader = new SemanticAuditReader() {
            @Override
            public SemanticAuditPage query(SemanticAuditQuery query) {
                throw new AssertionError("Unexpected query");
            }

            @Override
            public Optional<SemanticAuditRecord> get(String eventId) throws IOException {
                if ("failed".equals(eventId)) {
                    throw new IOException("Storage unavailable");
                }
                return Optional.of(record(eventId));
            }
        };
        assertThatThrownBy(() -> reader.getAll(List.of("available", "failed"))).isInstanceOf(IOException.class);
    }

    @Test
    void legacyEvidenceRecordsStillRejectMoreThanOneHundredReferences() {
        var references = IntStream.range(0, 101).mapToObj(i -> "event-" + i).toList();
        assertThatThrownBy(() -> SemanticAuditRecord.fromMap(Map.of(
                "schemaVersion", 1, "eventId", "legacy", "category", "decision",
                "timestamp", "2026-10-13T00:00:00Z", "evidence", references)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static SemanticAuditRecord record(String id) {
        return SemanticAuditRecord.fromMap(Map.of(
                "schemaVersion", 1, "eventId", id, "category", "evaluation", "timestamp", "2026-10-13T00:00:00Z"));
    }
}
