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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

import static org.apache.camel.dsl.jbang.core.commands.tui.SemanticDetails.*;

/** The same result language for named samples and direct expert calls. */
final class SemanticResultView {
    private SemanticResultView() {
    }

    static String number(Number value) {
        double number = value.doubleValue();
        return Double.isFinite(number)
                ? new BigDecimal(String.format(Locale.ROOT, "%.4g", number)).stripTrailingZeros().toString()
                : value.toString();
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
        Object value = response.get("value");
        String answer = value instanceof String string ? string : value instanceof Number number ? number(number)
                : Jsoner.serialize(value);
        lines.add(Line.from(Span.styled("Result  ", Theme.label()), Span.styled(answer, Theme.info().bold()),
                Span.styled("   " + text(operation, "resultType") + " · success · " + text(response, "elapsedMillis") + " ms",
                        Theme.muted())));
        if (contract(operation).get("resultMeaning") != null) {
            lines.add(Line.from(text(contract(operation), "resultMeaning")));
        }
        scoreRange(lines, levels);
        if (value instanceof Number number && !levels.isEmpty()) {
            double score = number.doubleValue();
            if (Double.isFinite(score) && score >= 0 && score <= levels.size() - 1) {
                int lower = (int) Math.floor(score);
                int upper = (int) Math.ceil(score);
                lines.add(Line.from(lower == upper
                        ? levelLabel(levels, lower)
                        : "Between " + levelLabel(levels, lower) + " and " + levelLabel(levels, upper)));
            }
        }
        for (String key : List.of("probability", "confidence")) {
            if (response.get(key) instanceof Number number) {
                add(lines, Character.toUpperCase(key.charAt(0)) + key.substring(1), number(number));
            }
        }
        if (response.get("probabilities") instanceof JsonObject probabilities && !probabilities.isEmpty()) {
            lines.add(Line.empty());
            lines.add(Line.from(Span.styled("Probabilities", Theme.label())));
            if (contract(operation).get("probabilityMeaning") != null) {
                lines.add(Line.from(Span.styled(text(contract(operation), "probabilityMeaning"), Theme.muted())));
            }
            probabilities.entrySet().stream().filter(entry -> entry.getValue() instanceof Number)
                    .sorted((a, b) -> Double.compare(((Number) b.getValue()).doubleValue(),
                            ((Number) a.getValue()).doubleValue()))
                    .forEach(entry -> {
                        double probability = ((Number) entry.getValue()).doubleValue();
                        int filled = Math.max(0, Math.min(16, (int) Math.round(probability * 16)));
                        String label = probabilityLabel(entry.getKey(), levels);
                        if (!label.equals(entry.getKey())) {
                            lines.add(Line.from(Span.styled(label, Theme.info())));
                        }
                        lines.add(Line.from(Span.styled(label.equals(entry.getKey()) ? label + "  " : "  ", Theme.info()),
                                Span.styled("━".repeat(filled) + "·".repeat(16 - filled), Theme.info()),
                                Span.raw("  " + number((Number) entry.getValue()))));
                    });
        }
        if (response.get("metadata") instanceof JsonObject metadata && !metadata.isEmpty()) {
            lines.add(Line.empty());
            lines.add(Line.from(Span.styled("Metadata", Theme.label())));
            Jsoner.prettyPrint(metadata.toJson()).lines().forEach(line -> lines.add(Line.from(line)));
        }
        return lines;
    }

    private static String levelLabel(List<String> levels, int index) {
        return "[" + index + "] " + levels.get(index);
    }

    private static String probabilityLabel(String key, List<String> levels) {
        for (int i = 0; i < levels.size(); i++) {
            if (Integer.toString(i).equals(key)) {
                return levelLabel(levels, i);
            }
        }
        return key;
    }

}
