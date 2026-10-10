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
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.semantic.internal.SemanticAuditService;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticAuditReviewTest {
    @ParameterizedTest
    @ValueSource(strings = { "refs:a,b", "refs:b,a" })
    void recordsEachReturnedAnswerButStillRejectsTheWholeBatch(String expression) throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind("security", new FixedSemanticExpert() {
                @Override
                public SemanticResult evaluate(SemanticEvaluation evaluation, Object state) {
                    return new SemanticResult(
                            evaluation.getParameters().get("threshold").equals(0.2) ? "invalid" : true,
                            null, null, null, null);
                }
            });
            var audit = configured(context);
            SemanticEvaluations.get(context).replace("defs", Map.of("a", evaluation(0.1), "b", evaluation(0.2)));
            context.start();
            var exchange = new DefaultExchange(context);
            exchange.getMessage().setBody("text");
            assertThatThrownBy(
                    () -> context.resolveLanguage("semantic").createExpression(expression).evaluate(exchange, Map.class))
                    .hasMessageContaining("Semantic evaluation 'b'");
            assertThat(exchange.getProperty("CamelSemanticResults")).isNull();
            audit.stop();
            assertThat(rows(audit)).filteredOn(r -> "a".equals(r.text("definition")))
                    .singleElement().satisfies(r -> assertThat(r.getStatus()).isEqualTo("success"));
            assertThat(rows(audit)).filteredOn(r -> "b".equals(r.text("definition")))
                    .singleElement().satisfies(r -> assertThat(r.text("reasonCode")).isEqualTo("invalid_result"));
        }
    }

    @Test
    void validatedMembersAreNotExecutedWhenAnotherInputIsInvalid() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var expert = new FixedSemanticExpert() {
                @Override
                public void validateInput(SemanticEvaluation evaluation, Object state) {
                    if (evaluation.getParameters().get("threshold").equals(0.2)) {
                        throw new IllegalArgumentException("invalid input");
                    }
                }
            };
            context.getRegistry().bind("security", expert);
            var audit = configured(context);
            SemanticEvaluations.get(context).replace("defs", Map.of("a", evaluation(0.1), "b", evaluation(0.2)));
            context.start();
            var exchange = new DefaultExchange(context);
            exchange.getMessage().setBody("text");
            assertThatThrownBy(
                    () -> context.resolveLanguage("semantic").createExpression("refs:a,b").evaluate(exchange, Map.class))
                    .hasMessageContaining("invalid input");
            audit.stop();
            assertThat(expert.calls).isZero();
            assertThat(rows(audit)).filteredOn(r -> "a".equals(r.text("definition")))
                    .singleElement().satisfies(r -> assertThat(r.getStatus()).isEqualTo("not_executed"));
            assertThat(rows(audit)).filteredOn(r -> "b".equals(r.text("definition")))
                    .singleElement().satisfies(r -> assertThat(r.text("reasonCode")).isEqualTo("invalid_input"));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void expertOnlyCaptureIncludesExplicitDecisions(boolean enabled) throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind("security", new FixedSemanticExpert());
            var audit = SemanticAuditService.get(context);
            audit.configure("audit", new SemanticAuditConfiguration(
                    false, Map.of("security", enabled),
                    List.of("memory"), "memory", 10, 10));
            SemanticEvaluations.get(context).replace("defs", Map.of("a", evaluation(0.1)));
            context.start();
            var exchange = new DefaultExchange(context);
            exchange.getMessage().setBody("text");
            context.resolveLanguage("semantic").createExpression("ref:a").evaluate(exchange, Boolean.class);
            var decision = new SemanticAuditDecision();
            decision.setFields(Map.of("action", "block"));
            decision.process(exchange);
            audit.stop();
            assertThat(rows(audit)).hasSize(enabled ? 2 : 0);
            if (enabled) {
                assertThat(rows(audit).get(0).getEvidence()).containsExactly(rows(audit).get(1).getEventId());
            } else {
                assertThat(exchange.getProperty(SemanticAudit.REFERENCES)).isNull();
            }
        }
    }

    @Test
    void contextRestartRebuildsChangedBackendsAndMemoryCapacity() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var first = new CountingSink();
            var second = new CountingSink();
            context.getRegistry().bind("first", first);
            context.getRegistry().bind("second", second);
            var audit = SemanticAuditService.get(context);
            audit.configure("audit",
                    new SemanticAuditConfiguration(true, Map.of(), List.of("first", "memory"), "memory", 10, 10));
            audit.activate();
            context.start();
            SemanticAudit.get(context).decision(null, Map.of("action", "allow"), List.of());
            context.stop();
            assertThat(first.count).hasValue(1);
            context.getRegistry().bind("second", second);
            audit.configure("audit",
                    new SemanticAuditConfiguration(true, Map.of(), List.of("second", "memory"), "memory", 2, 10));
            context.start();
            audit.decision(null, Map.of("action", "block"), List.of());
            context.stop();
            assertThat(first.count).hasValue(1);
            assertThat(first.isStopped()).isTrue();
            assertThat(second.count).hasValue(1);
            assertThat(((MemorySemanticAuditStore) audit.getReader()).getCapacity()).isEqualTo(2);
            assertThat(rows(audit)).singleElement().satisfies(r -> assertThat(r.text("action")).isEqualTo("block"));
        }
    }

    @Test
    void cursorRejectsCollidingFiltersAndNormalizesFilterOrder() throws Exception {
        var store = new MemorySemanticAuditStore(10);
        for (String id : List.of("a", "b", "c"))
            store.append(record(id, "Aa"));
        var filters = new LinkedHashMap<String, String>();
        filters.put("expert", "Aa");
        filters.put("category", "evaluation");
        var first = store.query(new SemanticAuditQuery(filters, null, null, 1));
        assertThatThrownBy(() -> store.query(new SemanticAuditQuery(
                Map.of("expert", "BB", "category", "evaluation"),
                null, first.getNextCursor(), 1))).isInstanceOf(IllegalArgumentException.class);
        var reversed = new LinkedHashMap<String, String>();
        reversed.put("category", "evaluation");
        reversed.put("expert", "Aa");
        assertThat(store.query(new SemanticAuditQuery(reversed, null, first.getNextCursor(), 1)).getRecords())
                .extracting(SemanticAuditRecord::getEventId).containsExactly("b");
        assertThatThrownBy(() -> store.query(new SemanticAuditQuery(filters, Instant.EPOCH, first.getNextCursor(), 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void persistedRecordsValidateTypesAndPreserveFieldOrder() {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("schemaVersion", 1);
        fields.put("eventId", "record");
        fields.put("category", "evaluation");
        fields.put("timestamp", "2026-10-10T00:00:00Z");
        fields.put("expert", "security");
        var record = SemanticAuditRecord.fromMap(fields);
        assertThat(record.toMap().keySet()).containsExactlyElementsOf(fields.keySet());
        assertThat(record.getTimestamp()).isEqualTo(Instant.parse("2026-10-10T00:00:00Z"));
        assertThat(new SemanticAuditQuery(Map.of("expert", "security"), record.getTimestamp(), null, 1).matches(record))
                .isTrue();
        for (var invalid : Map.<String, Object> of("expert", 42, "durationNanos", -1, "semantics", Map.of("meaning", 3),
                "result", Map.of("value", Map.of("unexpected", true))).entrySet()) {
            var broken = new LinkedHashMap<>(fields);
            broken.put(invalid.getKey(), invalid.getValue());
            assertThatThrownBy(() -> SemanticAuditRecord.fromMap(broken)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    private static SemanticAuditService configured(DefaultCamelContext context) {
        var audit = SemanticAuditService.get(context);
        audit.configure("audit", new SemanticAuditConfiguration(true, Map.of(), List.of("memory"), "memory", 100, 100));
        return audit;
    }

    private static SemanticEvaluation evaluation(double threshold) {
        return new SemanticEvaluation("boolean", "security", "${body}", Map.of("threshold", threshold));
    }

    private static List<SemanticAuditRecord> rows(SemanticAuditService audit) throws Exception {
        return audit.getReader().query(new SemanticAuditQuery(Map.of(), null, null, 100)).getRecords();
    }

    private static SemanticAuditRecord record(String id, String expert) {
        return SemanticAuditRecord.fromMap(Map.of("schemaVersion", 1, "eventId", id, "category", "evaluation",
                "timestamp", "2026-10-10T00:00:00Z", "expert", expert));
    }

    private static class CountingSink extends ServiceSupport implements SemanticAuditSink {
        private final AtomicInteger count = new AtomicInteger();

        public void append(SemanticAuditRecord record) {
            count.incrementAndGet();
        }
    }
}
