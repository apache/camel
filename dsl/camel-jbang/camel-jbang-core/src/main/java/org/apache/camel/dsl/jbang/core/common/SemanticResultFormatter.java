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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Neutral text for the same typed answer on CLI, playground, audit and MCP surfaces. */
public final class SemanticResultFormatter {
    private SemanticResultFormatter() {
    }

    public record View(String compact, List<String> details) {
        public View {
            details = List.copyOf(details);
        }
    }

    public static String number(Number value) {
        try {
            return new BigDecimal(value.toString()).stripTrailingZeros().toString();
        } catch (NumberFormatException nonFinite) {
            return value.toString();
        }
    }

    public static View audit(Map<String, ?> record) {
        return format(map(record.get("result")), map(record.get("semantics")), map(record.get("interpretation")),
                text(record.get("status")), record.get("resultOmitted"), record.get("interpretationOmitted"));
    }

    public static View format(
            Map<?, ?> result, Map<?, ?> semantics, Map<?, ?> interpretation,
            String execution, Object resultOmitted, Object interpretationOmitted) {
        List<String> lines = new ArrayList<>();
        String type = text(semantics.get("resultType")).toUpperCase(Locale.ROOT);
        String compact;
        Object value = result.get("value");
        if (resultOmitted != null) {
            compact = "Result omitted · " + text(resultOmitted);
        } else if (!result.containsKey("value")) {
            compact = "No result" + (execution.isEmpty() ? " recorded" : " · " + execution.replace('_', ' '));
        } else {
            compact = value(value) + " [" + (type.isEmpty() ? "type not recorded" : type) + "]";
            if ("CLASSIFICATION".equals(type) && value instanceof List<?> labels && labels.isEmpty()) {
                lines.add("No labels returned");
            }
        }
        add(lines, "Meaning", interpretation.containsKey("meaning") ? interpretation.get("meaning") : semantics.get("meaning"));
        if (interpretation.isEmpty()) {
            lines.add("Interpretation: "
                      + (interpretationOmitted == null ? "not recorded" : "omitted · " + text(interpretationOmitted)));
        } else {
            if ("BOOLEAN".equals(type) && value instanceof Boolean) {
                add(lines, "Returned answer", map(interpretation.get("booleanLabels")).get(value.toString()));
            } else if ("CHOICE".equals(type) && value instanceof String) {
                add(lines, "Selected meaning", map(interpretation.get("choices")).get(value));
                if (Boolean.TRUE.equals(interpretation.get("noMatchDeclared"))) {
                    lines.add(interpretation.get("noMatchLabel") == null
                            ? "No special no-match answer declared"
                            : "Declared no-match: " + text(interpretation.get("noMatchLabel"))
                              + (value.equals(interpretation.get("noMatchLabel")) ? " · selected" : ""));
                }
            }
            if ("SCORE".equals(type) && value instanceof Number score
                    && interpretation.get("scoreLevels") instanceof List<?> levels && !levels.isEmpty()) {
                double position = score.doubleValue();
                if (Double.isFinite(position) && position >= 0 && position <= levels.size() - 1) {
                    int lower = (int) Math.floor(position);
                    int upper = (int) Math.ceil(position);
                    lines.add(lower == upper
                            ? level(levels, lower, interpretation)
                            : "Between " + level(levels, lower, interpretation) + " and "
                              + level(levels, upper, interpretation));
                } else {
                    lines.add("Score outside the captured scale; numeric value preserved");
                }
                lines.add("Captured scale: 0.." + (levels.size() - 1));
            }
        }
        add(lines, "Probability", result.get("probability"));
        add(lines, "Probability meaning", interpretation.get("probabilityMeaning") != null
                ? interpretation.get("probabilityMeaning") : semantics.get("probabilityMeaning"));
        add(lines, "Confidence", result.get("confidence"));
        add(lines, "Confidence meaning", interpretation.get("confidenceMeaning") != null
                ? interpretation.get("confidenceMeaning") : semantics.get("confidenceMeaning"));
        Map<?, ?> probabilities = map(result.get("probabilities"));
        List<Map.Entry<String, Number>> ranked = new ArrayList<>();
        probabilities.forEach((key, probability) -> {
            if (key instanceof String label && probability instanceof Number number && Double.isFinite(number.doubleValue())) {
                ranked.add(Map.entry(label, number));
            }
        });
        ranked.sort(Comparator.<Map.Entry<String, Number>> comparingDouble(entry -> entry.getValue().doubleValue())
                .reversed().thenComparing(Map.Entry::getKey));
        ranked.forEach(entry -> lines.add("P(" + text(entry.getKey()) + ")=" + number(entry.getValue())));
        if (ranked.size() >= 2) {
            BigDecimal first = new BigDecimal(ranked.get(0).getValue().toString());
            BigDecimal second = new BigDecimal(ranked.get(1).getValue().toString());
            lines.add("Reported top-two gap: " + number(first.subtract(second)));
            if (first.compareTo(second) == 0) {
                lines.add("Highest reported probability is tied");
            }
            if ("CHOICE".equals(type) && value instanceof String selected) {
                ranked.stream().filter(entry -> !entry.getKey().equals(selected)).findFirst()
                        .ifPresent(entry -> lines.add("Highest reported alternative: " + text(entry.getKey())));
                if (probabilities.get(selected) instanceof Number selectedProbability
                        && first.compareTo(new BigDecimal(selectedProbability.toString())) > 0) {
                    lines.add("Selected answer preserved; a different key has the highest reported probability");
                }
            }
        }
        if (!ranked.isEmpty()) {
            Set<Object> domain = new LinkedHashSet<>(map(interpretation.get("choices")).keySet());
            if ("zero_based_levels".equals(interpretation.get("scoreProbabilityKeys"))
                    && interpretation.get("scoreLevels") instanceof List<?> levels) {
                for (int i = 0; i < levels.size(); i++) {
                    domain.add(Integer.toString(i));
                }
            }
            boolean partial = !domain.isEmpty() && !probabilities.keySet().containsAll(domain);
            if (partial) {
                lines.add("Distribution: incomplete for the captured domain; diagnostics use reported keys only");
            } else {
                lines.add(Boolean.TRUE.equals(interpretation.get("probabilitiesComplete"))
                        ? "Distribution: declared complete by the expert" : "Distribution completeness: not provided");
            }
            if (!domain.isEmpty() && !domain.containsAll(probabilities.keySet())) {
                lines.add("Reported distribution contains keys outside the captured domain");
            }
            if ("CHOICE".equals(type) && !probabilities.containsKey(value)) {
                lines.add("Selected answer has no reported probability; answer preserved");
            }
            if ("SCORE".equals(type) && "zero_based_levels".equals(interpretation.get("scoreProbabilityKeys"))
                    && interpretation.get("scoreLevels") instanceof List<?> levels) {
                double highest = ranked.get(0).getValue().doubleValue();
                List<String> modes = new ArrayList<>();
                for (var entry : ranked) {
                    if (Double.compare(entry.getValue().doubleValue(), highest) != 0) {
                        break;
                    }
                    for (int i = 0; i < levels.size(); i++) {
                        if (Integer.toString(i).equals(entry.getKey())) {
                            modes.add(level(levels, i, interpretation));
                        }
                    }
                }
                if (!modes.isEmpty()) {
                    lines.add("Reported score mode: " + String.join("; ", modes));
                }
            }
            lines.add("Reported probabilities describe this response; they are not calibrated certainty or model reasoning.");
        }
        return new View(compact, lines);
    }

    public static Map<?, ?> map(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private static String level(List<?> levels, int index, Map<?, ?> interpretation) {
        String label = text(levels.get(index));
        Object description = map(interpretation.get("scoreDescriptions")).get(levels.get(index));
        return "[" + index + "] " + label + (description == null ? "" : " — " + text(description));
    }

    private static String value(Object value) {
        if (value instanceof List<?> list) {
            return "[" + String.join(", ", list.stream().map(SemanticResultFormatter::text).toList()) + "]";
        }
        return text(value);
    }

    private static void add(List<String> lines, String label, Object value) {
        if (value != null && !text(value).isEmpty()) {
            lines.add(label + ": " + text(value));
        }
    }

    private static String text(Object value) {
        if (value == null) {
            return "";
        }
        String text = value instanceof Number number ? number(number) : value.toString();
        return text.replaceAll("[\\p{Cc}\\p{Cf}]", " ");
    }
}
