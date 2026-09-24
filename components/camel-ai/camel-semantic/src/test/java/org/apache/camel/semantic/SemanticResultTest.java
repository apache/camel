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

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticResultTest {
    @Test
    void booleanOnlyProviderCannotSilentlyIgnoreProbabilityPolicy() {
        SemanticResult result = new SemanticResult(true, null, null, null, null);
        SemanticQuestion defaults = SemanticLanguageTest.question(SemanticQuestion.Type.BOOLEAN, null, 0.5, 0,
                SemanticQuestion.UncertaintyPolicy.FAIL);
        assertThat(result.decision(defaults)).isEqualTo(true);
        SemanticQuestion threshold = SemanticLanguageTest.question(SemanticQuestion.Type.BOOLEAN, null, 0.7, 0,
                SemanticQuestion.UncertaintyPolicy.FAIL);
        assertThatThrownBy(() -> result.decision(threshold)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decision policy");
        SemanticQuestion uncertain = SemanticLanguageTest.question(SemanticQuestion.Type.BOOLEAN, null, 0.5, 0.1,
                SemanticQuestion.UncertaintyPolicy.NON_MATCH);
        assertThatThrownBy(() -> result.decision(uncertain)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decision policy");
    }

    @Test
    void choiceProbabilitiesMustCoverExactlyTheDeclaredCriteria() {
        SemanticQuestion question = SemanticLanguageTest.question(SemanticQuestion.Type.CHOICE, null, 0.5, 0,
                SemanticQuestion.UncertaintyPolicy.FAIL);
        SemanticResult incomplete = new SemanticResult("billing", null, Map.of("billing", 0.8), null, null);
        assertThatThrownBy(() -> incomplete.decision(question)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cover every criterion");
        SemanticResult complete = new SemanticResult("billing", null, Map.of("billing", 0.8, "technical", 0.2), null, null);
        assertThat(complete.decision(question)).isEqualTo("billing");
    }
}
