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

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.console.DevConsole;
import org.apache.camel.dsl.yaml.common.YamlDeserializationContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.language.semantic.SemanticLanguage;
import org.apache.camel.semantic.internal.SemanticAuditInputSnapshot;
import org.apache.camel.semantic.internal.SemanticAuditService;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.SimpleRegistry;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.lowlevel.Compose;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticAuditInputTest {
    @Test
    void captureIsPerExpertAndNeverSentToObservers() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var expert = new FixedSemanticExpert();
            context.getRegistry().bind("security", expert);
            context.getRegistry().bind("decisions", expert);
            AtomicInteger observed = new AtomicInteger();
            context.getRegistry().bind("observer", new SemanticObserver() {
                public Observation started(SemanticAuditRecord record, Object parent) {
                    assertThat(record.toMap()).doesNotContainKeys("input", "inputOmitted", "inputRedacted");
                    return completed -> {
                        assertThat(completed.toMap()).doesNotContainKeys("input", "inputOmitted", "inputRedacted");
                        observed.incrementAndGet();
                    };
                }
            });
            var audit = SemanticAuditService.get(context);
            audit.configure("test", configuration(true, new SemanticAuditInputConfiguration(true, 4096, null)));
            context.start();
            var language = (SemanticLanguage) context.resolveLanguage("semantic");
            String input = "first line\nsecond line " + "x".repeat(300);
            language.evaluate(evaluation("security"), input);
            language.evaluate(evaluation("decisions"), "private");
            assertThatThrownBy(() -> audit.configure("test", configuration(true, SemanticAuditInputConfiguration.DISABLED)))
                    .hasMessageContaining("restart");
            audit.stop();
            assertThat(records(audit)).hasSize(2);
            assertThat(records(audit).get(0).toMap()).doesNotContainKey("input");
            assertThat(records(audit).get(1).toMap()).containsEntry("input", input);
            assertThat(observed).hasValue(2);
            assertThat(audit.status()).containsEntry("observerErrors", 0L);
            assertThat(expert.states).containsExactly(input, "private");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void captureDoesNotEnableAuditingAndDefaultsToDisabled(boolean auditing) throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind("security", new FixedSemanticExpert());
            AtomicInteger redacted = new AtomicInteger();
            context.getRegistry().bind("redactor", (SemanticAuditInputRedactor) input -> {
                redacted.incrementAndGet();
                return input;
            });
            context.getRegistry().bind("observer", new SemanticObserver() {
                public Observation started(SemanticAuditRecord record, Object parent) {
                    return completed -> assertThat(completed.toMap()).doesNotContainKey("input");
                }
            });
            var audit = SemanticAuditService.get(context);
            audit.configure("test", configuration(auditing, auditing
                    ? SemanticAuditInputConfiguration.DISABLED
                    : new SemanticAuditInputConfiguration(true, 100, "redactor")));
            context.start();
            ((SemanticLanguage) context.resolveLanguage("semantic")).evaluate(evaluation("security"), "private");
            audit.stop();
            assertThat(redacted).hasValue(0);
            assertThat(records(audit)).hasSize(auditing ? 1 : 0)
                    .allSatisfy(record -> assertThat(record.toMap()).doesNotContainKeys("input", "inputOmitted"));
        }
    }

    @Test
    void selectedStateIsCapturedBeforeProviderMutationAndLinkedThroughConsole() throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind("security", new StructuredExpert());
            var audit = SemanticAuditService.get(context);
            audit.configure("test", configuration(true, new SemanticAuditInputConfiguration(true, 100, null)));
            SemanticEvaluations.get(context).replace("definitions", Map.of(
                    "a", new SemanticEvaluation("check", "security", "${header.selected}", Map.of()),
                    "b", new SemanticEvaluation("check", "security", "${header.selected}", Map.of())));
            context.start();
            var exchange = new DefaultExchange(context);
            Map<String, Object> input = new LinkedHashMap<>(Map.of("messages", new ArrayList<>(List.of("hello"))));
            exchange.getMessage().setBody("not selected");
            exchange.getMessage().setHeader("selected", input);
            context.resolveLanguage("semantic").createExpression("refs:a,b").evaluate(exchange, Map.class);
            SemanticAuditDecision decision = new SemanticAuditDecision();
            decision.setFields(Map.of("action", "allow"));
            decision.process(exchange);
            audit.stop();
            assertThat(input).containsEntry("mutated", true);
            assertThat(records(audit)).hasSize(3);
            assertThat(records(audit).get(0).toMap()).doesNotContainKey("input");
            for (var record : records(audit).subList(1, 3)) {
                assertThat(record.toMap()).containsEntry("input", Map.of("messages", List.of("hello")));
                var restored = SemanticAuditRecord.fromMap((JsonObject) Jsoner.deserialize(Jsoner.serialize(record.toMap())));
                assertThat(restored.toMap()).containsEntry("input", record.toMap().get("input"));
                assertThatThrownBy(() -> ((Map<String, Object>) restored.toMap().get("input")).put("secret", "x"))
                        .isInstanceOf(UnsupportedOperationException.class);
            }
            var console = new SemanticAuditConsole();
            console.setCamelContext(context);
            var response = (JsonObject) console.call(DevConsole.MediaType.JSON,
                    Map.of("eventId", records(audit).get(0).getEventId()));
            assertThat(Jsoner.serialize(response)).contains("hello").doesNotContain("not selected", "mutated");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "replace", "omit", "throw", "oversize", "mutate" })
    void redactionIsAppliedBeforeStorageAndFailsClosed(String behavior) throws Exception {
        try (var context = new DefaultCamelContext()) {
            var expert = new StructuredExpert();
            context.getRegistry().bind("security", expert);
            context.getRegistry().bind("redactor", (SemanticAuditInputRedactor) input -> {
                return switch (behavior) {
                    case "replace" -> Map.of("prompt", "[redacted]");
                    case "omit" -> null;
                    case "throw" -> throw new IllegalStateException("secret diagnostic");
                    case "mutate" -> ((Map<String, Object>) input).put("prompt", "changed");
                    default -> "x".repeat(101);
                };
            });
            var audit = SemanticAuditService.get(context);
            audit.configure("test", configuration(true, new SemanticAuditInputConfiguration(true, 100, "redactor")));
            context.start();
            Map<String, Object> input = new LinkedHashMap<>(Map.of("prompt", "secret"));
            var result = ((SemanticLanguage) context.resolveLanguage("semantic"))
                    .evaluate(new SemanticEvaluation("check", "security", null, Map.of()), input);
            assertThat(result.getValue()).isEqualTo(true);
            assertThat(input).containsEntry("prompt", "secret");
            audit.stop();
            var record = records(audit).get(0);
            assertThat(Jsoner.serialize(record.toMap())).doesNotContain("secret", "changed");
            if (behavior.equals("replace")) {
                assertThat(record.toMap()).containsEntry("input", Map.of("prompt", "[redacted]"))
                        .containsEntry("inputRedacted", true);
            } else {
                assertThat(record.toMap()).doesNotContainKey("input").containsEntry("inputOmitted",
                        behavior.equals("omit") ? "redacted" : "redaction_failed");
            }
        }
    }

    @Test
    void oversizedOrUnsupportedInputDoesNotChangeEvaluationOutcome() throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind("security", new StructuredExpert());
            var audit = SemanticAuditService.get(context);
            audit.configure("test", configuration(true, new SemanticAuditInputConfiguration(true, 4, null)));
            context.start();
            var language = (SemanticLanguage) context.resolveLanguage("semantic");
            for (Object input : List.of("too long", new LinkedHashMap<>(Map.of("x", new Object())))) {
                assertThat(language.evaluate(new SemanticEvaluation("check", "security", null, Map.of()), input).getValue())
                        .isEqualTo(true);
            }
            audit.stop();
            assertThat(records(audit)).hasSize(2).allSatisfy(record -> assertThat(record.toMap()).doesNotContainKey("input")
                    .containsEntry("inputOmitted", "snapshot_limit_or_unsupported_type"));
        }
    }

    @Test
    void snapshotsBoundCharactersDepthAndNodesWithoutConsumingOrStringifyingObjects() {
        assertThat(SemanticAuditInputSnapshot.copy("😀\n", 2)).isEqualTo("😀\n");
        assertThatThrownBy(() -> SemanticAuditInputSnapshot.copy("😀x", 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SemanticAuditInputSnapshot.copy(Double.NaN, 100)).isInstanceOf(IllegalArgumentException.class);
        List<Object> cycle = new ArrayList<>();
        cycle.add(cycle);
        assertThatThrownBy(() -> SemanticAuditInputSnapshot.copy(cycle, 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SemanticAuditInputSnapshot.copy(Collections.nCopies(1000, ""), 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SemanticAuditInputSnapshot.copy(new Object() {
            public String toString() {
                throw new AssertionError("must not stringify");
            }
        }, 100)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "yaml", "xml", "java" })
    void allDslsConfigureInputAndFreezeItUntilRestart(String dsl) throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind("security", new FixedSemanticExpert());
            context.getRegistry().bind("redactor", (SemanticAuditInputRedactor) input -> "safe");
            if (dsl.equals("yaml")) {
                yaml(context, "{enabled: true, maxChars: 42, redactor: redactor}");
            } else if (dsl.equals("xml")) {
                context.build();
                PluginHelper.getRoutesLoader(context).loadRoutes(ResourceHelper.fromString("audit.xml", """
                        <semantic xmlns="http://camel.apache.org/schema/semantic">
                          <audit enabled="true"><expert name="security" enabled="true"
                              inputEnabled="true" inputMaxChars="42" inputRedactor="redactor"/></audit>
                        </semantic>
                        """));
            } else {
                context.addRoutes(new RouteBuilder() {
                    public void configure() {
                        SemanticEvaluationsBuilder.semanticEvaluations(this)
                                .audit(SemanticAuditInputTest.configuration(true,
                                        new SemanticAuditInputConfiguration(true, 42, "redactor")))
                                .register();
                    }
                });
            }
            context.start();
            var audit = SemanticAuditService.get(context);
            assertThat(audit.getConfiguration().getInput("security"))
                    .isEqualTo(new SemanticAuditInputConfiguration(true, 42, "redactor"));
            ((SemanticLanguage) context.resolveLanguage("semantic")).evaluate(evaluation("security"), "secret");
            assertThatThrownBy(() -> audit.validateConfiguration("different",
                    configuration(true, SemanticAuditInputConfiguration.DISABLED)))
                    .isInstanceOf(IllegalArgumentException.class);
            audit.stop();
            assertThat(records(audit).get(0).toMap()).containsEntry("input", "safe");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "{enabled: maybe}", "{enabled: true, maxChars: 0}", "{maxChars: abc}", "{unknown: true}", "true" })
    void invalidYamlInputSettingsIncludeSourcePosition(String input) throws Exception {
        try (var context = new DefaultCamelContext()) {
            assertThatThrownBy(() -> yaml(context, input)).hasMessageContaining("audit.yaml");
        }
    }

    @Test
    void persistedRecordsRejectOversizedAndContradictoryInput() {
        Map<String, Object> fields = new LinkedHashMap<>(
                Map.of("schemaVersion", 1, "eventId", "event",
                        "category", "evaluation", "timestamp", "2026-10-10T10:00:00Z"));
        fields.put("input", "x".repeat(SemanticAuditInputConfiguration.MAX_CHARS + 1));
        assertThatThrownBy(() -> SemanticAuditRecord.fromMap(fields)).isInstanceOf(IllegalArgumentException.class);
        fields.put("input", "hello");
        fields.put("inputOmitted", "redacted");
        assertThatThrownBy(() -> SemanticAuditRecord.fromMap(fields)).isInstanceOf(IllegalArgumentException.class);
        fields.remove("inputOmitted");
        fields.put("category", "decision");
        assertThatThrownBy(() -> SemanticAuditRecord.fromMap(fields)).isInstanceOf(IllegalArgumentException.class);
        fields.put("category", "evaluation");
        fields.put("inputRedacted", "yes");
        assertThatThrownBy(() -> SemanticAuditRecord.fromMap(fields)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invocationBeforeStartupNeverBypassesAConfiguredRedactor() throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind("redactor", (SemanticAuditInputRedactor) input -> "safe");
            var audit = SemanticAuditService.get(context);
            audit.configure("test", configuration(true, new SemanticAuditInputConfiguration(true, 100, "redactor")));
            var invocation = audit.begin(null, null, evaluation("security"), null, "security", null, null, null);
            invocation.captureInput("private");
            context.start();
            invocation.complete("success", "evaluation_completed", null);
            audit.stop();
            assertThat(records(audit).get(0).toMap()).doesNotContainKey("input").containsEntry("inputOmitted",
                    "redaction_failed");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "capture", "omit", "throw" })
    void inputIsCapturedOnceWithoutCompletingTheInvocation(String behavior) throws Exception {
        try (var context = new DefaultCamelContext()) {
            AtomicInteger calls = new AtomicInteger();
            context.getRegistry().bind("redactor", (SemanticAuditInputRedactor) input -> {
                calls.incrementAndGet();
                return switch (behavior) {
                    case "omit" -> null;
                    case "throw" -> throw new IllegalStateException("private diagnostic");
                    default -> input;
                };
            });
            var audit = SemanticAuditService.get(context);
            audit.configure("test", configuration(true, new SemanticAuditInputConfiguration(true, 100, "redactor")));
            context.start();
            var invocation = audit.begin(null, null, evaluation("security"), null, "security", null, null, null);
            invocation.captureInput("first");
            invocation.captureInput("second");
            invocation.complete("success", "evaluation_completed", null);
            audit.stop();
            assertThat(calls).hasValue(1);
            assertThat(records(audit)).singleElement().satisfies(record -> {
                assertThat(record.toMap()).containsEntry("status", "success");
                if (behavior.equals("capture")) {
                    assertThat(record.toMap()).containsEntry("input", "first");
                } else {
                    assertThat(record.toMap()).doesNotContainKey("input").containsEntry("inputOmitted",
                            behavior.equals("omit") ? "redacted" : "redaction_failed");
                }
            });
        }
    }

    @Test
    void deferredStartupPublishesAllRedactorsTogether() throws Exception {
        CountDownLatch resolving = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger lookups = new AtomicInteger();
        var registry = new SimpleRegistry() {
            @Override
            public <T> T lookupByNameAndType(String name, Class<T> type) {
                if (type == SemanticAuditInputRedactor.class && lookups.incrementAndGet() == 2) {
                    resolving.countDown();
                    try {
                        assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(failure);
                    }
                }
                return super.lookupByNameAndType(name, type);
            }
        };
        registry.bind("redactor", (SemanticAuditInputRedactor) input -> "safe");
        try (var context = new DefaultCamelContext(registry)) {
            var audit = SemanticAuditService.get(context);
            var policy = new SemanticAuditInputConfiguration(true, 100, "redactor");
            audit.configure("test", new SemanticAuditConfiguration(
                    true,
                    Map.of("security", true, "decisions", true), List.of("memory"), "memory", 100, 100,
                    Map.of("security", policy, "decisions", policy)));
            var before = List.of(
                    audit.begin(null, null, evaluation("security"), null, "security", null, null, null),
                    audit.begin(null, null, evaluation("decisions"), null, "decisions", null, null, null));
            var startup = CompletableFuture.runAsync(context::start);
            try {
                assertThat(resolving.await(10, TimeUnit.SECONDS)).isTrue();
                before.forEach(invocation -> invocation.captureInput("private"));
            } finally {
                release.countDown();
                startup.get(10, TimeUnit.SECONDS);
            }
            before.forEach(invocation -> invocation.complete("success", "evaluation_completed", null));
            for (String expert : List.of("security", "decisions")) {
                var invocation = audit.begin(null, null, evaluation(expert), null, expert, null, null, null);
                invocation.captureInput("private");
                invocation.complete("success", "evaluation_completed", null);
            }
            audit.stop();
            assertThat(records(audit)).hasSize(4);
            for (var invocation : before) {
                var record = audit.getReader().get(invocation.getEventId()).orElseThrow();
                assertThat(record.toMap()).doesNotContainKey("input").containsEntry("inputOmitted", "redaction_failed");
            }
            assertThat(records(audit).subList(0, 2)).allSatisfy(record -> assertThat(record.toMap())
                    .containsEntry("input", "safe").containsEntry("inputRedacted", true));
        } finally {
            release.countDown();
        }
    }

    @Test
    void stoppedContextDoesNotReuseThePreviousRedactor() throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind("old", (SemanticAuditInputRedactor) input -> "old policy");
            var audit = SemanticAuditService.get(context);
            audit.configure("test", configuration(true, new SemanticAuditInputConfiguration(true, 100, "old")));
            context.start();
            audit.activate();
            context.stop();
            context.getRegistry().bind("new", (SemanticAuditInputRedactor) input -> "new policy");
            audit.configure("test", configuration(true, new SemanticAuditInputConfiguration(true, 100, "new")));
            var before = audit.begin(null, null, evaluation("security"), null, "security", null, null, null);
            before.captureInput("private");
            context.start();
            before.complete("success", "evaluation_completed", null);
            var after = audit.begin(null, null, evaluation("security"), null, "security", null, null, null);
            after.captureInput("private");
            after.complete("success", "evaluation_completed", null);
            audit.stop();
            assertThat(records(audit)).hasSize(2);
            assertThat(audit.getReader().get(before.getEventId()).orElseThrow().toMap())
                    .doesNotContainKey("input").containsEntry("inputOmitted", "redaction_failed");
            assertThat(audit.getReader().get(after.getEventId()).orElseThrow().toMap())
                    .containsEntry("input", "new policy").containsEntry("inputRedacted", true);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "snapshot_assertion", "snapshot_linkage", "redactor_assertion", "redactor_linkage" })
    void captureErrorsOmitInputWithoutChangingTheEvaluation(String scenario) throws Exception {
        try (var context = new DefaultCamelContext()) {
            Error failure = scenario.endsWith("assertion")
                    ? new AssertionError("private diagnostic")
                    : new LinkageError("private diagnostic");
            context.getRegistry().bind("security", new StructuredExpert());
            context.getRegistry().bind("redactor", (SemanticAuditInputRedactor) input -> {
                throw failure;
            });
            boolean redaction = scenario.startsWith("redactor");
            var audit = SemanticAuditService.get(context);
            audit.configure("test", configuration(true,
                    new SemanticAuditInputConfiguration(true, 100, redaction ? "redactor" : null)));
            context.start();
            Object input = redaction ? "private" : new AbstractList<>() {
                @Override
                public Object get(int index) {
                    throw failure;
                }

                @Override
                public int size() {
                    return 1;
                }
            };
            var result = ((SemanticLanguage) context.resolveLanguage("semantic"))
                    .evaluate(new SemanticEvaluation("check", "security", null, Map.of()), input);
            assertThat(result.getValue()).isEqualTo(true);
            audit.stop();
            assertThat(records(audit)).singleElement().satisfies(record -> assertThat(record.toMap())
                    .containsEntry("status", "success").doesNotContainKey("input")
                    .containsEntry("inputOmitted", redaction ? "redaction_failed" : "snapshot_limit_or_unsupported_type"));
        }
    }

    @Test
    void nestedSnapshotsEnforceTheSharedNodeBudget() {
        var fullBudget = List.of(Collections.nCopies(998, ""));
        assertThat(SemanticAuditInputSnapshot.copy(fullBudget, 100)).isEqualTo(fullBudget);
        assertThatThrownBy(() -> SemanticAuditInputSnapshot.copy(List.of(Collections.nCopies(998, ""), ""), 100))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("snapshot limit");
    }

    @Test
    void unknownRedactorFailsStartup() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var audit = SemanticAuditService.get(context);
            audit.configure("test", configuration(true, new SemanticAuditInputConfiguration(true, 100, "missing")));
            audit.activate();
            assertThatThrownBy(context::start).hasMessageContaining("Unknown semantic audit input redactor: missing");
        }
    }

    private static SemanticAuditConfiguration configuration(boolean enabled, SemanticAuditInputConfiguration input) {
        return new SemanticAuditConfiguration(
                enabled, Map.of("security", enabled, "decisions", enabled),
                List.of("memory"), "memory", 100, 100, Map.of("security", input));
    }

    private static SemanticEvaluation evaluation(String expert) {
        return new SemanticEvaluation("boolean", expert, null, Map.of());
    }

    private static List<SemanticAuditRecord> records(SemanticAuditService audit) throws Exception {
        return audit.getReader().query(new SemanticAuditQuery(Map.of(), null, null, 200)).getRecords();
    }

    private static void yaml(DefaultCamelContext context, String input) throws Exception {
        String yaml
                = "- semantic:\n    audit:\n      enabled: true\n      experts:\n        security:\n          enabled: true\n          input: "
                  + input;
        var settings = LoadSettings.builder().setLabel("audit.yaml").build();
        try (var deserialization = new YamlDeserializationContext(settings)) {
            deserialization.setCamelContext(context);
            deserialization.setResource(ResourceHelper.fromString("audit.yaml", yaml));
            deserialization.start();
            deserialization.preParse(new Compose(settings).composeString(yaml).orElseThrow());
        }
    }

    @SemanticExpert(name = "structured", description = "Test", provider = "test", artifactId = "test",
                    operations = @SemanticOperation(name = "check", description = "Check", inputTypes = {
                            SemanticExpert.InputType.TEXT, SemanticExpert.InputType.STRUCTURED },
                                                    inputRequirements = "Text or structured state",
                                                    resultType = SemanticExpert.ResultType.BOOLEAN,
                                                    resultMeaning = "Test result"))
    static class StructuredExpert implements SemanticAdapter {
        public void validate(SemanticEvaluation evaluation) {
            SemanticCapabilities.from(getClass()).validate(evaluation);
        }

        public SemanticResult evaluate(SemanticEvaluation evaluation, Object state) {
            if (state instanceof Map<?, ?> map) {
                ((Map<String, Object>) map).put("mutated", true);
            }
            return new SemanticResult(true, null, null, null, Map.of());
        }
    }
}
