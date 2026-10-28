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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.camel.semantic.SemanticExpert.ResultType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticResultMetricsTest {
    @Test
    void countsReturnedBooleanWithoutApplyingAnotherThreshold() {
        var metrics = metrics(ResultType.BOOLEAN);
        metrics.notify(event(ResultType.BOOLEAN, Map.of("value", false, "probability", 0.99), "success"));
        metrics.notify(event(ResultType.BOOLEAN, Map.of("value", true, "probability", 0.2), "success"));
        metrics.notify(event(ResultType.BOOLEAN, Map.of(), "failed"));
        metrics.notify(event(ResultType.BOOLEAN, Map.of(), "success"));
        var values = values(metrics);
        assertThat(values).containsEntry("answers", 2L).containsEntry("completed", 4L)
                .containsEntry("failed", 1L).containsEntry("unavailable", 1L).containsEntry("reportedProbabilityCount", 2L);
        assertThat(values.get("distribution")).isEqualTo(List.of(
                Map.of("label", "false", "count", 1L, "denominator", 2L, "proportion", 0.5),
                Map.of("label", "true", "count", 1L, "denominator", 2L, "proportion", 0.5)));
    }

    @Test
    void countsSelectedChoicesOnceAndPreservesTheOwnersSeriesAcrossReportedRevisions() {
        var metrics = metrics(ResultType.CHOICE);
        var completed = event(ResultType.CHOICE,
                Map.of("value", "billing", "probabilities", Map.of("billing", 0.1, "other", 0.9)), "success");
        metrics.notify(new SemanticEvent(SemanticEvent.Kind.EVALUATION_STARTED, completed.getData()));
        metrics.notify(completed);
        Map<String, Object> later = new LinkedHashMap<>(completed.getData());
        later.put("revision", "changed-revision");
        later.put("eventId", "second-member");
        metrics.notify(new SemanticEvent(SemanticEvent.Kind.EVALUATION_COMPLETED, later));
        assertThat(values(metrics)).containsEntry("completed", 2L).containsEntry("answers", 2L);
        assertThat(values(metrics).get("distribution")).isEqualTo(List.of(
                Map.of("label", "billing", "count", 2L, "denominator", 2L, "proportion", 1D)));
        assertThat(metrics.snapshots()).hasSize(1);
        assertThat(metrics.snapshots().get(0).toMap()).containsEntry("dimensions",
                Map.of("taskId", "billing-task", "taskVersion", "owner-v1"));
    }

    @Test
    void preservesFractionalScoresInAggregatesAndBoundedNumericHistograms() {
        var metrics = metrics(ResultType.SCORE);
        metrics.notify(event(ResultType.SCORE, Map.of("value", 1.6), "success"));
        metrics.notify(event(ResultType.SCORE, Map.of("value", 0.4), "success"));
        var values = values(metrics);
        assertThat(values.get("sum").toString()).isEqualTo("2.0");
        assertThat(values.get("minimum").toString()).isEqualTo("0.4");
        assertThat(values.get("maximum").toString()).isEqualTo("1.6");
        assertThat(values.get("histogramCounts")).isEqualTo(List.of(1L, 0L, 1L));
        assertThat(values).containsEntry("answers", 2L);
    }

    @Test
    void classificationCountsMembershipAndEmptySetsAndReportsCardinalityLoss() {
        var metrics = metrics(ResultType.CLASSIFICATION);
        metrics.notify(event(ResultType.CLASSIFICATION, Map.of("value", List.of("pii", "secrets")), "success"));
        metrics.notify(event(ResultType.CLASSIFICATION, Map.of("value", List.of()), "success"));
        metrics.notify(event(ResultType.CLASSIFICATION, Map.of("value", List.of("third")), "success"));
        metrics.notify(event(ResultType.CLASSIFICATION, Map.of(), "not_executed"));
        assertThat(values(metrics)).containsEntry("answers", 3L).containsEntry("completed", 4L)
                .containsEntry("emptySets", 1L).containsEntry("notExecuted", 1L).containsEntry("labelMembershipLoss", 1L);
        assertThat((List<?>) values(metrics).get("distribution")).hasSize(2);
    }

    @Test
    void expertAndDefinitionPopulationsRetainIndependentCountsAndTimings() {
        var expert = new SemanticResultMetrics(
                "security", "All injection calls", Map.of("aggregation", "expert"),
                ResultType.BOOLEAN, e -> "security".equals(e.getData().get("expert")), 2);
        var prompt = new SemanticResultMetrics(
                "prompt", "Prompt assessments", Map.of("definition", "screenPrompt"),
                ResultType.BOOLEAN, e -> "screenPrompt".equals(e.getData().get("definition")), 2);
        var context = new SemanticResultMetrics(
                "context", "Context assessments", Map.of("definition", "screenContext"),
                ResultType.BOOLEAN, e -> "screenContext".equals(e.getData().get("definition")), 2);
        for (var sample : List.of(Map.of("definition", "screenPrompt", "durationNanos", 1000000L, "status", "success"),
                Map.of("definition", "screenPrompt", "durationNanos", 3000000L, "status", "failed"),
                Map.of("definition", "screenContext", "durationNanos", 8000000L, "status", "success"),
                Map.of("durationNanos", 12000000L, "status", "success"))) {
            Map<String, Object> data
                    = new LinkedHashMap<>(event(ResultType.BOOLEAN, Map.of("value", true), "success").getData());
            data.putAll(sample);
            data.put("expert", "security");
            SemanticEvent event = new SemanticEvent(SemanticEvent.Kind.EVALUATION_COMPLETED, data);
            for (var metrics : List.of(expert, prompt, context)) {
                metrics.notify(event);
            }
        }
        assertThat(values(expert)).containsEntry("completed", 4L).containsEntry("answers", 3L).containsEntry("failed", 1L);
        assertThat(values(prompt)).containsEntry("completed", 2L).containsEntry("answers", 1L).containsEntry("failed", 1L);
        assertThat(values(context)).containsEntry("completed", 1L).containsEntry("answers", 1L).containsEntry("failed", 0L);
        assertThat(values(expert).get("meanDurationNanos").toString()).isEqualTo("6000000");
        assertThat(values(prompt).get("meanDurationNanos").toString()).isEqualTo("2000000");
        assertThat(values(context).get("meanDurationNanos").toString()).isEqualTo("8000000");
    }

    private static SemanticResultMetrics metrics(ResultType type) {
        return new SemanticResultMetrics(
                "owner", "Answers for the explicitly selected population",
                Map.of("taskId", "billing-task", "taskVersion", "owner-v1"), type, event -> true, 2, 0.5, 1.5);
    }

    @Test
    void measuresRecordedExecutionDurationsWithoutTreatingMissingOrSkippedCallsAsZero() {
        var metrics = metrics(ResultType.CHOICE);
        assertThat(values(metrics)).containsEntry("durationCount", 0L).doesNotContainKey("meanDurationNanos");
        for (var sample : List.of(Map.of("status", "success", "durationNanos", 1000000L),
                Map.of("status", "failed", "durationNanos", 3000000L),
                Map.of("status", "not_executed", "durationNanos", 0L))) {
            Map<String, Object> data = new LinkedHashMap<>(
                    event(ResultType.CHOICE,
                            Map.of("value", "billing"), sample.get("status").toString()).getData());
            data.putAll(sample);
            metrics.notify(new SemanticEvent(SemanticEvent.Kind.EVALUATION_COMPLETED, data));
        }
        metrics.notify(event(ResultType.CHOICE, Map.of("value", "billing"), "success"));
        var values = values(metrics);
        assertThat(values).containsEntry("durationCount", 2L).containsEntry("minimumDurationNanos", 1000000L)
                .containsEntry("maximumDurationNanos", 3000000L).containsEntry("completed", 4L);
        assertThat(values.get("meanDurationNanos").toString()).isEqualTo("2000000");
    }

    @Test
    void restartBeginsANewEpochWithoutPreviousDurations() {
        var metrics = metrics(ResultType.CHOICE);
        metrics.start();
        String previousEpoch = (String) metrics.snapshots().get(0).toMap().get("epoch");
        Map<String, Object> data = new LinkedHashMap<>(
                event(ResultType.CHOICE, Map.of("value", "billing"), "success").getData());
        data.put("durationNanos", 10L);
        metrics.notify(new SemanticEvent(SemanticEvent.Kind.EVALUATION_COMPLETED, data));
        assertThat(values(metrics)).containsEntry("durationCount", 1L);
        metrics.stop();
        metrics.start();
        try {
            assertThat(metrics.snapshots().get(0).toMap().get("epoch")).isNotEqualTo(previousEpoch);
            assertThat(values(metrics)).containsEntry("completed", 0L).containsEntry("durationCount", 0L)
                    .doesNotContainKeys("meanDurationNanos", "minimumDurationNanos", "maximumDurationNanos");
            data.put("durationNanos", 20L);
            metrics.notify(new SemanticEvent(SemanticEvent.Kind.EVALUATION_COMPLETED, data));
            assertThat(values(metrics)).containsEntry("durationCount", 1L)
                    .containsEntry("minimumDurationNanos", 20L).containsEntry("maximumDurationNanos", 20L);
            assertThat(values(metrics).get("meanDurationNanos").toString()).isEqualTo("20");
        } finally {
            metrics.stop();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> values(SemanticResultMetrics metrics) {
        return (Map<String, Object>) metrics.snapshots().get(0).toMap().get("values");
    }

    private static SemanticEvent event(ResultType type, Map<String, Object> result, String status) {
        Map<String, Object> data = new LinkedHashMap<>(
                Map.of("schemaVersion", 1, "eventId", UUID.randomUUID().toString(),
                        "timestamp", "2026-10-13T00:00:00Z", "category", "evaluation", "status", status,
                        "semantics", Map.of("resultType", type.name())));
        if (!result.isEmpty()) {
            data.put("result", result);
        }
        return new SemanticEvent(SemanticEvent.Kind.EVALUATION_COMPLETED, data);
    }
}
