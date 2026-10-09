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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticResultViewTest {
    @Test
    void resultIsReadableAndSmallProbabilitiesRemainNonzero() throws Exception {
        JsonObject response = (JsonObject) Jsoner.deserialize("""
                {"status":"success","value":"billing","probability":0.000000007874509,
                 "confidence":0.4914,"elapsedMillis":12,"probabilities":{}}
                """);
        JsonObject operation = (JsonObject) Jsoner.deserialize("""
                {"resultType":"choice","contract":{"resultMeaning":"The selected department"}}
                """);
        String text = SemanticResultView.lines(response, operation, List.of()).stream()
                .flatMap(line -> line.spans().stream()).map(span -> span.content()).collect(Collectors.joining("\n"));
        assertThat(text).contains("billing", "The selected department", "7.875E-9", "0.4914")
                .doesNotContain("\"billing\"", "elapsedMillis", "Probabilities");
    }

    @Test
    void orderedScoreResultsShowTheEffectiveRangeAndLevelDescriptions() throws Exception {
        JsonObject operation = (JsonObject) Jsoner.deserialize("""
                {"resultType":"score","contract":{"scoreLevelsParameter":"rubric","minimum":0,"maximum":9}}
                """);
        JsonObject parameters = (JsonObject) Jsoner.deserialize("""
                {"rubric":["Routine","Needs attention soon","Critical outage"]}
                """);
        List<String> levels = SemanticDetails.scoreLevels(operation, parameters);
        JsonObject response = (JsonObject) Jsoner.deserialize("""
                {"status":"success","value":1.644,"elapsedMillis":12,
                 "probabilities":{"2":0.644,"1":0.356,"unexpected":0}}
                """);
        String text = text(response, operation, levels);
        assertThat(text).contains("1.644", "Effective range", "0 … 2 · 3 levels",
                "Between [1] Needs attention soon and [2] Critical outage", "[2] Critical outage", "unexpected");
        response.put("value", 2);
        assertThat(text(response, operation, levels)).contains("[2] Critical outage").doesNotContain("Between");
        response.put("value", 0);
        assertThat(text(response, operation, List.of("Only level"))).contains("0 … 0 · 1 level", "[0] Only level");
        response.put("value", 8);
        assertThat(text(response, operation, levels)).doesNotContain("Between");
        operation.getJsonObject("contract").remove("scoreLevelsParameter");
        assertThat(SemanticDetails.scoreLevels(operation, parameters)).isEmpty();
        assertThat(text(response, operation, List.of())).doesNotContain("Effective range", "Critical outage");
    }

    @Test
    void incompleteOrUnrelatedParametersDoNotInventAScoreScale() throws Exception {
        JsonObject operation = (JsonObject) Jsoner.deserialize("""
                {"resultType":"score","contract":{"scoreLevelsParameter":"rubric"}}
                """);
        for (String json : List.of("{}", "{\"criteria\":[\"Low\",\"High\"]}",
                "{\"rubric\":[]}", "{\"rubric\":[1,2]}", "{\"rubric\":[\"Low\",\" \"]}")) {
            assertThat(SemanticDetails.scoreLevels(operation, Jsoner.deserialize(json))).isEmpty();
        }
        operation.put("resultType", "choice");
        assertThat(SemanticDetails.scoreLevels(operation, Jsoner.deserialize("{\"rubric\":[\"Low\",\"High\"]}")))
                .isEmpty();
    }

    private static String text(JsonObject response, JsonObject operation, List<String> levels) {
        return SemanticResultView.lines(response, operation, levels).stream()
                .flatMap(line -> line.spans().stream()).map(span -> span.content()).collect(Collectors.joining("\n"));
    }

    @Test
    void failuresDoNotExposeJavaClassPrefixes() {
        assertThat(SemanticResultView
                .error("org.apache.camel.RuntimeCamelException: com.example.ProviderException: Connection refused"))
                .isEqualTo("Connection refused");
    }
}
