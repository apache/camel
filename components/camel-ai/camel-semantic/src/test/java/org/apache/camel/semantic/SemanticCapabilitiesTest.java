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

import java.util.List;
import java.util.Map;

import org.apache.camel.semantic.SemanticExpert.InputType;
import org.apache.camel.semantic.SemanticExpert.Instructions;
import org.apache.camel.semantic.SemanticExpert.ResultType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticCapabilitiesTest {
    @Test
    void configuredCapabilitiesAreIndependentImmutableAndOrdered() {
        ResultType[] types = { ResultType.SCORE, ResultType.BOOLEAN, ResultType.CHOICE };
        var builder = SemanticCapabilities.builder().name("general").inputTypes(InputType.STRUCTURED, InputType.TEXT)
                .resultTypes(types).confidenceTypes(types).callerDefinedCriteria(true).maxChoices(10);
        var capabilities = builder.build();
        types[0] = ResultType.BOOLEAN;
        builder.resultTypes(ResultType.BOOLEAN).maxChoices(1);
        var narrowed = capabilities.toBuilder().resultTypes(ResultType.BOOLEAN).maxChoices(2).build();
        assertThat(capabilities.isKnown()).isTrue();
        assertThat(capabilities.getResultTypes()).containsExactly(ResultType.BOOLEAN, ResultType.CHOICE, ResultType.SCORE);
        assertThat(capabilities.getInputTypes()).containsExactly(InputType.TEXT, InputType.STRUCTURED);
        assertThat(capabilities.getConfidenceTypes()).containsExactly(ResultType.BOOLEAN, ResultType.CHOICE, ResultType.SCORE);
        assertThatThrownBy(() -> capabilities.getResultTypes().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(capabilities.getMaxChoices()).isEqualTo(10);
        assertThat(narrowed.getMaxChoices()).isEqualTo(2);
        assertThat(narrowed.getName()).isEqualTo("general");
        assertThat(narrowed.getResultTypes()).containsExactly(ResultType.BOOLEAN);
        assertThatThrownBy(() -> narrowed.validate(question(SemanticQuestion.Type.CHOICE, Map.of("a", "A"), List.of())))
                .hasMessageContaining("supports [BOOLEAN]");
    }

    @Test
    void duplicateAnnotationValuesAreDeduplicated() {
        var capabilities = SemanticCapabilities.from(DuplicateExpert.class.getAnnotation(SemanticExpert.class));
        assertThat(capabilities.getInputTypes()).containsExactly(InputType.TEXT, InputType.STRUCTURED);
        assertThat(capabilities.getResultTypes()).containsExactly(ResultType.BOOLEAN, ResultType.SCORE);
        assertThat(capabilities.getConfidenceTypes()).containsExactly(ResultType.BOOLEAN, ResultType.SCORE);
    }

    @Test
    void builderAcceptsEmptyEnumArrays() {
        var capabilities = SemanticCapabilities.builder().inputTypes().resultTypes().confidenceTypes().build();
        assertThat(capabilities.getInputTypes()).isEmpty();
        assertThat(capabilities.getResultTypes()).isEmpty();
        assertThat(capabilities.getConfidenceTypes()).isEmpty();
    }

    @SemanticExpert(name = "duplicates", description = "Duplicate capability values", provider = "test", artifactId = "test",
                    instructions = Instructions.OPTIONAL, callerDefinedCriteria = false,
                    inputTypes = { InputType.STRUCTURED, InputType.TEXT, InputType.TEXT },
                    resultTypes = { ResultType.SCORE, ResultType.BOOLEAN, ResultType.BOOLEAN },
                    confidenceTypes = { ResultType.SCORE, ResultType.BOOLEAN, ResultType.BOOLEAN })
    private static class DuplicateExpert {
    }

    @Test
    void choiceLimitsDoNotApplyToBooleanCriteriaOrUnlimitedScores() {
        var capabilities = SemanticCapabilities.builder().resultTypes(ResultType.values())
                .callerDefinedCriteria(true).maxChoices(1).build();
        Map<String, String> criteria = Map.of("true", "Yes", "false", "No");
        assertThatCode(() -> capabilities.validate(question(SemanticQuestion.Type.BOOLEAN, criteria, List.of())))
                .doesNotThrowAnyException();
        assertThatCode(() -> capabilities.validate(question(SemanticQuestion.Type.CHOICE, Map.of("a", "A"), List.of())))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> capabilities.validate(question(SemanticQuestion.Type.CHOICE, criteria, List.of())))
                .hasMessage("Supports at most 1 choice criteria");
        assertThatCode(() -> capabilities.validate(question(SemanticQuestion.Type.SCORE, Map.of(), List.of("low", "high"))))
                .doesNotThrowAnyException();
    }

    @Test
    void scoreLimitReportsOnlyTheLimitedDimension() {
        var capabilities = SemanticCapabilities.builder().resultTypes(ResultType.values())
                .callerDefinedCriteria(true).maxScoreLevels(1).build();
        assertThatCode(() -> capabilities.validate(question(SemanticQuestion.Type.SCORE, Map.of(), List.of("low"))))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> capabilities.validate(question(SemanticQuestion.Type.SCORE, Map.of(), List.of("low", "high"))))
                .hasMessage("Supports at most 1 score levels");
        assertThatCode(
                () -> capabilities.validate(question(SemanticQuestion.Type.CHOICE, Map.of("a", "A", "b", "B"), List.of())))
                .doesNotThrowAnyException();
    }

    @Test
    void explicitOptionalInstructionsDoNotClaimUnspecifiedResultSupport() {
        var capabilities = SemanticCapabilities.builder().instructions(Instructions.OPTIONAL).build();
        var question = new SemanticQuestion(
                SemanticQuestion.Type.BOOLEAN, null, null, null, null, 0.5, 0,
                SemanticQuestion.UncertaintyPolicy.FAIL);
        assertThatThrownBy(() -> capabilities.validate(question)).hasMessageContaining("supports []");
        assertThatCode(() -> capabilities.toBuilder().resultTypes(ResultType.BOOLEAN).build().validate(question))
                .doesNotThrowAnyException();
    }

    private SemanticQuestion question(SemanticQuestion.Type type, Map<String, String> criteria, List<String> levels) {
        return new SemanticQuestion(
                type, "Evaluate this", null, criteria, levels, 0.5, 0,
                SemanticQuestion.UncertaintyPolicy.FAIL);
    }
}
