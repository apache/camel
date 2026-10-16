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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticAuditQueryTest {
    private static final Instant START = Instant.parse("2026-10-14T12:00:00Z");
    private static final Instant END = START.plusSeconds(60);

    @Test
    void filtersHalfOpenCompletionWindowBeforePaginating() {
        var store = new MemorySemanticAuditStore(10);
        store.append(record("before", START.minusNanos(1), "CHOICE", "none_of_these"));
        store.append(record("start", START, "CHOICE", "none_of_these"));
        store.append(record("different", START.plusSeconds(1), "CHOICE", "billing"));
        store.append(record("last", END.minusNanos(1), "CHOICE", "none_of_these"));
        store.append(record("end", END, "CHOICE", "none_of_these"));
        var filters = Map.of("definition", "routeRequest", "operation", "choice",
                "resultType", "CHOICE", "resultValue", "none_of_these");
        var first = store.query(new SemanticAuditQuery(filters, START, END, null, 1));
        assertThat(first.getRecords()).extracting(SemanticAuditRecord::getEventId).containsExactly("last");
        assertThat(first.getNextCursor()).isNotNull();
        var next = store.query(new SemanticAuditQuery(filters, START, END, first.getNextCursor(), 1));
        assertThat(next.getRecords()).extracting(SemanticAuditRecord::getEventId).containsExactly("start");
        assertThat(next.getNextCursor()).isNull();
        for (Instant until : List.of(END.plusNanos(1), END.minusNanos(1))) {
            assertThatThrownBy(() -> store.query(new SemanticAuditQuery(filters, START, until, first.getNextCursor(), 1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> store.query(new SemanticAuditQuery(filters, START, first.getNextCursor(), 1)))
                .isInstanceOf(IllegalArgumentException.class);
        var changed = new LinkedHashMap<>(filters);
        changed.put("resultValue", "billing");
        assertThatThrownBy(() -> store.query(new SemanticAuditQuery(changed, START, END, first.getNextCursor(), 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new SemanticAuditQuery(Map.of(), null, START, null, 10)
                .matches(record("before", START.minusNanos(1), "BOOLEAN", false))).isTrue();
    }

    @Test
    void typesPreventBooleanChoiceAndNumericCoercion() {
        var bool = query(Map.of("resultType", "BOOLEAN", "resultValue", "false"));
        assertThat(bool.matches(record("boolean", START, "BOOLEAN", false))).isTrue();
        assertThat(bool.matches(record("choice", START, "CHOICE", "false"))).isFalse();
        assertThat(bool.matches(record("malformed", START, "BOOLEAN", "false"))).isFalse();
        var choice = query(Map.of("resultType", "CHOICE", "resultValue", "route to human"));
        assertThat(choice.matches(record("choice", START, "CHOICE", "route to human"))).isTrue();
        assertThat(choice.matches(record("choice", START, "CHOICE", "Route to human"))).isFalse();
        var score = query(Map.of("resultType", "SCORE", "resultValue", "2.00"));
        assertThat(score.matches(record("score", START, "SCORE", 2L))).isTrue();
        assertThat(score.matches(record("score", START, "SCORE", 2.0))).isTrue();
        assertThat(score.matches(record("score", START, "SCORE", "2"))).isFalse();
        assertThat(score.matches(record("choice", START, "CHOICE", "2"))).isFalse();
    }

    @Test
    void scoreRangesIncludeBothBoundsAndPreserveFractionalValues() {
        var range = query(Map.of("resultType", "SCORE", "scoreMin", "1.25", "scoreMax", "2.5"));
        for (double score : List.of(1.25, 1.644, 2.5)) {
            assertThat(range.matches(record("score", START, "SCORE", score))).isTrue();
        }
        for (double score : List.of(1.249, 2.501)) {
            assertThat(range.matches(record("score", START, "SCORE", score))).isFalse();
        }
        assertThat(query(Map.of("resultType", "SCORE", "scoreMin", "1.25"))
                .matches(record("score", START, "SCORE", 100))).isTrue();
        assertThat(query(Map.of("resultType", "SCORE", "scoreMax", "-1.25"))
                .matches(record("score", START, "SCORE", -2))).isTrue();
    }

    @Test
    void classificationMatchesMembershipRatherThanDisplayTextOrProbabilities() {
        var filter = query(Map.of("resultType", "CLASSIFICATION", "resultLabel", "needs review"));
        assertThat(filter.matches(record("labels", START, "CLASSIFICATION", List.of("billing", "needs review")))).isTrue();
        assertThat(filter.matches(record("labels", START, "CLASSIFICATION", List.of("Needs review")))).isFalse();
        assertThat(filter.matches(record("labels", START, "CLASSIFICATION", List.of()))).isFalse();
        assertThat(filter.matches(record("labels", START, "CHOICE", "needs review"))).isFalse();
        var fields = new LinkedHashMap<>(record("labels", START, "CLASSIFICATION", List.of("billing")).toMap());
        fields.put("result", Map.of("value", List.of("billing"), "probabilities", Map.of("needs review", 0.9)));
        assertThat(filter.matches(SemanticAuditRecord.fromMap(fields))).isFalse();
    }

    @Test
    void missingOmittedAndLegacyValuesAreNotInvented() {
        var fields = new LinkedHashMap<>(record("missing", START, "BOOLEAN", false).toMap());
        var type = query(Map.of("resultType", "BOOLEAN"));
        var value = query(Map.of("resultType", "BOOLEAN", "resultValue", "false"));
        fields.remove("result");
        fields.put("resultOmitted", "size_limit");
        assertThat(type.matches(SemanticAuditRecord.fromMap(fields))).isTrue();
        assertThat(value.matches(SemanticAuditRecord.fromMap(fields))).isFalse();
        fields.put("result", Map.of("value", false));
        assertThat(value.matches(SemanticAuditRecord.fromMap(fields))).isFalse();
        fields.remove("resultOmitted");
        fields.remove("semantics");
        assertThat(type.matches(SemanticAuditRecord.fromMap(fields))).isFalse();
        assertThat(value.matches(SemanticAuditRecord.fromMap(fields))).isFalse();
        fields.remove("definition");
        assertThat(query(Map.of("definition", "routeRequest")).matches(SemanticAuditRecord.fromMap(fields))).isFalse();
        assertThat(query(Map.of("operation", "Choice")).matches(SemanticAuditRecord.fromMap(fields))).isFalse();
    }

    @ParameterizedTest
    @MethodSource("invalidFilters")
    void rejectsAmbiguousOrInvalidTypedFilters(Map<String, String> filters) {
        assertThatThrownBy(() -> query(filters)).isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<Map<String, String>> invalidFilters() {
        return Stream.of(Map.of("resultType", "choice"), Map.of("resultValue", "false"),
                Map.of("resultLabel", "billing"), Map.of("scoreMin", "1"),
                Map.of("resultType", "BOOLEAN", "resultValue", "FALSE"),
                Map.of("resultType", "CHOICE", "resultLabel", "billing"),
                Map.of("resultType", "CLASSIFICATION", "resultValue", "billing"),
                Map.of("resultType", "CHOICE", "scoreMin", "1"),
                Map.of("resultType", "SCORE", "scoreMin", "2", "scoreMax", "1"),
                Map.of("resultType", "SCORE", "resultValue", "NaN"),
                Map.of("resultType", "SCORE", "scoreMin", "Infinity"),
                Map.of("resultType", "SCORE", "scoreMax", "1e999"),
                Map.of("definition", "x".repeat(257)));
    }

    @Test
    void rejectsEmptyOrReversedWindowsAndKeepsTheOriginalConstructor() {
        assertThatThrownBy(() -> new SemanticAuditQuery(Map.of(), END, START, null, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SemanticAuditQuery(Map.of(), START, START, null, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(query(Map.of()).getUntil()).isNull();
        assertThat(query(Map.of()).matches(record("event", END, "BOOLEAN", true))).isTrue();
    }

    private static SemanticAuditQuery query(Map<String, String> filters) {
        return new SemanticAuditQuery(filters, START, null, 10);
    }

    private static SemanticAuditRecord record(String id, Instant time, String type, Object value) {
        return SemanticAuditRecord.fromMap(Map.of("schemaVersion", 1, "eventId", id, "timestamp", time.toString(),
                "category", "evaluation", "definition", "routeRequest", "operation", "choice",
                "semantics", Map.of("resultType", type), "result", Map.of("value", value)));
    }
}
