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
package org.apache.camel.semantic.micrometer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.language.semantic.SemanticLanguage;
import org.apache.camel.semantic.SemanticAdapter;
import org.apache.camel.semantic.SemanticAuditQuery;
import org.apache.camel.semantic.SemanticCapabilities;
import org.apache.camel.semantic.SemanticEvaluation;
import org.apache.camel.semantic.SemanticEvaluations;
import org.apache.camel.semantic.SemanticEvent;
import org.apache.camel.semantic.SemanticExpert;
import org.apache.camel.semantic.SemanticExpert.InputType;
import org.apache.camel.semantic.SemanticExpert.ResultType;
import org.apache.camel.semantic.SemanticMonitoringEvent;
import org.apache.camel.semantic.SemanticMonitoringSnapshot;
import org.apache.camel.semantic.SemanticOperation;
import org.apache.camel.semantic.SemanticResult;
import org.apache.camel.semantic.SemanticResultMetrics;
import org.apache.camel.semantic.internal.SemanticAuditService;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.EventHelper;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class MicrometerSemanticMetricsTest {
    @Test
    void rejectsInvalidHistogramConfiguration() {
        var registry = new SimpleMeterRegistry();
        try {
            for (double[] bounds : List.of(new double[] { 2, 1 }, new double[] { 1, 1 },
                    new double[] { Double.NaN }, new double[] { Double.POSITIVE_INFINITY }, new double[65])) {
                assertThatThrownBy(() -> new MicrometerSemanticMetrics(registry, bounds))
                        .isInstanceOf(IllegalArgumentException.class);
            }
        } finally {
            registry.close();
        }
    }

    @Test
    void realDirectAndBatchEvaluationsWorkWithoutAuditAndAlongsideLocalMonitoring() throws Exception {
        var registry = new SimpleMeterRegistry();
        try (AutoCloseable closeRegistry = registry::close; var context = new DefaultCamelContext()) {
            context.disableJMX();
            context.getManagementStrategy().addEventNotifier(new MicrometerSemanticMetrics(registry));
            context.getRegistry().bind("expert", new BooleanExpert());
            context.getRegistry().bind("local", new SemanticResultMetrics(
                    "local", "Boolean answers", Map.of(), ResultType.BOOLEAN, event -> true, 2));
            var evaluation = new SemanticEvaluation("containsText", "expert", null, Map.of());
            SemanticEvaluations.get(context).replace("test", Map.of("first", evaluation, "second", evaluation));
            context.start();
            var language = (SemanticLanguage) context.resolveLanguage("semantic");
            assertThat(language.evaluate(evaluation, "private-input").getValue()).isEqualTo(true);
            var exchange = new DefaultExchange(context);
            exchange.getMessage().setBody("batch input");
            assertThat(language.createExpression("refs:first,second").evaluate(exchange, Map.class)).hasSize(2);
            assertThat(registry.find("camel.semantic.evaluations").counters().stream().mapToDouble(c -> c.count()).sum())
                    .isEqualTo(3);
            assertThat(registry.get("camel.semantic.evaluations").tag("definition", "first").counter().count()).isEqualTo(1);
            assertThat(registry.get("camel.semantic.evaluations").tag("definition", "second").counter().count()).isEqualTo(1);
            assertThat(registry.get("camel.semantic.evaluations").tag("definition", "").tag("origin", "direct")
                    .counter().count()).isEqualTo(1);
            assertThat(SemanticAuditService.get(context).getReader().query(new SemanticAuditQuery(Map.of(), null, null, 10))
                    .getRecords()).isEmpty();
        }
    }

    @Test
    void suppliedScalarGaugeReplacesValuesAndRejectsUnsafeDimensionsAndAmbiguousAggregates() throws Exception {
        var registry = new SimpleMeterRegistry();
        try (AutoCloseable closeRegistry = registry::close; var context = new DefaultCamelContext()) {
            context.getManagementStrategy().addEventNotifier(new MicrometerSemanticMetrics(registry));
            context.start();
            for (int value : new int[] { 3, 5 }) {
                var snapshot = new SemanticMonitoringSnapshot(
                        Map.of(
                                "id", "queue", "source", "expert", "name", "Queue", "meaning", "Pending requests",
                                "kind", "gauge", "unit", "requests", "values", Map.of("value", value)));
                EventHelper.notifyAiEvent(context, () -> new SemanticMonitoringEvent(snapshot));
            }
            assertThat(registry.get("camel.semantic.expert.value").gauge().value()).isEqualTo(5);
            var unsafe = new SemanticMonitoringSnapshot(
                    Map.of(
                            "id", "queue", "source", "expert", "name", "Queue", "meaning", "Pending requests",
                            "kind", "gauge", "unit", "requests", "values", Map.of("value", 8),
                            "dimensions", Map.of("exchangeId", "private-id")));
            EventHelper.notifyAiEvent(context, () -> new SemanticMonitoringEvent(unsafe));
            assertThat(registry.get("camel.semantic.collection.loss").tag("reason", "unsupported_measurement")
                    .counter().count()).isEqualTo(1);
            assertThat(registry.find("camel.semantic.expert.value").gauges()).hasSize(1);
            assertThat(registry.getMeters()).allSatisfy(meter -> assertThat(meter.getId().getTags().toString())
                    .doesNotContain("private-id", "exchangeId"));
            var unavailable = new SemanticMonitoringSnapshot(
                    Map.of(
                            "id", "queue", "source", "expert", "name", "Queue", "meaning", "Pending requests",
                            "kind", "gauge", "unit", "requests", "availability", "unavailable"));
            EventHelper.notifyAiEvent(context, () -> new SemanticMonitoringEvent(unavailable));
            assertThat(registry.get("camel.semantic.expert.value").gauge().value()).isNaN();
        }
    }

    @Test
    void scoreCollectionRecoversAfterAnInitialMeterRegistrationFailure() throws Exception {
        var registry = new SimpleMeterRegistry();
        try (AutoCloseable closeRegistry = registry::close; var context = new DefaultCamelContext()) {
            var rejectOnce = new AtomicBoolean(true);
            registry.config().meterFilter(new MeterFilter() {
                @Override
                public Meter.Id map(Meter.Id id) {
                    if (id.getName().equals("camel.semantic.score.minimum") && rejectOnce.getAndSet(false)) {
                        throw new IllegalStateException("Temporary registration failure");
                    }
                    return id;
                }
            });
            context.getManagementStrategy().addEventNotifier(new MicrometerSemanticMetrics(registry, 0, 1, 2));
            context.start();
            send(context, event(ResultType.SCORE, Map.of("value", 0.4), "success", 1));
            send(context, event(ResultType.SCORE, Map.of("value", 1.6), "success", 1));
            assertThat(registry.get("camel.semantic.collection.errors").functionCounter().count()).isEqualTo(1);
            assertThat(registry.get("camel.semantic.score.observations").counter().count()).isEqualTo(1);
            assertThat(registry.get("camel.semantic.score.sum").gauge().value()).isEqualTo(1.6);
            assertThat(registry.get("camel.semantic.score.distribution").tag("le", "+Inf").counter().count()).isEqualTo(1);
        }
    }

    @Test
    void countsOnlyCompletionsAndUsesReturnedBooleanWithoutAnotherThreshold() throws Exception {
        var registry = new SimpleMeterRegistry();
        try (AutoCloseable closeRegistry = registry::close; var context = new DefaultCamelContext()) {
            var metrics = new MicrometerSemanticMetrics(registry);
            context.getManagementStrategy().addEventNotifier(metrics);
            context.disableJMX();
            context.start();
            var completed = event(ResultType.BOOLEAN, Map.of("value", false, "probability", 0.99), "success", 1000000);
            send(context, new SemanticEvent(SemanticEvent.Kind.EVALUATION_STARTED, completed.getData()));
            send(context, completed);
            send(context, event(ResultType.BOOLEAN, Map.of("value", true), "success", 3000000));
            send(context, event(ResultType.BOOLEAN, Map.of(), "failed", 5000000));
            send(context, event(ResultType.BOOLEAN, Map.of(), "success", -1));
            send(context, event(ResultType.BOOLEAN, Map.of(), "not_executed", 0));
            EventHelper.notifyAiEvent(context, () -> new SemanticMonitoringEvent(
                    new SemanticMonitoringSnapshot(
                            Map.of("id", "ignored", "source", "expert", "name", "Results", "meaning", "Counts",
                                    "kind", "typed_results", "unit", "assessments", "values", Map.of("completed", 100)))));
            assertThat(registry.get("camel.semantic.evaluations").tag("status", "success").counter().count()).isEqualTo(3);
            assertThat(registry.get("camel.semantic.evaluations").tag("status", "failed").counter().count()).isEqualTo(1);
            assertThat(registry.get("camel.semantic.results").tag("answer", "false").counter().count()).isEqualTo(1);
            assertThat(registry.get("camel.semantic.results").tag("answer", "true").counter().count()).isEqualTo(1);
            assertThat(registry.get("camel.semantic.result.availability").tag("availability", "unavailable")
                    .counter().count()).isEqualTo(1);
            var timers = registry.find("camel.semantic.duration").timers();
            assertThat(timers.stream().mapToLong(timer -> timer.count()).sum()).isEqualTo(3);
            assertThat(timers.stream().mapToDouble(timer -> timer.totalTime(TimeUnit.MILLISECONDS)).sum()).isEqualTo(9);
            assertThat(context.getCamelContextExtension().isEventNotificationApplicable()).isFalse();
        }
    }

    @Test
    void choicesCountSelectionsAndClassificationCountsMembershipAndEmptySets() throws Exception {
        var registry = new SimpleMeterRegistry();
        try (AutoCloseable closeRegistry = registry::close; var context = new DefaultCamelContext()) {
            context.getManagementStrategy().addEventNotifier(new MicrometerSemanticMetrics(registry));
            context.start();
            send(context, event(ResultType.CHOICE,
                    Map.of("value", "billing", "probabilities", Map.of("billing", 0.1, "other", 0.9)), "success", 1));
            send(context, event(ResultType.CLASSIFICATION, Map.of("value", List.of("pii", "secrets")), "success", 1));
            send(context, event(ResultType.CLASSIFICATION, Map.of("value", List.of()), "success", 1));
            assertThat(registry.get("camel.semantic.results").tag("answer", "billing").counter().count()).isEqualTo(1);
            assertThat(registry.find("camel.semantic.results").tag("answer", "other").counter()).isNull();
            assertThat(registry.get("camel.semantic.results").tag("answer", "pii").counter().count()).isEqualTo(1);
            assertThat(registry.get("camel.semantic.results").tag("answer", "secrets").counter().count()).isEqualTo(1);
            assertThat(registry.get("camel.semantic.classification.empty").counter().count()).isEqualTo(1);
        }
    }

    @Test
    void signedFractionalScoresAndObservedDurationsSurvivePrometheusExport() throws Exception {
        var registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        try (AutoCloseable closeRegistry = registry::close; var context = new DefaultCamelContext()) {
            context.getManagementStrategy().addEventNotifier(new MicrometerSemanticMetrics(registry, -1, 0, 1, 2));
            context.start();
            send(context, event(ResultType.SCORE, Map.of("value", -0.4), "success", 1000000));
            send(context, event(ResultType.SCORE, Map.of("value", 1.6), "success", 9000000));
            assertThat(registry.get("camel.semantic.score.sum").gauge().value()).isCloseTo(1.2, within(1e-12));
            assertThat(registry.get("camel.semantic.score.minimum").gauge().value()).isEqualTo(-0.4);
            assertThat(registry.get("camel.semantic.score.maximum").gauge().value()).isEqualTo(1.6);
            assertThat(registry.get("camel.semantic.score.observations").counter().count()).isEqualTo(2);
            assertThat(registry.get("camel.semantic.score.distribution").tag("le", "0.0").counter().count()).isEqualTo(1);
            assertThat(registry.get("camel.semantic.score.distribution").tag("le", "+Inf").counter().count()).isEqualTo(2);
            String scrape = registry.scrape();
            assertThat(scrape).contains("camel_semantic_duration_seconds_bucket", "camel_semantic_duration_seconds_count",
                    "camel_semantic_duration_seconds_sum", "camel_semantic_score_observations_total")
                    .doesNotContain("private-input", "eventId", "breadcrumbId", "invocationId");
            assertThat(scrape.lines().filter(line -> line.startsWith("camel_semantic_score_distribution_total{")))
                    .anySatisfy(line -> assertThat(line).contains("le=\"0.0\"", "camelContext=\"" + context.getName() + "\"")
                            .endsWith(" 1.0"));
            assertThat(registry.get("camel.semantic.duration").timer().totalTime(TimeUnit.MILLISECONDS)).isEqualTo(10);
        }
    }

    @Test
    void applicationCanLimitDurationHistogramBuckets() throws Exception {
        var registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        registry.config().meterFilter(new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
                if (id.getName().equals("camel.semantic.duration")) {
                    return DistributionStatisticConfig.builder().percentilesHistogram(false)
                            .serviceLevelObjectives(Duration.ofMillis(10).toNanos(), Duration.ofMillis(100).toNanos(),
                                    Duration.ofSeconds(1).toNanos(), Duration.ofSeconds(10).toNanos())
                            .build().merge(config);
                }
                return config;
            }
        });
        try (AutoCloseable closeRegistry = registry::close; var context = new DefaultCamelContext()) {
            context.getManagementStrategy().addEventNotifier(new MicrometerSemanticMetrics(registry));
            context.start();
            send(context, event(ResultType.SCORE, Map.of("value", 1), "success", 50000000));
            send(context, event(ResultType.SCORE, Map.of("value", 2), "success", 15000000000L));
            String scrape = registry.scrape();
            assertThat(scrape.lines().filter(line -> line.startsWith("camel_semantic_duration_seconds_bucket{")))
                    .hasSize(5);
            assertThat(scrape.lines().filter(line -> line.startsWith("camel_semantic_duration_seconds_bucket{")))
                    .anySatisfy(line -> assertThat(line).contains("le=\"0.1\"").endsWith(" 1"))
                    .anySatisfy(line -> assertThat(line).contains("le=\"+Inf\"").endsWith(" 2"));
            assertThat(registry.get("camel.semantic.duration").timer().count()).isEqualTo(2);
            assertThat(registry.get("camel.semantic.duration").timer().totalTime(TimeUnit.MILLISECONDS)).isEqualTo(15050);
        }
    }

    @Test
    void cardinalityIsBoundedAndLossIsVisible() throws Exception {
        var registry = new SimpleMeterRegistry();
        try (AutoCloseable closeRegistry = registry::close; var context = new DefaultCamelContext()) {
            context.getManagementStrategy().addEventNotifier(new MicrometerSemanticMetrics(registry));
            context.start();
            for (int i = 0; i < 70; i++) {
                send(context, event(ResultType.CHOICE, Map.of("value", "answer-" + i), "success", 1));
            }
            assertThat(registry.find("camel.semantic.results").counters()).hasSize(64);
            assertThat(registry.get("camel.semantic.collection.loss").tag("reason", "answer_limit").counter().count())
                    .isEqualTo(6);
            for (int i = 0; i < 110; i++) {
                var data = new LinkedHashMap<>(event(ResultType.BOOLEAN, Map.of("value", true), "success", 1).getData());
                data.put("definition", "definition-" + i);
                send(context, new SemanticEvent(SemanticEvent.Kind.EVALUATION_COMPLETED, data));
            }
            assertThat(registry.get("camel.semantic.collection.loss").tag("reason", "population_limit").counter().count())
                    .isEqualTo(11);
            assertThat(registry.find("camel.semantic.evaluations").counters()).hasSize(100);
        }
    }

    @Test
    void restartRemovesOnlyOwnedMetersAndNeverClosesTheApplicationRegistry() throws Exception {
        var registry = new SimpleMeterRegistry();
        try (AutoCloseable closeRegistry = registry::close; var context = new DefaultCamelContext()) {
            var unrelated = registry.counter("application.requests");
            unrelated.increment();
            var metrics = new MicrometerSemanticMetrics(registry);
            context.getManagementStrategy().addEventNotifier(metrics);
            context.start();
            send(context, event(ResultType.BOOLEAN, Map.of("value", true), "success", 1));
            context.stop();
            assertThat(registry.isClosed()).isFalse();
            assertThat(registry.getMeters()).containsExactly(unrelated);
            context.start();
            send(context, event(ResultType.BOOLEAN, Map.of("value", true), "success", 1));
            assertThat(registry.get("camel.semantic.evaluations").counter().count()).isEqualTo(1);
        }
    }

    @Test
    void registryFailureIsVisibleAndDoesNotEscapeTheCallback() throws Exception {
        Logger logger = (Logger) LogManager.getLogger(MicrometerSemanticMetrics.class);
        Level previous = logger.getLevel();
        List<LogEvent> messages = new ArrayList<>();
        var appender = new AbstractAppender("SemanticMetricsTest", null, null, true, Property.EMPTY_ARRAY) {
            public void append(LogEvent event) {
                messages.add(event.toImmutable());
            }
        };
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.WARN);
        var registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        try (AutoCloseable closeRegistry = registry::close; var context = new DefaultCamelContext()) {
            registry.config().meterFilter(new MeterFilter() {
                @Override
                public Meter.Id map(Meter.Id id) {
                    if (id.getName().equals("camel.semantic.duration")) {
                        throw new IllegalStateException("private-provider-error");
                    }
                    return id;
                }
            });
            var metrics = new MicrometerSemanticMetrics(registry);
            context.getManagementStrategy().addEventNotifier(metrics);
            context.start();
            var errors = registry.get("camel.semantic.collection.errors").functionCounter();
            assertThat(errors.count()).isZero();
            metrics.notify(event(ResultType.BOOLEAN, Map.of("value", true), "success", 1));
            metrics.notify(event(ResultType.BOOLEAN, Map.of("value", false), "success", 1));
            assertThat(errors.count()).isEqualTo(2);
            assertThat(registry.scrape()).contains("# TYPE camel_semantic_collection_errors_total counter");
            assertThat(registry.scrape().lines()
                    .filter(line -> line.startsWith("camel_semantic_collection_errors_total{")))
                    .singleElement().asString().matches(".*camelContext=\"[^\"]+\".*} 2(?:\\.0)?");
            assertThat(messages).singleElement().satisfies(message -> {
                assertThat(message.getLevel()).isEqualTo(Level.WARN);
                assertThat(message.getMessage().getFormattedMessage()).contains("java.lang.IllegalStateException")
                        .doesNotContain("private-provider-error", "private-input");
                assertThat(message.getThrown()).isNull();
            });
            context.stop();
            assertThat(registry.find("camel.semantic.collection.errors").meters()).isEmpty();
            context.start();
            var restartedErrors = registry.get("camel.semantic.collection.errors").functionCounter();
            assertThat(restartedErrors).isNotSameAs(errors);
            assertThat(restartedErrors.count()).isZero();
            metrics.notify(event(ResultType.BOOLEAN, Map.of("value", true), "success", 1));
            assertThat(restartedErrors.count()).isEqualTo(1);
            assertThat(messages).hasSize(2);
        } finally {
            logger.setLevel(previous);
            logger.removeAppender(appender);
            appender.stop();
        }
    }

    @Test
    void duplicateCollectorsAreRejectedBeforeTheyCanDoubleCount() throws Exception {
        var registry = new SimpleMeterRegistry();
        try (AutoCloseable closeRegistry = registry::close; var context = new DefaultCamelContext()) {
            context.getManagementStrategy().addEventNotifier(new MicrometerSemanticMetrics(registry));
            context.getManagementStrategy().addEventNotifier(new MicrometerSemanticMetrics(registry));
            assertThatThrownBy(context::start).hasMessageContaining("Semantic Micrometer");
        }
    }

    private static void send(DefaultCamelContext context, SemanticEvent event) {
        EventHelper.notifyAiEvent(context, () -> event);
    }

    private static SemanticEvent event(ResultType type, Map<String, Object> result, String status, long nanos) {
        Map<String, Object> data = new LinkedHashMap<>(
                Map.of(
                        "schemaVersion", 1, "eventId", UUID.randomUUID().toString(), "timestamp", "2026-11-12T12:00:00Z",
                        "category", "evaluation", "status", status, "expert", "test-expert", "origin", "direct",
                        "semantics", Map.of("resultType", type.name())));
        if (!result.isEmpty()) {
            data.put("result", result);
        }
        if (nanos >= 0) {
            data.put("durationNanos", nanos);
        }
        return new SemanticEvent(SemanticEvent.Kind.EVALUATION_COMPLETED, data);
    }

    @SemanticExpert(name = "text", description = "Checks for nonempty text", provider = "local", artifactId = "test",
                    operations = @SemanticOperation(name = "containsText", description = "Has text",
                                                    inputTypes = InputType.TEXT, inputRequirements = "Text",
                                                    resultType = ResultType.BOOLEAN, resultMeaning = "Contains text"))
    static class BooleanExpert implements SemanticAdapter {
        @Override
        public void validate(SemanticEvaluation evaluation) {
            SemanticCapabilities.from(getClass()).validate(evaluation);
        }

        @Override
        public SemanticResult evaluate(SemanticEvaluation evaluation, Object state) {
            return new SemanticResult(!state.toString().isBlank(), null, Map.of(), null, Map.of());
        }
    }

}
