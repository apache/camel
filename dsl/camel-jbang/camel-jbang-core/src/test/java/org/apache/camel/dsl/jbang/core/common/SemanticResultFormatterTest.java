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

package org.apache.camel.dsl.jbang.core.common;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticResultFormatterTest {
    @Test
    void preservesTypedValuesAndIndependentExecutionStates() {
        assertEquals("false [BOOLEAN]", view("BOOLEAN", false).compact());
        assertEquals("[] [CLASSIFICATION]", view("CLASSIFICATION", List.of()).compact());
        assertEquals("1.6 [SCORE]", view("SCORE", 1.6).compact());
        assertEquals("1E-9", SemanticResultFormatter.number(0.000000001));
        assertEquals("Result omitted · snapshot_limit", SemanticResultFormatter.audit(Map.of("status", "success",
                "resultOmitted", "snapshot_limit")).compact());
        assertEquals("No result · failed", SemanticResultFormatter.audit(Map.of("status", "failed")).compact());
    }

    @Test
    void reportsExistingProbabilitiesWithoutReplacingTheSelectedAnswerOrInferringNoMatch() {
        var answer = SemanticResultFormatter.audit(Map.of("status", "success", "semantics", Map.of("resultType", "CHOICE"),
                "result",
                Map.of("value", "other", "probabilities", Map.of("billing", 0.68, "technical", 0.29, "other", 0.03))));
        assertEquals("other [CHOICE]", answer.compact());
        assertTrue(answer.details().contains("Reported top-two gap: 0.39"));
        assertTrue(answer.details().contains("Highest reported alternative: billing"));
        assertTrue(answer.details().contains("Distribution completeness: not provided"));
        assertFalse(String.join(" ", answer.details()).contains("no-match: other"));
    }

    @Test
    void explainsFractionalScoreFromCapturedLevelsOnly() {
        var answer = SemanticResultFormatter.audit(Map.of("status", "success", "semantics", Map.of("resultType", "SCORE"),
                "result", Map.of("value", 1.6), "interpretation", Map.of("scoreLevels", List.of("Routine", "Soon", "Urgent"))));
        assertEquals("1.6 [SCORE]", answer.compact());
        assertTrue(answer.details().contains("Between [1] Soon and [2] Urgent"));
        assertFalse(String.join(" ", answer.details()).contains("severity"));
    }

    @Test
    void includesCapturedRubricDescriptionsWithoutChangingTheScore() {
        var answer = SemanticResultFormatter.audit(Map.of("status", "success", "semantics", Map.of("resultType", "SCORE"),
                "result", Map.of("value", 0.96), "interpretation", Map.of(
                        "scoreLevels", List.of("routine", "urgent"),
                        "scoreDescriptions", Map.of("routine", "Within a week", "urgent", "Immediate action"))));
        assertEquals("0.96 [SCORE]", answer.compact());
        assertTrue(answer.details().contains("Between [0] routine — Within a week and [1] urgent — Immediate action"));
    }

    @Test
    void reportsIncompleteCapturedDomainWithoutInventingMissingProbabilities() {
        var view = SemanticResultFormatter.audit(Map.of("semantics", Map.of("resultType", "CHOICE"),
                "result", Map.of("value", "billing", "probabilities", Map.of("billing", 0.7)),
                "interpretation", Map.of("choices", Map.of("billing", "Billing", "technical", "Technical"),
                        "probabilitiesComplete", true)));
        assertEquals("billing [CHOICE]", view.compact());
        assertTrue(String.join(" ", view.details()).contains("incomplete for the captured domain"));
        assertFalse(String.join(" ", view.details()).contains("P(technical)"));
        assertFalse(String.join(" ", view.details()).contains("declared complete"));
    }

    @Test
    void filtersPreserveSpacesEqualsAndEscapedQuotes() {
        String route = "Route to \"human\" = fallback";
        var filters = SemanticAuditFilters.parse("routeId=" + SemanticAuditFilters.quote(route) + " expert='Business policy'",
                Set.of("routeId", "expert"));
        assertEquals(Map.of("routeId", route, "expert", "Business policy"), filters);
        assertThrows(IllegalArgumentException.class, () -> SemanticAuditFilters.parse("expert=\"open", Set.of("expert")));
        assertThrows(IllegalArgumentException.class, () -> SemanticAuditFilters.parse("expert=a expert=b", Set.of("expert")));
    }

    private static SemanticResultFormatter.View view(String type, Object value) {
        return SemanticResultFormatter.audit(Map.of("status", "success", "semantics", Map.of("resultType", type),
                "result", Map.of("value", value)));
    }
}
