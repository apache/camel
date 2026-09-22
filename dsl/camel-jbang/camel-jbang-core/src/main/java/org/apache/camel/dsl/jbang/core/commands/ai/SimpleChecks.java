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

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.LanguageValidationResult;
import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.apache.camel.tooling.model.EipModel;
import org.apache.camel.util.StringHelper;

import static org.apache.camel.dsl.jbang.core.commands.ai.YamlLines.countLeadingSpaces;
import static org.apache.camel.dsl.jbang.core.commands.ai.YamlLines.extractEipFromLine;
import static org.apache.camel.dsl.jbang.core.commands.ai.YamlLines.extractYamlValue;
import static org.apache.camel.dsl.jbang.core.commands.ai.YamlLines.findParentEip;
import static org.apache.camel.dsl.jbang.core.commands.ai.YamlLines.findParentEipLine;
import static org.apache.camel.dsl.jbang.core.commands.ai.YamlLines.findSiblingValue;

/**
 * The Simple language check of {@link SourceValidator}: every simple: value and log message parsed by the catalog, as a
 * predicate where the EIP takes one, with the aggregate-aware hints.
 */
final class SimpleChecks {

    private SimpleChecks() {
    }

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
            int textLine = i;
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
                        textLine = j;
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

            // a block scalar (simple: | ...) holds its text on the following lines (CAMEL-24883)
            if (YamlLines.isBlockIndicator(simpleText)) {
                simpleText = YamlLines.blockScalar(lines, textLine, simpleText);
                lineNum = textLine + 1;
            }
            if (simpleText == null || simpleText.isEmpty()) {
                continue;
            }
            // Skip what the catalog cannot validate because a placeholder is unresolved
            if (hasPlaceholderAsLogicalOperand(simpleText)) {
                continue;
            }

            // Determine predicate vs expression context
            boolean predicate = !isLogMessage && isPredicate(catalog, lines, i, lineIndent);

            boolean syntaxError = false;
            try {
                LanguageValidationResult result = predicate
                        ? catalog.validateLanguagePredicate(null, "simple", simpleText)
                        : catalog.validateLanguageExpression(null, "simple", simpleText);
                if (!result.isSuccess()) {
                    String error = result.getShortError() != null ? result.getShortError() : result.getError();
                    if (error != null && !isMissingDependency(error)) {
                        syntaxError = true;
                        errors.add("Line " + lineNum + ": Simple syntax error: " + error
                                   + aggregatedSizeHint(error, lines, i, lineIndent));
                    }
                }
            } catch (Exception e) {
                // best effort
            }

            // the parser cannot report this one: the expression is valid, it just does not mean what it says.
            // Only a reported syntax error suppresses it, to keep one message per expression; a catalog that could
            // not be asked at all does not, because this check does not depend on it.
            if (!syntaxError && !predicate && !isLogMessage) {
                String ternary = topLevelTernary(simpleText);
                if (ternary != null) {
                    errors.add("Line " + lineNum + ": " + ternary);
                }
            }
        }
        return errors;
    }

    /**
     * The top level of a Simple expression is a template, so a <tt>?</tt> and a <tt>:</tt> outside <tt>${...}</tt> are
     * literal text and not a ternary operator: the expression parses, and the text is used as the value with the
     * operator never evaluated (CAMEL-24826). The ternary operator is evaluated inside a single <tt>${...}</tt>, and at
     * the top level of a predicate, where there is no literal text to be ambiguous with.
     * <p/>
     * Reported only when the expression already uses a function, so prose such as <tt>Is it ok ? yes : no</tt> is left
     * alone, and not for a log message, where a <tt>?</tt> followed by a <tt>:</tt> is most often a sentence. Making
     * the parser itself report this is not an option: it regressed literal text twice already (CAMEL-22904,
     * CAMEL-23035).
     *
     * @param  text the simple expression
     * @return      the message to report, or <tt>null</tt> when the expression has no top-level ternary
     */
    static String topLevelTernary(String text) {
        if (text == null || !text.contains("${")) {
            return null;
        }
        int question = indexOfTopLevel(text, " ? ", 0);
        if (question < 0 || indexOfTopLevel(text, " : ", question + 3) < 0) {
            return null;
        }
        return "Simple has no top-level ternary: the ? at index " + (question + 1)
               + " and its : are outside ${...}, so they are literal text and the operator is never evaluated."
               + " Write the whole ternary inside one function, as in ${body.size() == 0 ? ${null} : ${body[0]}}";
    }

    /** The index of the token at the top level of the text: outside every ${...} and outside every quoted literal. */
    private static int indexOfTopLevel(String text, String token, int from) {
        char quote = 0;
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quote != 0) {
                if (ch == quote) {
                    quote = 0;
                }
            } else if (ch == '\'' || ch == '"') {
                quote = ch;
            } else if (ch == '$' && i < text.length() - 1 && text.charAt(i + 1) == '{') {
                depth++;
                i++;
            } else if (ch == '}' && depth > 0) {
                depth--;
            } else if (depth == 0 && i >= from && text.startsWith(token, i)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * A function served by a language or a component that is not on the classpath of the check (${jsonpath(...)},
     * ${a2a:text}): the route works when the dependency is there, which the check cannot know, so it is not reported.
     */
    static boolean isMissingDependency(String error) {
        return error.startsWith("No language could be found for:")
                || error.startsWith("No " + SimpleLanguageFunctionFactory.FACTORY + "/")
                        && error.contains("service could be found in the classpath");
    }

    /**
     * Whether the EIP the line is nested in evaluates the simple text as a predicate: the expression the model marks
     * with @AsPredicate, which the catalog carries as asPredicate on the option. The parent line is either the EIP
     * itself (filter, when, validate: its expression option) or an expression option of the EIP above it (the handled
     * of onException, the completionPredicate of aggregate). loop is the one EIP a flag on the option cannot describe:
     * its expression is a predicate only when doWhile is true.
     */
    static boolean isPredicate(CamelCatalog catalog, String[] lines, int lineIdx, int lineIndent) {
        int parentIdx = findParentEipLine(lines, lineIdx, lineIndent);
        if (parentIdx < 0) {
            return false;
        }
        String parent = StringHelper.dashToCamelCase(extractEipFromLine(lines[parentIdx].trim()));
        if (parent == null) {
            return false;
        }
        if ("loop".equals(parent)) {
            return "true".equals(findSiblingValue(lines, lineIdx, lineIndent, "doWhile"));
        }
        EipModel eip = catalog.eipModel(parent);
        if (eip != null) {
            return isPredicateOption(eip, "expression");
        }
        // not an EIP but an expression option of the EIP above it
        int ownerIdx = findParentEipLine(lines, parentIdx, countLeadingSpaces(lines[parentIdx]));
        if (ownerIdx < 0) {
            return false;
        }
        String ownerName = StringHelper.dashToCamelCase(extractEipFromLine(lines[ownerIdx].trim()));
        EipModel owner = ownerName != null ? catalog.eipModel(ownerName) : null;
        return owner != null && isPredicateOption(owner, parent);
    }

    private static boolean isPredicateOption(EipModel eip, String option) {
        return eip.getOptions().stream()
                .anyMatch(o -> option.equals(o.getName()) && "expression".equals(o.getKind()) && o.isAsPredicate());
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
