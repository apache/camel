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

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import org.apache.camel.semantic.SemanticExpert.ResultType;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.EventNotifierSupport;

/**
 * Bounded typed-result statistics for one producer-defined population. The owner explicitly supplies dimensions and
 * selection; no question/model identity, Boolean threshold, label meaning or series version is inferred. Register the
 * collector as an EventNotifier and optionally as a SemanticMetricSource for the shared management surfaces.
 */
public final class SemanticResultMetrics extends EventNotifierSupport implements SemanticMetricSource {
    private final String source;
    private final String meaning;
    private final Map<String, String> dimensions;
    private final ResultType type;
    private final Predicate<SemanticEvent> selection;
    private final int maxLabels;
    private final double[] bounds;
    private final long[] histogram;
    private final long[] probabilityHistogram = new long[10];
    private final Map<String, Long> labels = new LinkedHashMap<>();
    private String epoch = UUID.randomUUID().toString();
    private Instant from = Instant.now();
    private Instant to = from;
    private long completed;
    private long answers;
    private long failed;
    private long excluded;
    private long unavailable;
    private long lostLabels;
    private long empty;
    private long probabilities;
    private long timed;
    private BigDecimal durationSum = BigDecimal.ZERO;
    private Long durationMinimum;
    private Long durationMaximum;
    private BigDecimal sum = BigDecimal.ZERO;
    private BigDecimal minimum;
    private BigDecimal maximum;

    public SemanticResultMetrics(String source, String meaning, Map<String, String> dimensions, ResultType type,
                                 Predicate<SemanticEvent> selection, int maxLabels, double... scoreBounds) {
        setupIgnore(true);
        if (source == null || source.isBlank() || source.length() > 128 || meaning == null || meaning.isBlank()
                || meaning.length() > 256 || dimensions.size() > 16 || maxLabels < 2 || maxLabels > 64
                || scoreBounds.length > 64 || type == null || selection == null) {
            throw new IllegalArgumentException("Invalid typed metric declaration");
        }
        this.source = source;
        this.meaning = meaning;
        this.dimensions = Map.copyOf(dimensions);
        this.type = type;
        this.selection = selection;
        this.maxLabels = maxLabels;
        this.bounds = scoreBounds.clone();
        for (int i = 0; i < bounds.length; i++) {
            if (!Double.isFinite(bounds[i]) || i > 0 && bounds[i] <= bounds[i - 1]) {
                throw new IllegalArgumentException("Histogram bounds must be finite and increasing");
            }
        }
        histogram = new long[bounds.length + 1];
        snapshots(); // Validate the supplied descriptions and dimensions before events arrive.
    }

    @Override
    public boolean isEnabled(CamelEvent event) {
        return event instanceof SemanticEvent semantic && semantic.getKind() == SemanticEvent.Kind.EVALUATION_COMPLETED;
    }

    @Override
    public void notify(CamelEvent event) {
        if (!(event instanceof SemanticEvent semantic) || !isEnabled(event) || !selection.test(semantic)) {
            return;
        }
        collect(semantic.getData());
    }

    private synchronized void collect(Map<String, Object> data) {
        completed++;
        to = Instant.now();
        if (!"not_executed".equals(data.get("status")) && data.get("durationNanos") instanceof Number duration
                && duration.longValue() >= 0) {
            long nanos = duration.longValue();
            timed++;
            durationSum = durationSum.add(BigDecimal.valueOf(nanos));
            durationMinimum = durationMinimum == null ? nanos : Math.min(durationMinimum, nanos);
            durationMaximum = durationMaximum == null ? nanos : Math.max(durationMaximum, nanos);
        }
        if (!"success".equals(data.get("status"))) {
            if ("not_executed".equals(data.get("status"))) {
                excluded++;
            } else {
                failed++;
            }
            return;
        }
        if (!(data.get("semantics") instanceof Map<?, ?> semantics) || !type.name().equals(semantics.get("resultType"))
                || !(data.get("result") instanceof Map<?, ?> result) || !result.containsKey("value")) {
            unavailable++;
            return;
        }
        Object value = result.get("value");
        switch (type) {
            case BOOLEAN -> {
                if (!(value instanceof Boolean)) {
                    unavailable++;
                    return;
                }
                label(value.toString());
            }
            case CHOICE -> {
                if (!(value instanceof String selected)) {
                    unavailable++;
                    return;
                }
                label(selected);
            }
            case CLASSIFICATION -> {
                if (!(value instanceof List<?> selected) || selected.stream().anyMatch(item -> !(item instanceof String))) {
                    unavailable++;
                    return;
                }
                if (selected.isEmpty()) {
                    empty++;
                }
                selected.stream().distinct().map(String.class::cast).forEach(this::label);
            }
            case SCORE -> {
                if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
                    unavailable++;
                    return;
                }
                BigDecimal score = new BigDecimal(number.toString());
                sum = sum.add(score);
                minimum = minimum == null ? score : minimum.min(score);
                maximum = maximum == null ? score : maximum.max(score);
                int bucket = 0;
                while (bucket < bounds.length && number.doubleValue() > bounds[bucket]) {
                    bucket++;
                }
                histogram[bucket]++;
            }
        }
        answers++;
        if (result.get("probability") instanceof Number probability) {
            double valueProbability = probability.doubleValue();
            if (Double.isFinite(valueProbability) && valueProbability >= 0 && valueProbability <= 1) {
                probabilityHistogram[Math.min(9, (int) (valueProbability * 10))]++;
                probabilities++;
            }
        }
    }

    private void label(String value) {
        if (labels.containsKey(value) || labels.size() < maxLabels && value.length() <= 256) {
            labels.merge(value, 1L, Long::sum);
        } else {
            lostLabels++;
        }
    }

    @Override
    public synchronized List<SemanticMonitoringSnapshot> snapshots() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("completed", completed);
        values.put("answers", answers);
        values.put("failed", failed);
        values.put("notExecuted", excluded);
        values.put("unavailable", unavailable);
        values.put("labelMembershipLoss", lostLabels);
        values.put("providerAttempts", "not_provided");
        values.put("durationCount", timed);
        if (timed > 0) {
            values.put("meanDurationNanos", durationSum.divide(BigDecimal.valueOf(timed), MathContext.DECIMAL64));
            values.put("minimumDurationNanos", durationMinimum);
            values.put("maximumDurationNanos", durationMaximum);
        }
        if (type == ResultType.SCORE) {
            values.put("sum", sum);
            if (minimum != null) {
                values.put("minimum", minimum);
                values.put("maximum", maximum);
            }
            values.put("histogramUpperBounds", Arrays.stream(bounds).boxed().toList());
            values.put("histogramCounts", Arrays.stream(histogram).boxed().toList());
            values.put("histogramMeaning", "Upper bounds inclusive; final bucket is above the last bound");
        } else {
            List<Map<String, Object>> distribution = new ArrayList<>();
            labels.forEach((label, count) -> distribution.add(Map.of("label", label, "count", count,
                    "denominator", answers, "proportion", answers == 0 ? 0D : count.doubleValue() / answers)));
            values.put("distribution", distribution);
            if (type == ResultType.CLASSIFICATION) {
                values.put("emptySets", empty);
                values.put("distributionMeaning", "Per-label membership per assessment; proportions need not sum to one");
            }
        }
        values.put("reportedProbabilityCount", probabilities);
        values.put("reportedProbabilityHistogram", Arrays.stream(probabilityHistogram).boxed().toList());
        values.put("probabilityHistogramMeaning",
                "Ten width-0.1 buckets; lower bounds inclusive; 1 belongs to the final bucket");
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", source);
        snapshot.put("source", source);
        snapshot.put("name", type.name() + " result distribution");
        snapshot.put("meaning", meaning);
        snapshot.put("kind", "typed_results");
        snapshot.put("unit", "assessments");
        snapshot.put("values", values);
        snapshot.put("dimensions", dimensions);
        snapshot.put("epoch", epoch);
        snapshot.put("from", from.toString());
        snapshot.put("to", to.toString());
        snapshot.put("availability", "available");
        snapshot.put("denominator", answers);
        snapshot.put("exclusions", excluded + failed + unavailable);
        snapshot.put("loss", lostLabels);
        return List.of(new SemanticMonitoringSnapshot(snapshot));
    }

    @Override
    protected synchronized void doStart() {
        epoch = UUID.randomUUID().toString();
        from = Instant.now();
        to = from;
        completed = answers = failed = excluded = unavailable = lostLabels = empty = probabilities = 0;
        timed = 0;
        durationSum = BigDecimal.ZERO;
        durationMinimum = durationMaximum = null;
        sum = BigDecimal.ZERO;
        minimum = maximum = null;
        labels.clear();
        Arrays.fill(histogram, 0);
        Arrays.fill(probabilityHistogram, 0);
    }
}
