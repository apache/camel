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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.semantic.SemanticExpert.InputType;
import org.apache.camel.semantic.SemanticExpert.ResultType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticCapabilitiesTest {
    @Test
    void staticContractDoesNotConstructExpertAndIsImmutable() {
        var capabilities = SemanticCapabilities.from(StaticExpert.class);
        var operation = capabilities.operation("classify");
        assertThat(capabilities.getName()).isEqualTo("static");
        assertThat(SemanticCapabilities.from(StaticExpert.class)).isSameAs(capabilities);
        assertThat(operation.getResultType()).isEqualTo(ResultType.CLASSIFICATION);
        assertThat(operation.getScoreLevelsParameter()).isEmpty();
        assertThat(operation.getInputTypes()).containsExactly(InputType.STRUCTURED);
        assertThat(operation.getParameters().get("policy").getOmission()).isEqualTo("Service defaults");
        assertThatThrownBy(() -> capabilities.getOperations().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> operation.getParameters().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> operation.getLabels().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void operationAndParameterValidationDoNotExposeValues() {
        var capabilities = SemanticCapabilities.from(StaticExpert.class);
        assertThatThrownBy(() -> capabilities.operation("missing")).hasMessageContaining("Unknown operation");
        var operation = capabilities.operation("classify");
        assertThatThrownBy(() -> operation.validate(Map.of())).hasMessageContaining("criterion")
                .hasMessageContaining("required");
        assertThatThrownBy(() -> operation.validate(Map.of("criterion", "SECRET")))
                .hasMessageContaining("criterion").hasMessageNotContaining("SECRET");
        assertThatThrownBy(() -> operation.validate(Map.of("criterion", "safety", "unknown", "SECRET")))
                .hasMessageContaining("unknown").hasMessageNotContaining("SECRET");
        assertThatThrownBy(() -> operation.validate(Map.of("criterion", "safety", "threshold", "0.5")))
                .hasMessageContaining("threshold").hasMessageContaining("Number");
        assertThatThrownBy(() -> operation.validate(Map.of("criterion", "safety", "threshold", Double.NaN)))
                .hasMessageContaining("threshold");
        assertThatCode(() -> operation.validate(Map.of("criterion", "safety", "threshold", 0.5)))
                .doesNotThrowAnyException();
    }

    @Test
    void nestedDeclarationParametersAreCopiedButOmittedValuesStayAbsent() {
        Map<String, Object> policy = new LinkedHashMap<>();
        List<String> labels = new ArrayList<>(List.of("privacy"));
        policy.put("labels", labels);
        SemanticEvaluation evaluation = new SemanticEvaluation(
                "classify", "expert", null,
                Map.of("criterion", "safety", "policy", policy));
        labels.add("later");
        policy.put("new", true);
        assertThat(evaluation.getParameters()).doesNotContainKey("threshold");
        assertThat(evaluation.getParameters().get("policy")).isEqualTo(Map.of("labels", List.of("privacy")));
        assertThatThrownBy(() -> evaluation.getParameters().clear()).isInstanceOf(UnsupportedOperationException.class);
        var frozenPolicy = (Map<?, ?>) evaluation.getParameters().get("policy");
        var frozenLabels = (List<?>) frozenPolicy.get("labels");
        assertThatThrownBy(frozenPolicy::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(frozenLabels::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nestedParameterMapsRejectInvalidKeys() {
        for (Object key : Arrays.asList("", " \t", null, 1)) {
            Map<Object, Object> invalid = new LinkedHashMap<>();
            invalid.put(key, true);
            for (Object value : List.of(invalid, List.of(invalid))) {
                assertThatThrownBy(() -> new SemanticEvaluation("custom", null, null, Map.of("policy", value)))
                        .isExactlyInstanceOf(IllegalArgumentException.class)
                        .hasMessage(key instanceof String
                                ? "Parameter names must not be blank"
                                : "Parameter maps require string keys");
            }
        }
    }

    @Test
    void resultTypesRangesAndOptionalInformationAreValidated() {
        var operation = SemanticCapabilities.from(StaticExpert.class).operation("classify");
        assertThat(operation.validateResult(result(Set.of()))).isEqualTo(Set.of());
        assertThat(operation.validateResult(result(Set.of("privacy")))).isEqualTo(Set.of("privacy"));
        assertThatThrownBy(() -> operation.validateResult(result(List.of("privacy")))).hasMessageContaining("CLASSIFICATION");
        assertThatThrownBy(() -> operation.validateResult(result(Set.of("unknown")))).hasMessageContaining("CLASSIFICATION");
        assertThatThrownBy(() -> operation.validateResult(new SemanticResult(Set.of(), null, null, 0.9, null)))
                .hasMessageContaining("undeclared");
        assertThat(operation.validateResult(new SemanticResult(Set.of("privacy"), null, Map.of("privacy", 0.8), null, null)))
                .isEqualTo(Set.of("privacy"));
        assertThatThrownBy(
                () -> operation.validateResult(new SemanticResult(Set.of(), null, Map.of("unknown", 0.8), null, null)))
                .hasMessageContaining("unknown labels");
        var score = SemanticCapabilities.from(StaticExpert.class).operation("score");
        assertThat(score.validateResult(result(42))).isEqualTo(42);
        for (double value : new double[] { -1, 101, Double.NaN, Double.POSITIVE_INFINITY }) {
            assertThatThrownBy(() -> score.validateResult(result(value))).hasMessageContaining("SCORE");
        }
    }

    @Test
    void explicitPolicyValuesArePreservedAndMutableNumbersAreRejected() {
        var evaluation = new SemanticEvaluation(
                "boolean", null, null, Map.of("threshold", 0.5, "uncertainty", 0.0, "uncertaintyPolicy", "fail"));
        assertThat(evaluation.getParameters()).containsEntry("threshold", 0.5).containsEntry("uncertainty", 0.0)
                .containsEntry("uncertaintyPolicy", "fail");
        assertThatThrownBy(() -> new SemanticEvaluation("classify", "expert", null, Map.of("threshold", new AtomicInteger(1))))
                .hasMessageContaining("immutable scalar");
        var operation = SemanticCapabilities.from(StaticExpert.class).operation("classify");
        assertThatCode(() -> operation.validate(Map.of("criterion", "safety", "limit", 2.0))).doesNotThrowAnyException();
        assertThatCode(() -> operation.validate(Map.of("criterion", "safety", "limit", 2))).doesNotThrowAnyException();
        assertThatThrownBy(() -> operation.validate(Map.of("criterion", "safety", "limit", 2.1)))
                .hasMessageContaining("integer");
    }

    @Test
    void numericBoundsDoNotRoundInvalidValuesOntoBoundaries() {
        var contract = SemanticCapabilities.from(StaticExpert.class);
        for (String value : List.of("1.0000000000000000001", "-1e-400")) {
            assertThatThrownBy(() -> contract.operation("classify").validate(
                    Map.of("criterion", "safety", "threshold", new BigDecimal(value))))
                    .hasMessageContaining("threshold").hasMessageContaining("numeric constraints");
        }
        for (String value : List.of("100.0000000000000000001", "-1e-400")) {
            assertThatThrownBy(() -> contract.operation("score").validateResult(result(new BigDecimal(value))))
                    .hasMessageContaining("SCORE");
        }
    }

    private static SemanticResult result(Object value) {
        return new SemanticResult(value, null, null, null, null);
    }

    @SemanticExpert(name = "static", description = "Inspectable without construction", provider = "test", artifactId = "test",
                    operations = {
                            @SemanticOperation(name = "classify", description = "Classify content",
                                               inputTypes = InputType.STRUCTURED,
                                               inputRequirements = "Conversation map", resultType = ResultType.CLASSIFICATION,
                                               resultMeaning = "Detected categories", labels = { "privacy", "unsafe" },
                                               probabilities = true,
                                               probabilityMeaning = "Independent probability for each category", parameters = {
                                                       @SemanticParameter(name = "limit", description = "Maximum labels",
                                                                          type = Number.class, integer = true, minimum = 1,
                                                                          omission = "All labels"),
                                                       @SemanticParameter(name = "criterion", description = "Criterion",
                                                                          required = true, values = "safety"),
                                                       @SemanticParameter(name = "policy",
                                                                          description = "Nested provider policy",
                                                                          type = Map.class, omission = "Service defaults"),
                                                       @SemanticParameter(name = "threshold",
                                                                          description = "Requested filtering threshold",
                                                                          type = Number.class,
                                                                          minimum = 0, maximum = 1,
                                                                          omission = "Service defaults") }),
                            @SemanticOperation(name = "score", description = "Quality", inputTypes = InputType.TEXT,
                                               inputRequirements = "Text", resultType = ResultType.SCORE,
                                               resultMeaning = "Quality from 0 to 100",
                                               minimum = 0, maximum = 100)
                    })
    private static class StaticExpert {
        StaticExpert() {
            throw new AssertionError("Contract inspection must not construct the expert");
        }
    }
}
