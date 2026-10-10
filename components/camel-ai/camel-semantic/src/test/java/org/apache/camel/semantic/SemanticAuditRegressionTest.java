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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dsl.yaml.common.YamlDeserializationContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.language.semantic.SemanticLanguage;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.lowlevel.Compose;
import org.snakeyaml.engine.v2.exceptions.MarkedYamlEngineException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticAuditRegressionTest {
    @TempDir
    Path directory;

    @Test
    void deletedAuditResourceDoesNotPoisonReloadAndCanMoveWithoutChangingActiveSettings() throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind("security", new FixedSemanticExpert());
            Path file = Files.createFile(directory.resolve("Original.java"));
            RouteBuilder original = declarations(context, file);
            context.addRoutes(original);
            context.start();
            var audit = SemanticAudit.get(context);
            var config = audit.getConfiguration();
            Files.delete(file);
            for (int i = 0; i < 2; i++) {
                context.addRoutes(new RouteBuilder() {
                    public void configure() {
                    }
                });
            }
            assertThat(SemanticEvaluations.get(context).snapshot()).isEmpty();
            assertThat(audit.getConfiguration()).isEqualTo(config);
            Path renamed = Files.createFile(directory.resolve("Renamed.java"));
            context.addRoutes(declarations(context, renamed));
            assertThat(SemanticEvaluations.get(context).snapshot()).containsKey("check");
            Files.delete(renamed);
            SemanticEvaluations.get(context).removeDeletedResources();
            assertThat(SemanticEvaluations.get(context).snapshot()).isEmpty();
            assertThatThrownBy(() -> audit.configure("different.yaml", SemanticAuditConfiguration.DISABLED))
                    .hasMessageContaining("restart");
        }
    }

    private RouteBuilder declarations(DefaultCamelContext context, Path path) {
        var builder = new RouteBuilder() {
            public void configure() {
                SemanticEvaluationsBuilder.semanticEvaluations(this).audit(auditConfiguration())
                        .expert("security").evaluation("check").operation("boolean").register();
            }
        };
        builder.setResource(ResourceHelper.resolveResource(context, path.toUri().toString()));
        return builder;
    }

    @Test
    void preStartInvocationDoesNotFreezeConfigurationAndLateObserversAreExplicitlyStartupOnly() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var audit = SemanticAudit.get(context);
            audit.decision(null, Map.of("action", "continue"), List.of());
            assertThat(audit.isStarted()).isFalse();
            audit.configure("audit.yaml", auditConfiguration());
            AtomicInteger before = new AtomicInteger();
            AtomicInteger after = new AtomicInteger();
            context.getRegistry().bind("before", decisionObserver(before));
            context.start();
            context.getRegistry().bind("after", decisionObserver(after));
            audit.decision(null, Map.of("action", "allow"), List.of());
            audit.stop();
            assertThat(before).hasValue(1);
            assertThat(after).hasValue(0);
            assertThat(audit.getReader().query(query()).getRecords()).hasSize(1);
            assertThatThrownBy(() -> audit.configure("audit.yaml", SemanticAuditConfiguration.DISABLED))
                    .hasMessageContaining("restart");
        }
    }

    @Test
    void slowReaderStartupCannotBlockRouteObservers() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var reader = new SlowReader();
            context.getRegistry().bind("reader", reader);
            var audit = SemanticAudit.get(context);
            audit.configure("test", new SemanticAuditConfiguration(false, Map.of(), List.of("memory"), "reader", 10, 10));
            var decisions = new AtomicInteger();
            context.getRegistry().bind("observer", decisionObserver(decisions));
            context.start();
            var pool = Executors.newFixedThreadPool(2);
            try {
                var reading = pool.submit(audit::getReader);
                assertThat(reader.entered.await(5, TimeUnit.SECONDS)).isTrue();
                pool.submit(() -> audit.decision(null, Map.of("action", "allow"), List.of())).get(2, TimeUnit.SECONDS);
                assertThat(decisions).hasValue(1);
                reader.release.countDown();
                assertThat(reading.get(5, TimeUnit.SECONDS)).isSameAs(reader);
            } finally {
                reader.release.countDown();
                pool.shutdownNow();
            }
        }
    }

    @Test
    void capturesStaticProviderAndAllowlistedModelIdentityWithSeparateStartAndCompletionTimes() throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind("security", new FixedSemanticExpert() {
                public SemanticResult evaluate(SemanticEvaluation evaluation, Object input) {
                    return new SemanticResult(
                            true, null, null, null, Map.of(
                                    "provider", "untrusted-override", "model", "detector-v2", "revision", "abc123",
                                    "prompt", "secret", "credentials", "secret"));
                }
            });
            var start = new AtomicReference<SemanticAuditRecord>();
            context.getRegistry().bind("observer", (SemanticObserver) (record, parent) -> {
                start.set(record);
                return completed -> {
                };
            });
            var audit = SemanticAudit.get(context);
            audit.configure("test", auditConfiguration());
            context.start();
            ((SemanticLanguage) context.resolveLanguage("semantic"))
                    .evaluate(new SemanticEvaluation("boolean", "security", null, Map.of()), "secret");
            audit.stop();
            var captured = audit.getReader().query(query()).getRecords().get(0);
            assertThat(captured.toMap()).containsEntry("provider", "test").containsEntry("model", "detector-v2")
                    .containsEntry("revision", "abc123").doesNotContainKeys("prompt", "credentials", "metadata");
            assertThat(captured.text("startedAt")).isEqualTo(start.get().text("timestamp"));
            assertThat(Instant.parse(captured.text("timestamp"))).isAfterOrEqualTo(Instant.parse(captured.text("startedAt")));
            assertThat(SemanticAuditRecord.fromMap(captured.toMap()).toMap()).isEqualTo(captured.toMap());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "bad\nmodel", "", "OVERSIZED" })
    void omitsInvalidModelIdentityWithoutTruncatingIt(String model) throws Exception {
        String value = "OVERSIZED".equals(model) ? "x".repeat(257) : model;
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind("security", new FixedSemanticExpert() {
                public SemanticResult evaluate(SemanticEvaluation evaluation, Object input) {
                    return new SemanticResult(true, null, null, null, Map.of("model", value, "revision", 123));
                }
            });
            var audit = SemanticAudit.get(context);
            audit.configure("test", auditConfiguration());
            context.start();
            ((SemanticLanguage) context.resolveLanguage("semantic"))
                    .evaluate(new SemanticEvaluation("boolean", "security", null, Map.of()), "text");
            audit.stop();
            assertThat(audit.getReader().query(query()).getRecords().get(0).toMap()).doesNotContainKeys("model", "revision");
        }
    }

    @Test
    void declarationRejectedDuringPredicateUseStillSuppliesDecisionEvidence() throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind("choice", new ChoiceExpert());
            var audit = SemanticAudit.get(context);
            audit.configure("test", auditConfiguration());
            SemanticEvaluations.get(context).replace("definitions",
                    Map.of("check", new SemanticEvaluation("choice", "choice", null, Map.of())));
            context.start();
            var exchange = new DefaultExchange(context);
            exchange.getMessage().setBody("text");
            Predicate predicate = (Predicate) context.resolveLanguage("semantic").createExpression("ref:check");
            assertThatThrownBy(() -> predicate.matches(exchange)).hasMessageContaining("boolean");
            var decision = new SemanticAuditDecision();
            decision.setFields(Map.of("action", "review", "reasonCode", "invalid_declaration"));
            decision.process(exchange);
            audit.stop();
            var records = audit.getReader().query(query()).getRecords();
            assertThat(records).hasSize(2);
            assertThat(records.get(0).toMap()).containsEntry("evidence", List.of(records.get(1).getEventId()));
            assertThat(records.get(1).toMap()).containsEntry("reasonCode", "invalid_declaration");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "capacity: 0", "capacity: abc", "queueCapacity: 100001", "sinks: memory" })
    void yamlAuditErrorsCarryTheSourcePosition(String setting) throws Exception {
        try (var context = new DefaultCamelContext()) {
            String yaml = "- semantic:\n    audit:\n      " + setting + "\n";
            var settings = LoadSettings.builder().setLabel("audit-invalid.yaml").build();
            try (var dc = new YamlDeserializationContext(settings)) {
                dc.setCamelContext(context);
                dc.setResource(ResourceHelper.fromString("audit-invalid.yaml", yaml));
                dc.start();
                assertThatThrownBy(() -> dc.preParse(new Compose(settings).composeString(yaml).orElseThrow()))
                        .isInstanceOf(MarkedYamlEngineException.class).hasMessageContaining("line 3");
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "capacity='0'", "capacity='abc'", "queueCapacity='100001'" })
    void xmlCapacityErrorsNameTheOptionAndResource(String setting) throws Exception {
        try (var context = new DefaultCamelContext()) {
            assertThatThrownBy(() -> PluginHelper.getRoutesLoader(context).loadRoutes(ResourceHelper.fromString("invalid.xml",
                    "<semantic><audit " + setting + "/></semantic>")))
                    .hasMessageContaining(setting.substring(0, setting.indexOf('='))).hasMessageContaining("invalid.xml")
                    .hasMessageContaining("between 1 and 100000");
        }
    }

    @Test
    void xmlMissingEnabledNamesTheExpert() throws Exception {
        try (var context = new DefaultCamelContext()) {
            assertThatThrownBy(() -> PluginHelper.getRoutesLoader(context).loadRoutes(ResourceHelper.fromString("invalid.xml",
                    "<semantic><audit><expert name='security'/></audit></semantic>")))
                    .hasMessageContaining("security").hasMessageContaining("enabled");
        }
    }

    private static SemanticObserver decisionObserver(AtomicInteger counter) {
        return new SemanticObserver() {
            public Observation started(SemanticAuditRecord record, Object parent) {
                return null;
            }

            public void decision(SemanticAuditRecord record) {
                counter.incrementAndGet();
            }
        };
    }

    private static SemanticAuditConfiguration auditConfiguration() {
        return new SemanticAuditConfiguration(true, Map.of(), List.of("memory"), "memory", 20, 20);
    }

    private static SemanticAuditQuery query() {
        return new SemanticAuditQuery(Map.of(), null, null, 20);
    }

    private static class SlowReader extends ServiceSupport implements SemanticAuditReader {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        protected void doStart() throws Exception {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS))
                throw new IllegalStateException("reader not released");
        }

        public SemanticAuditPage query(SemanticAuditQuery query) {
            return new SemanticAuditPage(List.of(), null, 0, false);
        }

        public Optional<SemanticAuditRecord> get(String id) {
            return Optional.empty();
        }
    }

    @SemanticExpert(name = "choice", description = "Test choice", provider = "test", artifactId = "test",
                    operations = @SemanticOperation(name = "choice", description = "Choose",
                                                    inputTypes = SemanticExpert.InputType.TEXT,
                                                    inputRequirements = "Text", resultType = SemanticExpert.ResultType.CHOICE,
                                                    resultMeaning = "Chosen label"))
    private static class ChoiceExpert implements SemanticAdapter {
        public void validate(SemanticEvaluation evaluation) {
            SemanticCapabilities.from(getClass()).validate(evaluation);
        }

        public SemanticResult evaluate(SemanticEvaluation evaluation, Object input) {
            return new SemanticResult("a", null, null, null, null);
        }
    }
}
