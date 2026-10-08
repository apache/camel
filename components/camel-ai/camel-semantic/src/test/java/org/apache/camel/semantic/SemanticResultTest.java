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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticResultTest {
    @Test
    void classificationLabelsAreImmutableAndConfidenceIsNotInvented() {
        Set<String> labels = new HashSet<>(Set.of("privacy"));
        SemanticResult result = new SemanticResult(labels, null, null, null, null);
        labels.clear();
        assertThat(result.getValue()).isEqualTo(Set.of("privacy"));
        assertThatThrownBy(() -> ((Set<?>) result.getValue()).clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(result.getProbability()).isNull();
        assertThat(result.getProbabilities()).isEmpty();
        assertThat(result.getConfidence()).isNull();
    }

    @Test
    void nullClassificationLabelHasAnActionableError() {
        Set<String> labels = new HashSet<>();
        labels.add(null);
        assertThatThrownBy(() -> new SemanticResult(labels, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("labels must not be null");
    }

    @Test
    void malformedProbabilityAndConfidenceAreErrors() {
        for (double invalid : new double[] { -0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY }) {
            assertThatThrownBy(() -> new SemanticResult(true, invalid, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new SemanticResult(true, null, null, invalid, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new SemanticResult(Set.of(), null, Map.of("privacy", invalid), null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
