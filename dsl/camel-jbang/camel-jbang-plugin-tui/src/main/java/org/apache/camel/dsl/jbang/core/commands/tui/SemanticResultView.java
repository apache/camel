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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import org.apache.camel.dsl.jbang.core.common.SemanticResultFormatter;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

import static org.apache.camel.dsl.jbang.core.commands.tui.SemanticDetails.*;

/** The same result language for named samples and direct expert calls. */
final class SemanticResultView {
    private SemanticResultView() {
    }

    static String number(Number value) {
        return SemanticResultFormatter.number(value);
    }

    static String error(String value) {
        if (value == null || value.isBlank()) {
            return "The expert could not complete the request.";
        }
        return value.replaceAll("(?:[a-zA-Z_$][\\w$]*\\.)+[A-Z][\\w$]*(?:Exception|Error):\\s*", "");
    }

    static JsonObject failure(Throwable failure) {
        Throwable cause = failure.getCause() == null ? failure : failure.getCause();
        JsonObject response = new JsonObject();
        response.put("status", "failed");
        response.put("error", cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage());
        return response;
    }

    static List<Line> lines(JsonObject response, JsonObject operation, List<String> levels) {
        List<Line> lines = new ArrayList<>();
        if (!"success".equals(response.get("status"))) {
            lines.add(Line.from(Span.styled("Expert call failed", Theme.error())));
            lines.add(Line.from(Span.styled(error(response.getString("error")), Theme.error())));
            lines.add(Line.from("Draft kept · correct the input or check the service, then Ctrl+r to retry."));
            return lines;
        }
        JsonObject semantics = new JsonObject(contract(operation));
        semantics.put("resultType", operation.get("resultType"));
        semantics.put("meaning", contract(operation).get("resultMeaning"));
        Map<?, ?> interpretation = response.get("interpretation") instanceof Map<?, ?> captured
                ? captured
                : interpretation(operation, Map.of(), levels);
        var formatted = SemanticResultFormatter.format(response, semantics, interpretation,
                response.getString("status"), response.get("resultOmitted"), response.get("interpretationOmitted"));
        lines.add(Line.from(Span.styled("Result  " + formatted.compact(), Theme.info().bold()),
                Span.styled(" · success · " + text(response, "elapsedMillis") + " ms", Theme.muted())));
        formatted.details().forEach(line -> lines.add(Line.from(line)));
        if (response.get("metadata") instanceof JsonObject metadata && !metadata.isEmpty()) {
            lines.add(Line.empty());
            lines.add(Line.from(Span.styled("Metadata", Theme.label())));
            Jsoner.prettyPrint(metadata.toJson()).lines().forEach(line -> lines.add(Line.from(line)));
        }
        return lines;
    }

    static JsonObject interpretation(JsonObject operation, Object parameters, List<String> levels) {
        JsonObject result = new JsonObject();
        JsonObject contract = contract(operation);
        result.put("source", "expert_contract");
        result.put("meaning", contract.get("resultMeaning"));
        for (String field : List.of("probabilityMeaning", "confidenceMeaning", "noMatchDeclared", "noMatchLabel",
                "probabilitiesComplete")) {
            if (contract.get(field) != null && !("noMatchLabel".equals(field) && "".equals(contract.get(field)))) {
                result.put(field, contract.get(field));
            }
        }
        if (!levels.isEmpty()) {
            result.put("scoreLevels", List.copyOf(levels));
            if (Boolean.TRUE.equals(contract.get("probabilities"))) {
                result.put("scoreProbabilityKeys", "zero_based_levels");
            }
        }
        if (parameters instanceof Map<?, ?> values) {
            Object descriptions = contract.get("scoreDescriptionsParameter");
            if (descriptions instanceof String parameter && values.get(parameter) instanceof Map<?, ?> labels) {
                result.put("scoreDescriptions", Map.copyOf(labels));
            }
            for (String kind : List.of("choice", "boolean")) {
                Object name = contract.get(kind + "LabelsParameter");
                if (name instanceof String parameter && values.get(parameter) instanceof Map<?, ?> labels) {
                    result.put("choice".equals(kind) ? "choices" : "booleanLabels", Map.copyOf(labels));
                }
            }
        }
        return result;
    }
}
