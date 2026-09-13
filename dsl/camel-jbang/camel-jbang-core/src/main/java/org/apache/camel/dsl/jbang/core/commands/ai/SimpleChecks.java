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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.LanguageValidationResult;

import static org.apache.camel.dsl.jbang.core.commands.ai.YamlLines.countLeadingSpaces;
import static org.apache.camel.dsl.jbang.core.commands.ai.YamlLines.extractEipFromLine;
import static org.apache.camel.dsl.jbang.core.commands.ai.YamlLines.extractYamlValue;
import static org.apache.camel.dsl.jbang.core.commands.ai.YamlLines.findParentEip;

/**
 * The Simple language check of {@link SourceValidator}: every simple: value and log message parsed by the catalog, as a
 * predicate where the EIP takes one, with the aggregate-aware hints.
 */
final class SimpleChecks {

    private SimpleChecks() {
    }

    static final Set<String> PREDICATE_EIPS = Set.of(
            "filter", "when", "validate", "onWhen", "on-when",
            "handled", "continued", "retryWhile", "retry-while",
            "completionPredicate", "completion-predicate",
            "completion", "loopDoWhile", "loop-do-while");

    /** The simple expressions of a YAML route checked against the catalog, as predicate where the EIP expects one. */
    public static List<String> validateYamlSimple(String content, CamelCatalog catalog) {
        List<String> errors = new ArrayList<>();
        String[] lines = content.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                continue;
            }

            String simpleText = null;
            int lineNum = i + 1;
            int lineIndent = countLeadingSpaces(line);
            boolean isLogMessage = false;

            // Strip YAML list prefix for matching
            String key = trimmed.startsWith("- ") ? trimmed.substring(2) : trimmed;

            // Match "simple: <value>" (inline shorthand)
            if (key.startsWith("simple:") && !key.equals("simple:")) {
                simpleText = extractYamlValue(key, "simple");
            }
            // Match "simple:" followed by "expression: <value>" on next line
            else if (key.equals("simple:")) {
                for (int j = i + 1; j < lines.length; j++) {
                    String next = lines[j].trim();
                    if (next.isBlank()) {
                        continue;
                    }
                    if (next.startsWith("expression:")) {
                        simpleText = extractYamlValue(next, "expression");
                        lineNum = j + 1;
                    }
                    break;
                }
            }
            // Match "message: <value>" under log: EIP
            else if (key.startsWith("message:") && !key.equals("message:")) {
                String parentEip = findParentEip(lines, i, lineIndent);
                if ("log".equals(parentEip)) {
                    simpleText = extractYamlValue(key, "message");
                    isLogMessage = true;
                }
            }

            if (simpleText == null || simpleText.isEmpty()) {
                continue;
            }
            // Skip what the catalog cannot validate because a placeholder is unresolved
            if (hasPlaceholderAsLogicalOperand(simpleText)) {
                continue;
            }

            // Determine predicate vs expression context
            boolean predicate = false;
            if (!isLogMessage) {
                String parentEip = findParentEip(lines, i, lineIndent);
                predicate = parentEip != null && PREDICATE_EIPS.contains(parentEip);
            }

            try {
                LanguageValidationResult result = predicate
                        ? catalog.validateLanguagePredicate(null, "simple", simpleText)
                        : catalog.validateLanguageExpression(null, "simple", simpleText);
                if (!result.isSuccess()) {
                    String error = result.getShortError() != null ? result.getShortError() : result.getError();
                    if (error != null) {
                        errors.add("Line " + lineNum + ": Simple syntax error: " + error
                                   + aggregatedSizeHint(error, lines, i, lineIndent));
                    }
                }
            } catch (Exception e) {
                // best effort
            }
        }
        return errors;
    }

    /**
     * ${size} or ${count} written inside an aggregate: the parser's did-you-mean (${length}) is about the function,
     * what the author wants is the number of aggregated messages, an exchange property.
     */
    static String aggregatedSizeHint(String error, String[] lines, int lineIdx, int lineIndent) {
        if (!error.contains("Unknown function: size") && !error.contains("Unknown function: count")) {
            return "";
        }
        if (!hasAncestorEip(lines, lineIdx, lineIndent, "aggregate")) {
            return "";
        }
        return " (inside an aggregate the number of aggregated messages is"
               + " ${exchangeProperty.CamelAggregatedSize}, and ${exchangeProperty.CamelAggregatedCompletedBy}"
               + " says what completed the group)";
    }

    /** Whether one of the EIPs the line is nested in (any depth) is the given one. */
    static boolean hasAncestorEip(String[] lines, int lineIdx, int lineIndent, String eip) {
        int indent = lineIndent;
        for (int j = lineIdx - 1; j >= 0 && indent > 0; j--) {
            String prev = lines[j];
            if (prev.isBlank() || prev.trim().startsWith("#")) {
                continue;
            }
            int prevIndent = countLeadingSpaces(prev);
            if (prevIndent < indent) {
                if (eip.equals(extractEipFromLine(prev.trim()))) {
                    return true;
                }
                indent = prevIndent;
            }
        }
        return false;
    }

    /**
     * Whether the text uses a property placeholder as an operand of a logical operator, such as
     * <tt>{{enabled}} && ${body} > 10</tt>.
     *
     * A placeholder can expand to an entire predicate, which the catalog cannot know as it validates without a running
     * Camel application. The catalog substitutes a placeholder with a dummy value, which is what an operand of a binary
     * operator needs, but a logical operator needs a predicate on either side. Validating those would report an error
     * for a route that is perfectly valid at runtime, so they are skipped.
     */
    public static boolean hasPlaceholderAsLogicalOperand(String text) {
        if (text == null || !text.contains("{{")) {
            return false;
        }

        // split into the operands of the logical operators, ignoring any quoted literal
        List<String> operands = new ArrayList<>();
        char quote = 0;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quote == 0 && (ch == '\'' || ch == '"')) {
                quote = ch;
            } else if (quote == ch) {
                quote = 0;
            } else if (quote == 0 && i < text.length() - 1) {
                char next = text.charAt(i + 1);
                if (ch == '&' && next == '&' || ch == '|' && next == '|') {
                    operands.add(text.substring(start, i));
                    i++;
                    start = i + 1;
                }
            }
        }
        if (operands.isEmpty()) {
            // no logical operator so the placeholders are all used as a value which the catalog can validate
            return false;
        }
        operands.add(text.substring(start));

        for (String operand : operands) {
            String s = operand.trim();
            // is the operand nothing but a single placeholder
            if (s.startsWith("{{") && s.endsWith("}}") && s.indexOf("}}") == s.length() - 2) {
                return true;
            }
        }
        return false;
    }

}
