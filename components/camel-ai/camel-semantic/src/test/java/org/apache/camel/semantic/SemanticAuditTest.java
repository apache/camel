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
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.console.DevConsole;
import org.apache.camel.dsl.yaml.common.YamlDeserializationContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.language.semantic.SemanticLanguage;
import org.apache.camel.semantic.internal.SemanticAuditService;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.lowlevel.Compose;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class SemanticAuditTest {
    @ParameterizedTest
    @CsvSource({
            "false,inherit,false", "false,true,true", "false,false,false", "true,inherit,true", "true,true,true",
            "true,false,false" })
    void expertOverridesMasterWithoutChangingInference(boolean master, String override, boolean expected) throws Exception {
        try (var context = new DefaultCamelContext()) {
            FixedSemanticExpert expert = new FixedSemanticExpert();
            context.getRegistry().bind("security", expert);
            SemanticAuditService audit = SemanticAuditService.get(context);
            audit.configure("test", configuration(master,
                    override.equals("inherit") ? Map.of() : Map.of("security", Boolean.valueOf(override))));
            context.start();
            SemanticLanguage language = (SemanticLanguage) context.resolveLanguage("semantic");
            assertThat(language.evaluate(evaluation("security"), "secret input").getValue()).isEqualTo(true);
            audit.stop();
            assertThat(records(audit)).hasSize(expected ? 1 : 0);
            assertThat(records(audit)).allSatisfy(record -> assertThat(record.toMap())
                    .doesNotContainKeys("exchangeId", "breadcrumbId"));
            assertThat(expert.calls).isEqualTo(1);
        }
    }

    @Test
    void telemetryObservesWhenAuditIsDisabledAndCarriesParentAcrossConsoleExecutor() throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind("security", new FixedSemanticExpert());
            Object parent = new Object();
            AtomicInteger completions = new AtomicInteger();
            context.getRegistry().bind("telemetry", new SemanticObserver() {
                public Object captureContext() {
                    return parent;
                }

                public Observation started(SemanticAuditRecord record, Object captured) {
                    assertThat(captured).isSameAs(parent);
                    return completed -> {
                        assertThat(completed.getStatus()).isEqualTo("success");
                        completions.incrementAndGet();
                    };
                }
            });
            context.start();
            SemanticEvaluateConsole console = new SemanticEvaluateConsole();
            console.setCamelContext(context);
            try {
                var result = console.call(DevConsole.MediaType.JSON,
                        Map.of("expert", "security", "operation", "boolean", "input", "private"));
                assertThat(((Map<?, ?>) result).get("status")).isEqualTo("success");
                assertThat(completions).hasValue(1);
                SemanticAuditService.get(context).stop();
                assertThat(records(SemanticAuditService.get(context))).isEmpty();
            } finally {
                console.stop();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "refs:a,b", "refs:b,a" })
    void batchingAliasesUsesEachDeclarationsEffectiveExpert(String expression) throws Exception {
        try (var context = new DefaultCamelContext()) {
            var expert = new FixedSemanticExpert();
            context.getRegistry().bind("recorded", expert);
            context.getRegistry().bind("excluded", expert);
            var language = (SemanticLanguage) context.resolveLanguage("semantic");
            language.setDefaultExpert("excluded");
            var audit = SemanticAuditService.get(context);
            audit.configure("test", configuration(true, Map.of("excluded", false, "recorded", true)));
            SemanticEvaluations.get(context).replace("definitions", Map.of("a", evaluation("recorded"), "b", evaluation(null)));
            context.start();
            var exchange = new DefaultExchange(context);
            exchange.getMessage().setBody("text");
            assertThat(language.createExpression(expression).evaluate(exchange, Map.class)).hasSize(2);
            assertThat(expert.batches).hasSize(1);
            audit.stop();
            assertThat(records(audit)).hasSize(1);
            assertThat(records(audit).get(0).getExpert()).isEqualTo("recorded");
            assertThat(records(audit).get(0).text("definition")).isEqualTo("a");
        }
    }

    @Test
    void namedBatchKeepsProviderBatchingAndExplicitDecisionLinks() throws Exception {
        try (var context = new DefaultCamelContext()) {
            FixedSemanticExpert expert = new FixedSemanticExpert();
            context.getRegistry().bind("security", expert);
            var audit = SemanticAuditService.get(context);
            audit.configure("audit", configuration(true, Map.of()));
            SemanticEvaluations.get(context).replace("definitions",
                    Map.of("first", evaluation("security"), "second", evaluation("security")));
            context.start();
            var exchange = new DefaultExchange(context);
            exchange.getMessage().setBody("private");
            context.resolveLanguage("semantic").createExpression("refs:first,second").evaluate(exchange, Map.class);
            assertThat(expert.batches).containsExactly(List.of("first", "second"));
            SemanticAuditDecision decision = new SemanticAuditDecision();
            decision.setFields(Map.of("action", "block", "reasonCode", "prompt_injection", "operation", "tools/call", "target",
                    "support-request"));
            decision.process(exchange);
            audit.stop();
            List<SemanticAuditRecord> records = records(audit);
            assertThat(records).hasSize(3);
            assertThat(records).allSatisfy(record -> assertThat(record.text("exchangeId")).isEqualTo(exchange.getExchangeId()));
            assertThat(records).filteredOn(r -> r.getCategory().equals("evaluation")).allSatisfy(r -> {
                assertThat(r.toMap()).doesNotContainKey("action");
                assertThat(r.getStatus()).isEqualTo("success");
            });
            assertThat(records.get(0).toMap().get("evidence")).isEqualTo(exchange.getProperty(SemanticAuditService.REFERENCES));
            assertThat(records.get(0).text("action")).isEqualTo("block");
            assertThat(records.get(1).text("batchId")).isEqualTo(records.get(2).text("batchId"));
            assertThat(records.get(1).getInvocationId()).isNotEqualTo(records.get(2).getInvocationId());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void splitEvaluationsAndDecisionsShareTheCamelGeneratedBreadcrumb(boolean enabled) throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.setUseBreadcrumb(enabled);
            context.getRegistry().bind("security", new FixedSemanticExpert());
            var audit = SemanticAuditService.get(context);
            audit.configure("audit", configuration(true, Map.of()));
            SemanticEvaluations.get(context).replace("definitions", Map.of("check", evaluation("security")));
            var expression = context.resolveLanguage("semantic").createExpression("ref:check");
            var decision = new SemanticAuditDecision();
            decision.setFields(Map.of("action", "allow", "correlationId", "application-request"));
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:audit").routeId("audited-route")
                            .split(body())
                            .process(exchange -> expression.evaluate(exchange, Boolean.class))
                            .process(decision);
                }
            });
            context.start();
            try (var producer = context.createProducerTemplate()) {
                Exchange exchange = producer.request("direct:audit", e -> e.getMessage().setBody(List.of("one", "two")));
                assertThat(exchange.getException()).isNull();
                String breadcrumb = exchange.getMessage().getHeader(Exchange.BREADCRUMB_ID, String.class);
                assertThat(breadcrumb).isEqualTo(enabled ? exchange.getExchangeId() : null);
                audit.stop();
                List<SemanticAuditRecord> records = records(audit);
                assertThat(records).hasSize(4).allSatisfy(record -> {
                    assertThat(record.text("breadcrumbId")).isEqualTo(breadcrumb);
                    assertThat(record.text("exchangeId")).isNotBlank().isNotEqualTo(exchange.getExchangeId());
                    assertThat(record.text("routeId")).isEqualTo("audited-route");
                });
                assertThat(records.stream().map(record -> record.text("exchangeId")).distinct()).hasSize(2);
                assertThat(records).filteredOn(record -> "decision".equals(record.getCategory())).allSatisfy(record -> {
                    assertThat(record.text("correlationId")).isEqualTo("application-request");
                    assertThat(record.getEvidence()).hasSize(1);
                    var evidence = audit.getReader().get(record.getEvidence().get(0)).orElseThrow();
                    assertThat(evidence.text("exchangeId")).isEqualTo(record.text("exchangeId"));
                    assertThat(evidence.text("breadcrumbId")).isEqualTo(breadcrumb);
                });
                assertThat(audit.getReader().query(new SemanticAuditQuery(
                        Map.of("breadcrumbId", exchange.getExchangeId()), null, null, 10)).getRecords())
                        .hasSize(enabled ? 4 : 0);
            }
        }
    }

    @Test
    void invalidBreadcrumbHeadersAreOmittedWithoutConvertingThemOrChangingInference() throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind("security", new FixedSemanticExpert());
            var audit = SemanticAuditService.get(context);
            audit.configure("audit", configuration(true, Map.of()));
            SemanticEvaluations.get(context).replace("definitions", Map.of("check", evaluation("security")));
            context.start();
            var expression = context.resolveLanguage("semantic").createExpression("ref:check");
            Object unsupported = new Object() {
                @Override
                public String toString() {
                    throw new AssertionError("A breadcrumb must not convert arbitrary headers");
                }
            };
            List<Object> invalid = List.of(unsupported, 42, " ", "request\n123", "x".repeat(257));
            for (Object breadcrumb : invalid) {
                var exchange = new DefaultExchange(context);
                exchange.getMessage().setBody("sample");
                exchange.getMessage().setHeader(Exchange.BREADCRUMB_ID, breadcrumb);
                assertThat(expression.evaluate(exchange, Boolean.class)).isTrue();
                SemanticAudit.get(context).decision(exchange, Map.of("action", "allow"), List.of());
            }
            audit.stop();
            assertThat(records(audit)).hasSize(invalid.size() * 2)
                    .allSatisfy(record -> assertThat(record.toMap()).doesNotContainKey("breadcrumbId"));
        }
    }

    @Test
    void capturesFailuresAndUnexecutedGroupsWithoutRawExceptions() throws Exception {
        try (var context = new DefaultCamelContext()) {
            FixedSemanticExpert first = new FixedSemanticExpert();
            first.fail = true;
            FixedSemanticExpert second = new FixedSemanticExpert();
            context.getRegistry().bind("first", first);
            context.getRegistry().bind("second", second);
            var audit = SemanticAuditService.get(context);
            audit.configure("audit", configuration(true, Map.of()));
            SemanticEvaluations.get(context).replace("definitions",
                    Map.of("a", evaluation("first"), "b", evaluation("second")));
            context.start();
            var exchange = new DefaultExchange(context);
            exchange.getMessage().setBody("secret");
            var expression = context.resolveLanguage("semantic").createExpression("refs:a,b");
            assertThatThrownBy(() -> expression.evaluate(exchange, Map.class)).hasMessageContaining("provider unavailable");
            audit.stop();
            assertThat(records(audit)).hasSize(2).anySatisfy(r -> assertThat(r.getStatus()).isEqualTo("not_executed"))
                    .anySatisfy(r -> assertThat(r.text("reasonCode")).isEqualTo("provider_error"));
            assertThat(second.calls).isZero();
            assertThat(records(audit).toString()).doesNotContain("secret", "provider unavailable");
        }
    }

    @Test
    void safeSnapshotSurvivesObserverAndSinkFailures() throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind("security", new FixedSemanticExpert() {
                public SemanticResult evaluate(SemanticEvaluation evaluation, Object state) {
                    return new SemanticResult(true, 0.9, null, null, Map.of("input", state, "token", "private-token"));
                }
            });
            context.getRegistry().bind("broken", new BrokenSink());
            context.getRegistry().bind("observer", (SemanticObserver) (record, parent) -> {
                throw new AssertionError("private-observer-error");
            });
            var audit = SemanticAuditService.get(context);
            audit.configure("test",
                    new SemanticAuditConfiguration(true, Map.of(), List.of("broken", "memory"), "memory", 10, 10));
            context.start();
            var language = (SemanticLanguage) context.resolveLanguage("semantic");
            assertThat(language.evaluate(evaluation("security"), "secret-body").getValue()).isEqualTo(true);
            audit.stop();
            var record = records(audit).get(0);
            assertThat(new JsonObject(record.toMap()).toJson()).doesNotContain("secret-body", "private-token",
                    "private-observer-error", "metadata");
            assertThat(audit.status()).containsEntry("observerErrors", 1L);
            assertThat(((Map<?, ?>) audit.status().get("sinkErrors")).get("broken")).isEqualTo(1L);
            assertThatThrownBy(() -> record.toMap().put("body", "bad")).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test
    void boundedHistoryHasStablePagesAndReportsEviction() throws Exception {
        var store = new MemorySemanticAuditStore(3);
        for (int i = 0; i < 3; i++) {
            store.append(record("e" + i));
        }
        var first = store.query(new SemanticAuditQuery(Map.of(), null, null, 2));
        assertThat(first.getRecords()).extracting(SemanticAuditRecord::getEventId).containsExactly("e2", "e1");
        store.append(record("e3"));
        var next = store.query(new SemanticAuditQuery(Map.of(), null, first.getNextCursor(), 2));
        assertThat(next.getRecords()).isEmpty();
        assertThat(next.isCursorExpired()).isTrue();
        assertThat(next.getEvicted()).isEqualTo(1);
        assertThat(store.get("e0")).isEmpty();
        assertThatThrownBy(
                () -> store.query(new SemanticAuditQuery(Map.of("expert", "security"), null, first.getNextCursor(), 2)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void yamlDeclaresMasterAndTwoExpertOverridesAndRejectsConflictingReload() throws Exception {
        String yaml = """
                - semantic:
                    audit:
                      enabled: true
                      experts:
                        security: {enabled: true}
                        decisions: {enabled: false}
                      sinks: [memory, log]
                      capacity: 25
                    evaluation:
                      check: {expert: security, operation: boolean}
                """;
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind("security", new FixedSemanticExpert());
            preParse(context, yaml);
            var audit = SemanticAuditService.get(context);
            assertThat(audit.getConfiguration().isEnabled("security")).isTrue();
            assertThat(audit.getConfiguration().isEnabled("decisions")).isFalse();
            context.start();
            assertThatThrownBy(() -> preParse(context, yaml.replace("enabled: true", "enabled: false")))
                    .hasMessageContaining("restart");
            assertThat(audit.getConfiguration().isEnabled()).isTrue();
            assertThat(SemanticEvaluations.get(context).get("check")).isNotNull();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void timeoutAndLateProviderCompletionRespectExpertOverride(boolean enabled) throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(1);
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind("security", new FixedSemanticExpert() {
                public SemanticResult evaluate(SemanticEvaluation evaluation, Object state) {
                    entered.countDown();
                    boolean interrupted = false;
                    while (true) {
                        try {
                            if (!release.await(5, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("test timed out");
                            }
                            break;
                        } catch (InterruptedException e) {
                            interrupted = true;
                        }
                    }
                    if (interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    return new SemanticResult(true, 0.9, null, null, null);
                }
            });
            var audit = SemanticAuditService.get(context);
            audit.configure("test", configuration(true, Map.of("security", enabled)));
            context.start();
            SemanticEvaluateConsole console = new SemanticEvaluateConsole();
            console.setCamelContext(context);
            try {
                var answer = console.call(DevConsole.MediaType.JSON,
                        Map.of("expert", "security", "operation", "boolean", "input", "secret", "timeout", 100));
                assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(((Map<?, ?>) answer).get("status")).isEqualTo("failed");
                release.countDown();
                if (enabled) {
                    await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(records(audit)).hasSize(2));
                }
                console.stop();
                audit.stop();
                var records = records(audit);
                if (!enabled) {
                    assertThat(records).isEmpty();
                    return;
                }
                assertThat(records).hasSize(2);
                assertThat(records).extracting(SemanticAuditRecord::getCategory).containsExactlyInAnyOrder("evaluation",
                        "request");
                assertThat(records.get(0).getInvocationId()).isEqualTo(records.get(1).getInvocationId());
                assertThat(records).anySatisfy(r -> assertThat(r.getStatus()).isEqualTo("success"));
            } finally {
                release.countDown();
                console.stop();
            }
        } finally {
            release.countDown();
        }
    }

    @ParameterizedTest
    @CsvSource({
            "true,security", "false,security", "true,{{expert.name}}", "false,{{expert.name}}", "true,default",
            "false,default" })
    void invalidDeclarationsResolveExpertPlaceholdersBeforeAuditFiltering(boolean enabled, String expert) throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind("security", new FixedSemanticExpert());
            Properties properties = new Properties();
            properties.put("expert.name", "security");
            context.getPropertiesComponent().setInitialProperties(properties);
            var audit = SemanticAuditService.get(context);
            audit.configure("test", configuration(true, Map.of("security", enabled)));
            context.start();
            var language = (SemanticLanguage) context.resolveLanguage("semantic");
            language.setDefaultExpert("security");
            assertThatThrownBy(() -> language.evaluate(
                    new SemanticEvaluation("unknown", "default".equals(expert) ? null : expert, null, Map.of()), "text"))
                    .isInstanceOf(IllegalArgumentException.class);
            audit.stop();
            assertThat(records(audit)).hasSize(enabled ? 1 : 0);
            if (enabled) {
                assertThat(records(audit).get(0).getExpert()).isEqualTo("security");
            }
        }
    }

    @Test
    void timeoutDuringExpertResolutionCannotBypassAnExclusion() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind("security", new FixedSemanticExpert() {
                public void validate(SemanticEvaluation evaluation) {
                    entered.countDown();
                    try {
                        if (!release.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("test timed out");
                        }
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(failure);
                    }
                    super.validate(evaluation);
                }
            });
            var audit = SemanticAuditService.get(context);
            audit.configure("test", configuration(true, Map.of("security", false)));
            context.start();
            var console = new SemanticEvaluateConsole();
            console.setCamelContext(context);
            try {
                console.call(DevConsole.MediaType.JSON,
                        Map.of("expert", "security", "operation", "boolean", "input", "secret", "timeout", 100));
                assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
                release.countDown();
                console.stop();
                audit.stop();
                assertThat(records(audit)).isEmpty();
            } finally {
                release.countDown();
                console.stop();
            }
        } finally {
            release.countDown();
        }
    }

    @Test
    void removedDefinitionIsNotAttributedToTheDefaultExpert() throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind("security", new FixedSemanticExpert());
            var audit = SemanticAuditService.get(context);
            audit.configure("test", configuration(true, Map.of()));
            var definitions = SemanticEvaluations.get(context);
            definitions.replace("definitions", Map.of("check", evaluation("security")));
            context.start();
            var language = (SemanticLanguage) context.resolveLanguage("semantic");
            language.setDefaultExpert("security");
            var expression = language.createExpression("ref:check");
            definitions.replace("definitions", Map.of());
            var exchange = new DefaultExchange(context);
            exchange.getMessage().setBody("text");
            assertThatThrownBy(() -> expression.evaluate(exchange, Boolean.class))
                    .hasMessageContaining("Unknown semantic evaluation");
            audit.stop();
            assertThat(records(audit)).isEmpty();
        }
    }

    @Test
    void disabledCaptureDoesNotStartSinksOrCreateEvidence() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var sink = new BrokenSink();
            context.getRegistry().bind("unused", sink);
            context.getRegistry().bind("security", new FixedSemanticExpert());
            var audit = SemanticAuditService.get(context);
            audit.configure("test",
                    new SemanticAuditConfiguration(false, Map.of(), List.of("unused", "memory"), "memory", 10, 10));
            SemanticEvaluations.get(context).replace("definitions", Map.of("check", evaluation("security")));
            context.start();
            var exchange = new DefaultExchange(context);
            exchange.getMessage().setBody("text");
            assertThat(context.resolveLanguage("semantic").createExpression("ref:check").evaluate(exchange, Boolean.class))
                    .isTrue();
            assertThat(exchange.getProperty(SemanticAuditService.REFERENCES)).isNull();
            assertThat(sink.isStarted()).isFalse();
            assertThat(audit.status()).containsEntry("dropped", 0L);
        }
    }

    @Test
    void detachedBatchObservationsKeepTheSameParentAndDoNotLeakThreadContext() throws Exception {
        ThreadLocal<Object> ambient = new ThreadLocal<>();
        Object parent = new Object();
        ambient.set(parent);
        List<Object> parents = new ArrayList<>();
        AtomicInteger completed = new AtomicInteger();
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind("security", new FixedSemanticExpert() {
                public Map<String, SemanticResult> evaluateBatch(Map<String, SemanticEvaluation> evaluations, Object state)
                        throws Exception {
                    assertThat(ambient.get()).isSameAs(parent);
                    return super.evaluateBatch(evaluations, state);
                }
            });
            context.getRegistry().bind("observer", new SemanticObserver() {
                public Object captureContext() {
                    return ambient.get();
                }

                public Observation started(SemanticAuditRecord record, Object captured) {
                    parents.add(captured);
                    Object previous = ambient.get();
                    ambient.set(record.getInvocationId());
                    try {
                        return result -> {
                            assertThat(ambient.get()).isSameAs(parent);
                            completed.incrementAndGet();
                        };
                    } finally {
                        ambient.set(previous);
                    }
                }
            });
            SemanticEvaluations.get(context).replace("definitions",
                    Map.of("a", evaluation("security"), "b", evaluation("security")));
            context.start();
            var exchange = new DefaultExchange(context);
            exchange.getMessage().setBody("text");
            context.resolveLanguage("semantic").createExpression("refs:a,b").evaluate(exchange, Map.class);
            assertThat(parents).containsExactly(parent, parent);
            assertThat(completed).hasValue(2);
            assertThat(ambient.get()).isSameAs(parent);
        } finally {
            ambient.remove();
        }
    }

    @Test
    void fullQueueDropsNewRecordsAndDrainsAcceptedRecordsOnStop() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind("security", new FixedSemanticExpert());
            context.getRegistry().bind("slow", new BrokenSink() {
                public void append(SemanticAuditRecord record) {
                    entered.countDown();
                    try {
                        assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(failure);
                    }
                }
            });
            var audit = SemanticAuditService.get(context);
            audit.configure("test", new SemanticAuditConfiguration(true, Map.of(), List.of("slow", "memory"), "memory", 10, 1));
            context.start();
            var language = (SemanticLanguage) context.resolveLanguage("semantic");
            language.evaluate(evaluation("security"), "one");
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            language.evaluate(evaluation("security"), "two");
            language.evaluate(evaluation("security"), "three");
            assertThat(audit.status()).containsEntry("dropped", 1L);
            release.countDown();
            audit.stop();
            assertThat(records(audit)).hasSize(2);
        } finally {
            release.countDown();
        }
    }

    @Test
    void persistedSnapshotsRoundTripAndRejectUnexpectedData() throws Exception {
        Map<String, Object> fields = new LinkedHashMap<>(
                Map.of("schemaVersion", 1, "eventId", "e1", "category", "evaluation",
                        "timestamp", Instant.now().toString(), "result",
                        new LinkedHashMap<>(Map.of("value", List.of("A", "B"), "probabilities", Map.of("A", 0.7)))));
        var copy = SemanticAuditRecord.fromMap((Map<String, ?>) Jsoner.deserialize(new JsonObject(fields).toJson()));
        fields.clear();
        assertThat(copy.getEventId()).isEqualTo("e1");
        assertThatThrownBy(() -> ((Map<String, Object>) copy.toMap().get("result")).put("body", "secret"))
                .isInstanceOf(UnsupportedOperationException.class);
        var invalid = new LinkedHashMap<>(copy.toMap());
        invalid.put("result", Map.of("metadata", "secret"));
        assertThatThrownBy(() -> SemanticAuditRecord.fromMap(invalid)).isInstanceOf(IllegalArgumentException.class);
        invalid.put("result", Map.of("value", true));
        invalid.put("schemaVersion", 1.5);
        assertThatThrownBy(() -> SemanticAuditRecord.fromMap(invalid)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void javaAndXmlConfigureTheSameMasterAndExpertOverrides() throws Exception {
        for (boolean xml : List.of(true, false)) {
            try (var context = new DefaultCamelContext()) {
                context.getRegistry().bind("security", new FixedSemanticExpert());
                if (xml) {
                    context.build();
                    PluginHelper.getRoutesLoader(context).loadRoutes(ResourceHelper.fromString("audit.xml",
                            """
                                    <semantic xmlns="http://camel.apache.org/schema/semantic">
                                      <audit enabled="false" capacity="20"><expert name="security" enabled="true"/><sink ref="memory"/></audit>
                                      <evaluation name="check" expert="security" operation="boolean"/>
                                    </semantic>
                                    """));
                } else {
                    context.addRoutes(new RouteBuilder() {
                        public void configure() {
                            SemanticEvaluationsBuilder.semanticEvaluations(this)
                                    .audit(SemanticAuditTest.configuration(false, Map.of("security", true)))
                                    .evaluation("check").expert("security").operation("boolean").end().register();
                        }
                    });
                }
                context.start();
                var exchange = new DefaultExchange(context);
                exchange.getMessage().setBody("text");
                assertThat(context.resolveLanguage("semantic").createExpression("ref:check").evaluate(exchange, Boolean.class))
                        .isTrue();
                var audit = SemanticAuditService.get(context);
                audit.stop();
                assertThat(records(audit)).hasSize(1);
                assertThat(audit.getConfiguration().isEnabled()).isFalse();
            }
        }
    }

    private static SemanticAuditConfiguration configuration(boolean enabled, Map<String, Boolean> experts) {
        return new SemanticAuditConfiguration(enabled, experts, List.of("memory"), "memory", 100, 100);
    }

    private static SemanticEvaluation evaluation(String expert) {
        return new SemanticEvaluation("boolean", expert, null, Map.of());
    }

    private static List<SemanticAuditRecord> records(SemanticAuditService audit) throws Exception {
        return audit.getReader().query(new SemanticAuditQuery(Map.of(), null, null, 200)).getRecords();
    }

    private static SemanticAuditRecord record(String id) {
        return new SemanticAuditRecord(Map.of("eventId", id, "timestamp", Instant.now().toString(), "category", "evaluation"));
    }

    private static void preParse(DefaultCamelContext context, String yaml) throws Exception {
        var settings = LoadSettings.builder().setLabel("audit.yaml").build();
        try (var deserialization = new YamlDeserializationContext(settings)) {
            deserialization.setCamelContext(context);
            deserialization.setResource(ResourceHelper.fromString("audit.yaml", yaml));
            deserialization.start();
            deserialization.preParse(new Compose(settings).composeString(yaml).orElseThrow());
        }
    }

    private static class BrokenSink extends ServiceSupport implements SemanticAuditSink {
        public void append(SemanticAuditRecord record) {
            throw new IllegalStateException("secret-sink-error");
        }
    }
}
