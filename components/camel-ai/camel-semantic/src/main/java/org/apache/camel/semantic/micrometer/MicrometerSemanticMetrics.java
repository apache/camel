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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.apache.camel.semantic.SemanticEvent;
import org.apache.camel.semantic.SemanticExpert.ResultType;
import org.apache.camel.semantic.SemanticMonitoringEvent;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Optional Micrometer instrumentation of completed semantic assessment members. Register one notifier with the Camel
 * management strategy, supplying the application's registry. The registry remains application-owned; this notifier
 * removes its meters on stop. It never reads message input, audit history or a remote monitoring backend.
 *
 * @since 4.24
 */
public final class MicrometerSemanticMetrics extends EventNotifierSupport {
    private static final Logger LOG = LoggerFactory.getLogger(MicrometerSemanticMetrics.class);
    private static final String CAMEL_CONTEXT_TAG = "camelContext";
    private static final int MAX_POPULATIONS = 100;
    private static final int MAX_ANSWERS = 64;
    private static final Set<String> STATUSES = Set.of("success", "failed", "not_executed");
    private static final Set<String> UNITS = Set.of("1", "ratio", "bytes", "seconds", "milliseconds", "nanoseconds",
            "count", "requests", "assessments");
    private static final Set<String> DIMENSIONS = Set.of("expert", "definition", "operation", "provider",
            "taskId", "taskVersion", "revision");
    private final MeterRegistry registry;
    private final double[] scoreBounds;
    private final Map<Tags, Population> populations = new HashMap<>();
    private final Map<Tags, AtomicLong> measurements = new HashMap<>();
    private final Set<Meter> owned = new HashSet<>();
    private final AtomicLong errors = new AtomicLong();
    private Set<Meter> existing = Set.of();
    private Tags contextTags;
    private Counter populationLoss;
    private Counter answerLoss;
    private Counter invalidEvents;
    private Counter unsupportedMeasurements;

    /**
     * @param registry    application-owned registry, shared with the runtime's usual metrics endpoint
     * @param scoreBounds finite, increasing, inclusive score histogram bounds; negative bounds are supported. An empty
     *                    array exports count, sum, minimum and maximum without finite histogram buckets.
     */
    public MicrometerSemanticMetrics(MeterRegistry registry, double... scoreBounds) {
        setupIgnore(true);
        this.registry = Objects.requireNonNull(registry, "registry");
        this.scoreBounds = Objects.requireNonNull(scoreBounds, "scoreBounds").clone();
        if (scoreBounds.length > 64) {
            throw new IllegalArgumentException("At most 64 score bounds are supported");
        }
        for (int i = 0; i < scoreBounds.length; i++) {
            if (!Double.isFinite(scoreBounds[i]) || i > 0 && scoreBounds[i] <= scoreBounds[i - 1]) {
                throw new IllegalArgumentException("Score bounds must be finite and increasing");
            }
        }
    }

    @Override
    public boolean isEnabled(CamelEvent event) {
        return event instanceof SemanticEvent semantic && semantic.getKind() == SemanticEvent.Kind.EVALUATION_COMPLETED
                || event instanceof SemanticMonitoringEvent;
    }

    @Override
    public synchronized void notify(CamelEvent event) {
        if (!isStarted() || !isEnabled(event)) {
            return;
        }
        try {
            if (event instanceof SemanticEvent semantic) {
                record(semantic.getData());
            } else {
                measurement(((SemanticMonitoringEvent) event).getSnapshot().toMap());
            }
        } catch (Exception | AssertionError | LinkageError failure) {
            // Instrumentation must not change the assessment or expose payloads through exception logging.
            if (errors.incrementAndGet() == 1) {
                LOG.warn(
                        "Semantic metric collection failed ({}); further failures are counted in camel.semantic.collection.errors",
                        failure.getClass().getName());
            }
        }
    }

    private void measurement(Map<String, Object> data) {
        if ("typed_results".equals(data.get("kind"))) {
            return; // Assessment events are the only ingestion path for result statistics.
        }
        Map<?, ?> dimensions = data.get("dimensions") instanceof Map<?, ?> map ? map : Map.of();
        if (!"gauge".equals(data.get("kind")) || !UNITS.contains(text(data, "unit"))
                || !DIMENSIONS.containsAll(dimensions.keySet())) {
            unsupportedMeasurements.increment();
            return;
        }
        Tags tags = contextTags.and("source", text(data, "source"), "measurement", text(data, "id"),
                "unit", text(data, "unit"));
        // Every series has the same tag keys, including absent dimensions, as required by Prometheus.
        for (String dimension : DIMENSIONS) {
            tags = tags.and(dimension, text(dimensions, dimension));
        }
        AtomicLong current = measurements.get(tags);
        if (!(data.get("values") instanceof Map<?, ?> values) || values.size() != 1
                || !(values.get("value") instanceof Number number) || !Double.isFinite(number.doubleValue())
                || data.containsKey("availability") && !"available".equals(data.get("availability"))) {
            if (current != null) {
                current.set(Double.doubleToRawLongBits(Double.NaN));
            }
            unsupportedMeasurements.increment();
            return;
        }
        if (current == null) {
            if (measurements.size() >= MAX_POPULATIONS) {
                populationLoss.increment();
                return;
            }
            current = new AtomicLong();
            own(Gauge.builder("camel.semantic.expert.value", current, v -> Double.longBitsToDouble(v.get()))
                    .description("Expert-published current scalar; unit and measurement identify its meaning")
                    .tags(tags).register(registry));
            measurements.put(tags, current);
        }
        current.set(Double.doubleToRawLongBits(number.doubleValue()));
    }

    private void record(Map<String, Object> data) {
        String status = text(data, "status");
        if (!STATUSES.contains(status)) {
            invalidEvents.increment();
            return;
        }
        String type = data.get("semantics") instanceof Map<?, ?> semantics ? text(semantics, "resultType") : "";
        if (!Set.of("", "BOOLEAN", "CHOICE", "SCORE", "CLASSIFICATION").contains(type)) {
            invalidEvents.increment();
            return;
        }
        Tags tags = contextTags.and("expert", text(data, "expert"), "definition", text(data, "definition"),
                "operation", text(data, "operation"), "origin", text(data, "origin"), "result.type", type);
        Population population = populations.get(tags);
        if (population == null) {
            if (populations.size() >= MAX_POPULATIONS) {
                populationLoss.increment();
                return;
            }
            population = new Population(tags);
            populations.put(tags, population);
        }
        population.outcomes.computeIfAbsent(status,
                value -> counter("evaluations", tags.and("status", value))).increment();
        if (!"not_executed".equals(status) && data.get("durationNanos") instanceof Number duration
                && duration.longValue() >= 0) {
            population.durations.computeIfAbsent(status, value -> own(Timer.builder("camel.semantic.duration")
                    .description("Observed semantic assessment duration, including failed executions")
                    .tags(tags.and("status", value)).publishPercentileHistogram().register(registry)))
                    .record(duration.longValue(), TimeUnit.NANOSECONDS);
        }
        if (!"success".equals(status)) {
            return;
        }
        Object value = data.get("result") instanceof Map<?, ?> result ? result.get("value") : null;
        boolean available = switch (type) {
            case "BOOLEAN" -> value instanceof Boolean;
            case "CHOICE" -> value instanceof String;
            case "SCORE" -> value instanceof Number number && Double.isFinite(number.doubleValue());
            case "CLASSIFICATION" -> value instanceof List<?> list && list.stream().allMatch(String.class::isInstance);
            default -> false;
        };
        population.availability.computeIfAbsent(available ? "available" : "unavailable",
                state -> counter("result.availability", tags.and("availability", state))).increment();
        if (!available) {
            return;
        }
        switch (ResultType.valueOf(type)) {
            case BOOLEAN, CHOICE -> population.answer(value.toString());
            case CLASSIFICATION -> {
                var labels = (List<?>) value;
                if (labels.isEmpty()) {
                    if (population.empty == null) {
                        population.empty = counter("classification.empty", tags);
                    }
                    population.empty.increment();
                }
                labels.stream().distinct().map(String.class::cast).forEach(population::answer);
            }
            case SCORE -> population.score(((Number) value).doubleValue());
        }
    }

    private static String text(Map<?, ?> data, String key) {
        return data.get(key) instanceof String value ? value : "";
    }

    private Counter counter(String name, Tags tags) {
        return own(Counter.builder("camel.semantic." + name).tags(tags).register(registry));
    }

    private <T extends Meter> T own(T meter) {
        if (!existing.contains(meter)) {
            owned.add(meter);
        }
        return meter;
    }

    @Override
    protected synchronized void doStart() throws Exception {
        super.doStart();
        long collectors = getCamelContext().getManagementStrategy().getEventNotifiers().stream()
                .filter(MicrometerSemanticMetrics.class::isInstance).count();
        if (collectors > 1) {
            throw new IllegalStateException("Only one Semantic Micrometer collector may be registered per CamelContext");
        }
        existing = new HashSet<>(registry.getMeters());
        contextTags = Tags.of(CAMEL_CONTEXT_TAG, getCamelContext().getName());
        errors.set(0);
        own(Gauge.builder("camel.semantic.collection.errors", errors, AtomicLong::doubleValue)
                .description("Metric collection failures since this collector started").tags(contextTags).register(registry));
        populationLoss = counter("collection.loss", contextTags.and("reason", "population_limit"));
        answerLoss = counter("collection.loss", contextTags.and("reason", "answer_limit"));
        invalidEvents = counter("collection.loss", contextTags.and("reason", "invalid_event"));
        unsupportedMeasurements = counter("collection.loss", contextTags.and("reason", "unsupported_measurement"));
    }

    @Override
    protected synchronized void doStop() throws Exception {
        for (Meter meter : owned) {
            registry.remove(meter);
        }
        owned.clear();
        existing = Set.of();
        populations.clear();
        measurements.clear();
        super.doStop();
    }

    private final class Population {
        private final Tags tags;
        private final Map<String, Counter> outcomes = new HashMap<>();
        private final Map<String, Timer> durations = new HashMap<>();
        private final Map<String, Counter> availability = new HashMap<>();
        private final Map<String, Counter> answers = new HashMap<>();
        private final List<Counter> buckets = new ArrayList<>();
        private Counter empty;
        private Counter scores;
        private volatile double sum;
        private volatile double minimum = Double.NaN;
        private volatile double maximum = Double.NaN;

        private Population(Tags tags) {
            this.tags = tags;
        }

        private void answer(String answer) {
            Counter counter = answers.get(answer);
            if (counter == null) {
                if (answers.size() >= MAX_ANSWERS || answer.length() > 256) {
                    answerLoss.increment();
                    return;
                }
                counter = counter("results", tags.and("answer", answer));
                answers.put(answer, counter);
            }
            counter.increment();
        }

        private void score(double value) {
            if (scores == null) {
                Counter observations = counter("score.observations", tags);
                own(Gauge.builder("camel.semantic.score.sum", this, p -> p.sum).tags(tags).register(registry));
                own(Gauge.builder("camel.semantic.score.minimum", this, p -> p.minimum).tags(tags).register(registry));
                own(Gauge.builder("camel.semantic.score.maximum", this, p -> p.maximum).tags(tags).register(registry));
                List<Counter> registeredBuckets = new ArrayList<>();
                for (double bound : scoreBounds) {
                    registeredBuckets.add(counter("score.distribution", tags.and("le", Double.toString(bound))));
                }
                registeredBuckets.add(counter("score.distribution", tags.and("le", "+Inf")));
                buckets.addAll(registeredBuckets);
                scores = observations;
            }
            scores.increment();
            sum += value;
            minimum = Double.isNaN(minimum) ? value : Math.min(minimum, value);
            maximum = Double.isNaN(maximum) ? value : Math.max(maximum, value);
            for (int i = 0; i < scoreBounds.length; i++) {
                if (value <= scoreBounds[i]) {
                    buckets.get(i).increment();
                }
            }
            buckets.get(scoreBounds.length).increment();
        }
    }
}
