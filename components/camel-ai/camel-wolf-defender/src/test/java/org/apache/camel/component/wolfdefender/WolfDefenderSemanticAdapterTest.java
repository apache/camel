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
package org.apache.camel.component.wolfdefender;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.semantic.SemanticCapabilities;
import org.apache.camel.semantic.SemanticEvaluation;
import org.apache.camel.semantic.SemanticExpert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WolfDefenderSemanticAdapterTest {
    static SemanticEvaluation evaluation() {
        return evaluation(null);
    }

    static SemanticEvaluation evaluation(String expert) {
        return new SemanticEvaluation("injection", expert, null, Map.of());
    }

    static WolfDefenderSemanticAdapter expert(WolfDefenderInference inference) {
        return new WolfDefenderSemanticAdapter() {
            @Override
            WolfDefenderInference createInference() {
                return inference;
            }
        };
    }

    @Test
    void capabilitiesAndValidationDoNotLoadArtifacts() {
        var expert = new WolfDefenderSemanticAdapter();
        expert.setModelDirectory("/missing/model");
        var capabilities = SemanticCapabilities.from(expert.getClass());
        assertThat(capabilities.getOperations()).containsOnlyKeys("injection");
        var operation = capabilities.operation("injection");
        assertThat(operation.getInputTypes()).containsExactly(SemanticExpert.InputType.TEXT);
        assertThat(operation.getResultType()).isEqualTo(SemanticExpert.ResultType.BOOLEAN);
        assertThat(operation.isProbability()).isTrue();
        expert.validate(evaluation());
        for (String parameter : List.of("instructions", "criteria")) {
            assertThatThrownBy(() -> expert.validate(new SemanticEvaluation(
                    "injection", null, null, Map.of(parameter, "unsupported"))))
                    .hasMessageContaining("Unknown parameter '" + parameter + "'");
        }
        for (String name : List.of("boolean", "choice", "score", "classify")) {
            assertThatThrownBy(() -> expert.validate(new SemanticEvaluation(name, null, null, Map.of())))
                    .hasMessageContaining("Unknown operation");
        }
        assertThatThrownBy(expert::start).hasMessageContaining("Model loading failed");
    }

    @Test
    void appliesExpertDecisionPolicyAndPreservesProbability() throws Exception {
        var inference = mock(WolfDefenderInference.class);
        when(inference.evaluate(anyString(), anyInt(), any())).thenReturn(0.03, 0.5, 0.5);
        var expert = expert(inference);
        expert.start();
        try {
            var benign = expert.evaluate(evaluation(), "benign text");
            assertThat(benign.getValue()).isEqualTo(false);
            assertThat(benign.getProbability()).isEqualTo(0.03);
            assertThat(benign.getConfidence()).isNull();
            assertThat(benign.getMetadata()).containsEntry("revision", WolfDefenderSemanticAdapter.MODEL_REVISION);
            assertThat(expert.evaluate(evaluation(), "boundary").getValue()).isEqualTo(true);
            var uncertain = new SemanticEvaluation("injection", null, null, Map.of("uncertainty", 0.1));
            assertThatThrownBy(() -> expert.evaluate(uncertain, "uncertain")).hasMessageContaining("uncertain");
        } finally {
            expert.stop();
        }
        verify(inference).close();
    }

    @Test
    void rejectsInvalidPolicyBeforeInference() throws Exception {
        var inference = mock(WolfDefenderInference.class);
        var expert = expert(inference);
        for (Map<String, Object> parameters : List.<Map<String, Object>> of(
                Map.of("threshold", -0.1), Map.of("threshold", 1.1), Map.of("threshold", Double.NaN),
                Map.of("threshold", Double.POSITIVE_INFINITY), Map.of("threshold", "0.5"),
                Map.of("uncertainty", -0.1), Map.of("uncertainty", 1.1), Map.of("uncertainty", Double.NaN),
                Map.of("uncertaintyPolicy", "ignore"), Map.of("threshold", 0.9, "uncertainty", 0.2),
                Map.of("threshold", 0.1, "uncertainty", 0.2))) {
            var definition = new SemanticEvaluation("injection", null, null, parameters);
            assertThatThrownBy(() -> expert.evaluate(definition, "text")).isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(inference);
    }

    @Test
    void uncertaintyEdgesAndNonMatchRetainTheOriginalProbability() throws Exception {
        var inference = mock(WolfDefenderInference.class);
        when(inference.evaluate(anyString(), anyInt(), any())).thenReturn(0.375, 0.625, 0.625, 0.626, 0.374);
        var expert = expert(inference);
        expert.start();
        try {
            var uncertain = new SemanticEvaluation("injection", null, null, Map.of("uncertainty", 0.125));
            assertThatThrownBy(() -> expert.evaluate(uncertain, "lower edge")).hasMessageContaining("uncertain");
            assertThatThrownBy(() -> expert.evaluate(uncertain, "upper edge")).hasMessageContaining("uncertain");
            var nonMatch = new SemanticEvaluation(
                    "injection", null, null,
                    Map.of("uncertainty", 0.125, "uncertaintyPolicy", "non-match"));
            var result = expert.evaluate(nonMatch, "upper edge");
            assertThat(result.getValue()).isEqualTo(false);
            assertThat(result.getProbability()).isEqualTo(0.625);
            assertThat(expert.evaluate(uncertain, "above band").getValue()).isEqualTo(true);
            assertThat(expert.evaluate(uncertain, "below band").getValue()).isEqualTo(false);
        } finally {
            expert.stop();
        }
    }

    @Test
    void rejectsMalformedProbabilityBeforeApplyingPolicy() throws Exception {
        var inference = mock(WolfDefenderInference.class);
        when(inference.evaluate(anyString(), anyInt(), any()))
                .thenReturn(Double.NaN, Double.POSITIVE_INFINITY, -0.1, 1.1);
        var expert = expert(inference);
        expert.start();
        try {
            for (int i = 0; i < 4; i++) {
                assertThatThrownBy(() -> expert.evaluate(evaluation(), "text")).hasMessageContaining("probability");
            }
        } finally {
            expert.stop();
        }
    }

    @Test
    void consecutiveCallsDoNotFailWhileTheWorkerReturns() throws Exception {
        var inference = mock(WolfDefenderInference.class);
        when(inference.evaluate(anyString(), anyInt(), any())).thenReturn(0.03);
        var expert = expert(inference);
        expert.start();
        try {
            for (int i = 0; i < 1000; i++) {
                assertThat(expert.evaluate(evaluation(), "text").getProbability()).isEqualTo(0.03);
            }
        } finally {
            expert.stop();
        }
    }

    @Test
    void rejectsInvalidInputBeforeInference() throws Exception {
        var inference = mock(WolfDefenderInference.class);
        var expert = expert(inference);
        expert.setMaxCharacters(4);
        expert.start();
        try {
            for (Object state : new Object[] { null, Map.of("text", "x"), 42, new StringBuilder("text"), "", "  ", "12345" }) {
                assertThatThrownBy(() -> expert.evaluate(evaluation(), state)).isInstanceOf(RuntimeException.class)
                        .satisfies(e -> assertThat(e.getMessage()).doesNotContain("12345"));
            }
        } finally {
            expert.stop();
        }
        verify(inference, never()).evaluate(anyString(), anyInt(), any());
    }

    @ParameterizedTest
    @ValueSource(ints = { -1, 0, 1, 2049 })
    void rejectsInvalidTokenLimitsWithoutLoading(int limit) {
        var inference = mock(WolfDefenderInference.class);
        var expert = expert(inference);
        expert.setMaxTokens(limit);
        assertThatThrownBy(expert::start).hasMessageContaining("maxTokens in [2,2048]");
        verifyNoInteractions(inference);
    }

    @Test
    void errorsCannotLeakTextOrBecomeBenignDecisions() throws Exception {
        var inference = mock(WolfDefenderInference.class);
        when(inference.evaluate(anyString(), anyInt(), any()))
                .thenThrow(new IllegalArgumentException("private submitted text"));
        var expert = expert(inference);
        expert.start();
        try {
            assertThatThrownBy(() -> expert.evaluate(evaluation(), "private submitted text"))
                    .hasMessageContaining("Inference failed (IllegalArgumentException)")
                    .hasMessageNotContaining("private submitted text").hasNoCause();
        } finally {
            expert.stop();
        }
    }

    @Test
    void timeoutCancelsWorkAndRetainsResourcesUntilWorkerExits() throws Exception {
        var inference = mock(WolfDefenderInference.class);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean closed = new AtomicBoolean();
        AtomicBoolean cancelled = new AtomicBoolean();
        when(inference.evaluate(anyString(), anyInt(), any())).thenAnswer(invocation -> {
            entered.countDown();
            // Model a native kernel that does not return immediately on Java interruption.
            boolean done = false;
            while (!done) {
                try {
                    done = release.await(5, TimeUnit.SECONDS);
                    if (!done) {
                        throw new IllegalStateException("Test release timed out");
                    }
                } catch (InterruptedException ignored) {
                    cancelled.set(true);
                }
            }
            return 0.03;
        });
        doAnswer(invocation -> {
            closed.set(true);
            return null;
        }).when(inference).close();
        var expert = expert(inference);
        expert.setTimeoutMillis(100);
        expert.start();
        try {
            assertThatThrownBy(() -> expert.evaluate(evaluation(), "input")).isInstanceOf(TimeoutException.class);
            assertThat(entered.getCount()).isZero();
            await().atMost(5, TimeUnit.SECONDS).untilTrue(cancelled);
            assertThatThrownBy(() -> expert.evaluate(evaluation(), "second")).hasMessageContaining("busy");
            assertThatThrownBy(expert::stop).hasMessageContaining("Shutdown timed out");
            assertThatThrownBy(expert::start).hasMessageContaining("Previous worker is still stopping");
            assertThat(closed).isFalse();
        } finally {
            release.countDown();
        }
        await().atMost(5, TimeUnit.SECONDS).untilTrue(closed);
        try {
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThatCode(expert::start).doesNotThrowAnyException());
            assertThat(expert.isStarted()).isTrue();
        } finally {
            expert.stop();
        }
    }

    @Test
    void concurrentCallsAreBoundedAndInterruptionCancelsTheActiveCall() throws Exception {
        var inference = mock(WolfDefenderInference.class);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        when(inference.evaluate(anyString(), anyInt(), any())).thenAnswer(invocation -> {
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Test release timed out");
                }
                return 0.99;
            } catch (InterruptedException e) {
                interrupted.set(true);
                throw e;
            }
        });
        var expert = expert(inference);
        var caller = Executors.newSingleThreadExecutor();
        expert.start();
        try {
            var result = caller.submit(() -> expert.evaluate(evaluation(), "first"));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> expert.evaluate(evaluation(), "second")).hasMessageContaining("busy");
            result.cancel(true);
            await().atMost(5, TimeUnit.SECONDS).untilTrue(interrupted);
        } finally {
            release.countDown();
            caller.shutdownNow();
            expert.stop();
        }
        verify(inference).close();
    }

    @Test
    void preInterruptedCallerDoesNotRunInference() throws Exception {
        var inference = mock(WolfDefenderInference.class);
        var expert = expert(inference);
        expert.start();
        try {
            Thread.currentThread().interrupt();
            assertThatThrownBy(() -> expert.evaluate(evaluation(), "text")).isInstanceOf(InterruptedException.class);
            assertThat(Thread.interrupted()).isTrue();
        } finally {
            Thread.interrupted();
            expert.stop();
        }
        verify(inference, never()).evaluate(anyString(), anyInt(), any());
    }
}
