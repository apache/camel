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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.console.DevConsole;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.semantic.internal.SemanticAuditService;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticAuditConsoleTest {
    private static final String TIME = "2026-10-09T12:00:00Z";

    @Test
    void rejectsAnUnfedMemoryReaderButAllowsIndependentBackends() {
        assertThatThrownBy(() -> new SemanticAuditConfiguration(true, Map.of(), List.of("log"), "memory", 10, 10))
                .hasMessageContaining("reader 'memory' requires the 'memory' sink");
        assertThat(new SemanticAuditConfiguration(true, Map.of(), List.of("log"), "database", 10, 10).getReader())
                .isEqualTo("database");
    }

    @Test
    void consoleFiltersSinceInclusivelyAndPagesThroughRetainedRecords() throws Exception {
        try (var fixture = new Fixture()) {
            var filters = Map.of("category", "decision", "action", "block", "expert", "security", "routeId", "route1",
                    "namespace", "tenant1", "correlationId", "request1", "breadcrumbId", "breadcrumb1");
            fixture.store.append(record("old", Map.of("timestamp", "2026-10-09T11:00:00Z")));
            fixture.store.append(record("matching", filters));
            fixture.store.append(record("newest", Map.of("timestamp", "2026-10-09T13:00:00Z")));
            Map<String, Object> options = new HashMap<>(filters);
            options.put("since", TIME);
            var filtered = fixture.call(options);
            assertThat(rows(filtered)).extracting(r -> r.get("eventId")).containsExactly("matching");
            for (String field : filters.keySet()) {
                assertThat(rows(fixture.call(Map.of(field, "missing")))).isEmpty();
            }
            assertThat(rows(fixture.call(Map.of("since", TIME))))
                    .extracting(r -> r.get("eventId")).containsExactly("newest", "matching");
            var page = fixture.call(Map.of("limit", 1));
            assertThat(rows(page)).extracting(r -> r.get("eventId")).containsExactly("newest");
            assertThat(rows(fixture.call(Map.of("cursor", page.get("nextCursor"), "limit", 1))))
                    .extracting(r -> r.get("eventId")).containsExactly("matching");
        }
    }

    @Test
    void consoleUsesBulkEvidenceLookupAndPreservesMissingRecords() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var evaluation = record("evaluation", Map.of("category", "evaluation"));
            var decision = record("decision", Map.of("evidence", List.of("evaluation", "missing")));
            AtomicInteger gets = new AtomicInteger();
            AtomicInteger bulkGets = new AtomicInteger();
            context.getRegistry().bind("remote", new SemanticAuditReader() {
                public SemanticAuditPage query(SemanticAuditQuery query) {
                    throw new AssertionError("unexpected query");
                }

                public Optional<SemanticAuditRecord> get(String id) {
                    gets.incrementAndGet();
                    assertThat(id).isEqualTo("decision");
                    return Optional.of(decision);
                }

                public Map<String, SemanticAuditRecord> getAll(List<String> ids) {
                    bulkGets.incrementAndGet();
                    assertThat(ids).containsExactly("evaluation", "missing");
                    return Map.of("evaluation", evaluation);
                }
            });
            SemanticAuditService.get(context).configure("test",
                    new SemanticAuditConfiguration(false, Map.of(), List.of("memory"), "remote", 10, 10));
            context.start();
            var console = new SemanticAuditConsole();
            console.setCamelContext(context);
            var result = (JsonObject) console.call(DevConsole.MediaType.JSON, Map.of("eventId", "decision"));
            assertThat(result.get("evidence"))
                    .isEqualTo(List.of(evaluation.toMap(), Map.of("eventId", "missing", "unavailable", true)));
            assertThat(gets).hasValue(1);
            assertThat(bulkGets).hasValue(1);
            console.stop();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void listsOmitCapturedInputWithoutChangingStoredDetailsOrEvidence(boolean redacted) throws Exception {
        try (var fixture = new Fixture()) {
            Object input = redacted ? Map.of("prompt", "captured input") : "captured input";
            Map<String, Object> fields = new HashMap<>(Map.of("category", "evaluation", "input", input));
            if (redacted) {
                fields.put("inputRedacted", true);
            }
            var evaluation = record("evaluation", fields);
            var decision = record("decision", Map.of("evidence", List.of("evaluation")));
            fixture.store.append(evaluation);
            fixture.store.append(decision);

            var page = fixture.call(Map.of());
            assertThat(rows(page)).hasSize(2).allSatisfy(row -> assertThat(row)
                    .doesNotContainKeys("input", "inputRedacted"));
            assertThat(page.toJson()).doesNotContain("captured input");
            assertThat(fixture.call(Map.of("eventId", "evaluation"))).containsEntry("record", evaluation.toMap());
            assertThat(fixture.call(Map.of("eventId", "decision"))).containsEntry("evidence", List.of(evaluation.toMap()));
            assertThat(fixture.store.get("evaluation").orElseThrow().toMap()).containsEntry("input", input);
        }
    }

    @Test
    void consoleReportsAnUnavailableSelectedEvent() throws Exception {
        try (var fixture = new Fixture()) {
            assertThat(fixture.call(Map.of("eventId", "absent"))).containsEntry("record", null).containsEntry("evidence",
                    List.of());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "0", "201", "-1", "abc" })
    void consoleRejectsInvalidPageLimitsWithoutLeakingInput(String limit) throws Exception {
        try (var fixture = new Fixture()) {
            assertThat(fixture.call(Map.of("limit", limit))).containsEntry("error", "audit_query_failed")
                    .doesNotContainKey("records");
        }
    }

    @Test
    void consoleSanitizesInvalidQueriesAndBackendFailures() throws Exception {
        try (var fixture = new Fixture()) {
            for (String field : List.of("since", "cursor")) {
                var result = fixture.call(Map.of(field, "private-invalid-value"));
                assertThat(result).containsEntry("error", "audit_query_failed");
                assertThat(result.toJson()).doesNotContain("private-invalid-value");
            }
        }
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind("broken", new SemanticAuditReader() {
                public SemanticAuditPage query(SemanticAuditQuery query) {
                    throw new IllegalStateException("secret credentials");
                }

                public Optional<SemanticAuditRecord> get(String id) {
                    throw new IllegalStateException("secret credentials");
                }
            });
            SemanticAuditService.get(context).configure("test",
                    new SemanticAuditConfiguration(false, Map.of(), List.of("memory"), "broken", 10, 10));
            context.start();
            var console = new SemanticAuditConsole();
            console.setCamelContext(context);
            for (Map<String, Object> options : List.of(Map.<String, Object> of(), Map.<String, Object> of("eventId", "a"))) {
                var result = (JsonObject) console.call(DevConsole.MediaType.JSON, options);
                assertThat(result).containsEntry("error", "audit_query_failed");
                assertThat(result.toJson()).doesNotContain("secret", "credentials");
            }
            console.stop();
        }
    }

    @Test
    void persistedEvidenceMustBeAListOfBoundedNonblankStringIds() {
        for (Object evidence : List.of(List.of(42), List.of(""), List.of(" "), List.of("x".repeat(257)), List.of("bad\n"),
                "event-id")) {
            assertThatThrownBy(() -> record("decision", Map.of("evidence", evidence)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void loggingSinkWritesTheStructuredRecordAsJson() throws Exception {
        Logger logger = (Logger) LogManager.getLogger("org.apache.camel.semantic.audit");
        Level previous = logger.getLevel();
        List<String> messages = new ArrayList<>();
        var appender = new AbstractAppender("SemanticAuditTest", null, null, true, Property.EMPTY_ARRAY) {
            public void append(LogEvent event) {
                messages.add(event.getMessage().getFormattedMessage());
            }
        };
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
        try {
            var record = record("logged", Map.of("action", "block", "evidence", List.of("evaluation")));
            new LoggingSemanticAuditSink().append(record);
            assertThat(messages).hasSize(1);
            assertThat((JsonObject) Jsoner.deserialize(messages.get(0))).containsEntry("eventId", "logged")
                    .containsEntry("action", "block").containsEntry("evidence", List.of("evaluation"));
        } finally {
            logger.setLevel(previous);
            logger.removeAppender(appender);
            appender.stop();
        }
    }

    private static SemanticAuditRecord record(String id, Map<String, ?> fields) {
        Map<String, Object> data
                = new HashMap<>(Map.of("schemaVersion", 1, "eventId", id, "category", "decision", "timestamp", TIME));
        data.putAll(fields);
        return SemanticAuditRecord.fromMap(data);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(JsonObject response) {
        return (List<Map<String, Object>>) response.get("records");
    }

    private static class Fixture implements AutoCloseable {
        final DefaultCamelContext context = new DefaultCamelContext();
        final SemanticAuditConsole console = new SemanticAuditConsole();
        final MemorySemanticAuditStore store;

        Fixture() throws Exception {
            SemanticAuditService.get(context).configure("test",
                    new SemanticAuditConfiguration(true, Map.of(), List.of("memory"), "memory", 10, 10));
            context.start();
            store = (MemorySemanticAuditStore) SemanticAuditService.get(context).getReader();
            console.setCamelContext(context);
        }

        JsonObject call(Map<String, Object> options) {
            return (JsonObject) console.call(DevConsole.MediaType.JSON, options);
        }

        public void close() throws Exception {
            console.stop();
            context.close();
        }
    }
}
