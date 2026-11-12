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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.LanguageValidationResult;
import org.apache.camel.dsl.jbang.run.common.VersionHelper;
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
            // a filter of JsonPath or SpEL inside a function: said with the jsonpath step to write, in place of the
            // parser's error when it has one
            String filter = filterInFunction(simpleText);
            if (filter != null) {
                errors.add("Line " + lineNum + ": " + filter);
                continue;
            }
            // Skip what the catalog cannot validate because a placeholder is unresolved
            if (hasPlaceholderAsLogicalOperand(simpleText)) {
                continue;
            }

            // Determine predicate vs expression context
            boolean predicate = !isLogMessage && isPredicate(catalog, lines, i, lineIndent);
            checkText(errors, catalog, simpleText, lineNum, predicate, isLogMessage,
                    hasAncestorEip(lines, i, lineIndent, "aggregate"));
            // resolveResource: true loads the resource the result names, so there a resource: value is right
            if (!predicate && !isLogMessage
                    && !"true".equals(findSiblingValue(lines, textLine, countLeadingSpaces(lines[textLine]),
                            "resolveResource"))) {
                String resource = resourceAsAValue(simpleText);
                if (resource != null) {
                    errors.add("Line " + lineNum + ": " + resource);
                }
            }
        }
        // ${property.x}: the hint of the parser, with where the route keeps x
        return HeaderPropertyMixups.withWhereKept(errors, content);
    }

    /**
     * The checks of one simple text, whatever the DSL it is written in: its syntax, as a predicate or an expression, a
     * ternary operator written where it is literal text, and an elvis operator inside ${...} that is not read as one.
     *
     * @param lineNum     the line of the text, 1-based
     * @param predicate   whether the EIP evaluates the text as a predicate
     * @param logMessage  whether the text is the message of a log, where a ? followed by a : is most often a sentence
     * @param inAggregate whether the text is inside an aggregate, for the hint on the size of the group
     */
    static void checkText(
            List<String> errors, CamelCatalog catalog, String text, int lineNum, boolean predicate, boolean logMessage,
            boolean inAggregate) {
        if (text.startsWith("resource:")) {
            // the expression is loaded from a file (resource:classpath:script.txt), which is not the text here.
            // The location is a template itself, so a ternary written in it is still literal text (CAMEL-25485)
            if (!predicate && !logMessage) {
                String ternary = topLevelTernary(text);
                if (ternary != null) {
                    errors.add("Line " + lineNum + ": " + ternary);
                }
            }
            return;
        }
        boolean syntaxError = false;
        try {
            LanguageValidationResult result = predicate
                    ? catalog.validateLanguagePredicate(null, "simple", text)
                    : catalog.validateLanguageExpression(null, "simple", text);
            if (!result.isSuccess()) {
                String error = result.getShortError() != null ? result.getShortError() : result.getError();
                if (error != null && !isMissingDependency(error) && !isOfTheApplication(error)) {
                    syntaxError = true;
                    errors.add("Line " + lineNum + ": Simple syntax error: " + error
                               + (inAggregate ? aggregatedSizeHint(error) : ""));
                }
            }
        } catch (Exception e) {
            // best effort
        }

        // the parser cannot report this one: the expression is valid, it just does not mean what it says.
        // Only a reported syntax error suppresses it, to keep one message per expression; a catalog that could
        // not be asked at all does not, because this check does not depend on it.
        if (!syntaxError && !predicate && !logMessage) {
            String ternary = topLevelTernary(text);
            if (ternary != null) {
                errors.add("Line " + lineNum + ": " + ternary);
            }
        }
        // the same for an elvis operator inside ${...} that is not read as one, in any kind of text: the catalog
        // parses it with the CLI's own Camel, so it also says when the version the route is for does not read it
        if (!syntaxError) {
            String elvis = elvisInsideFunction(text, catalogVersion(catalog));
            if (elvis != null) {
                errors.add("Line " + lineNum + ": " + elvis);
            }
            String unknown = unknownFunctionWithNested(text, catalog);
            if (unknown != null) {
                errors.add("Line " + lineNum + ": " + unknown);
            }
        }
    }

    /** The name a function starts with: size of ${size:${body}}. */
    private static final Pattern FUNCTION_NAME = Pattern.compile("[A-Za-z][\\w-]*");

    /**
     * A function whose name Simple does not have, with a function nested in it: ${size:${body}}. The parser takes such
     * a function as a whole only when it evaluates it, so the expression passes the check and fails on the first
     * message with "Unknown function: size:${body}" (r27 rest-code-first step 1: 4 errors before ${body.size()}). The
     * catalog is asked with the nested functions as plain text, and only its "Unknown function" of that name counts.
     *
     * @param  text    the simple text
     * @param  catalog the catalog that parses it
     * @return         the message, or null
     */
    static String unknownFunctionWithNested(String text, CamelCatalog catalog) {
        if (text == null || catalog == null || !text.contains("${")) {
            return null;
        }
        for (int open = text.indexOf("${"); open >= 0; open = text.indexOf("${", open + 2)) {
            int close = closingBrace(text, open);
            if (close < 0) {
                continue;
            }
            String inner = text.substring(open + 2, close);
            int nested = inner.indexOf("${");
            Matcher name = FUNCTION_NAME.matcher(inner);
            if (nested <= 0 || !name.lookingAt() || name.end() > nested) {
                continue;
            }
            // ${maskEmail(${header.email})} can call a custom function (a SimpleFunction in the registry), which the
            // catalog does not know: only a name that is not followed by ( is never one
            if (name.end() < inner.length() && inner.charAt(name.end()) == '(') {
                continue;
            }
            // the nested functions as a plain value
            StringBuilder plain = new StringBuilder();
            int from = 0;
            for (int n = inner.indexOf("${"); n >= 0; n = inner.indexOf("${", from)) {
                int end = closingBrace(inner, n);
                if (end < 0) {
                    break;
                }
                plain.append(inner, from, n).append('x');
                from = end + 1;
            }
            plain.append(inner.substring(from));
            String error;
            try {
                LanguageValidationResult result = catalog.validateLanguageExpression(null, "simple", "${" + plain + "}");
                error = result.isSuccess() ? null : result.getShortError() != null ? result.getShortError() : result.getError();
            } catch (Exception e) {
                error = null;
            }
            if (error == null || !error.startsWith("Unknown function: " + name.group())) {
                continue;
            }
            String function = text.substring(open, close + 1);
            String written = name.group() + (name.end() < inner.length() ? String.valueOf(inner.charAt(name.end())) : "");
            boolean size = Set.of("size", "count", "length").contains(name.group());
            return "Simple has no function " + written + ", so " + function + " fails when the route runs (Unknown"
                   + " function: " + inner + ")" + (size ? ": the number of elements of the body is ${body.size()}" : "");
        }
        return null;
    }

    /** The functions whose text in parentheses is another language: ${jsonpath($[?(@.sku == 'A')])} is right. */
    private static final Pattern OTHER_LANGUAGE = Pattern.compile("(jsonpath|jq|xpath)\\s*\\(");

    /** [?(@.orderId == '${header.id}')]: a JsonPath filter that compares a field. */
    private static final Pattern JSONPATH_FILTER = Pattern.compile(
            "\\[\\?\\(\\s*@\\.([A-Za-z_]\\w*)\\s*(==|!=|<=|>=|<|>)\\s*(.+?)\\s*\\)\\s*]");

    /** .?[orderId == '${header.id}']: a SpEL selection that compares a field (.^[ the first match, .$[ the last). */
    private static final Pattern SPEL_SELECTION = Pattern.compile(
            "\\.[?^$]\\[\\s*(?:#this\\.)?([A-Za-z_]\\w*)\\s*(==|!=|<=|>=|<|>)\\s*(.+?)\\s*]");

    /** .![orderId]: a SpEL projection of a field. */
    private static final Pattern SPEL_PROJECTION = Pattern.compile("\\.!\\[\\s*(?:#this\\.)?([A-Za-z_]\\w*)\\s*]");

    /**
     * What a function reads the filter on: the body (as written, or bodyAs a type), or a header, a variable or an
     * exchange property, then fields; a field of the body is a step of the path, the others the source of jsonpath.
     */
    private static final Pattern FILTERED = Pattern.compile(
            "(body|(?:mandatoryB|b)odyAs\\([^)]*\\)|(header|headers|variable|variables|exchangeProperty)[.:]([\\w-]+))"
                                                            + "((?:\\??\\.[A-Za-z_]\\w*|\\[\\s*'?[A-Za-z_]\\w*'?\\s*])*)\\.?");

    /** One field of what a function reads: .lines, ?.lines, [lines] or ['lines']. */
    private static final Pattern FIELD = Pattern.compile("[A-Za-z_]\\w*");

    /**
     * A filter or a projection of another language inside a function, which the parser reads as a key or a method: the
     * expression passes and fails on the first message. r25 rest-code-first step 1:
     * <tt>${bodyAs(List).?[orderId == '${header.id}'].size()}</tt> failed with "ClassNotFoundException: List" and
     * "Method with name: ? not found", and <tt>${body[?(@.orderId == '${header.id}')].size()}</tt> with "Key
     * ?(@.orderId == 'ORD-1001') not found". The message gives the jsonpath step that does it, as YAML lines.
     * <p/>
     * The same for a filter written right after the function, <tt>${body}?.?[orderId == '${header.id}']</tt> (r27
     * rest-code-first step 1): text there, which the parser refused in a predicate with "operators and values must be
     * separated by a space", and took as literal text in an expression.
     *
     * @param  text the simple text
     * @return      the message, or null when no function has such a filter
     */
    static String filterInFunction(String text) {
        if (text == null || !text.contains("${")) {
            return null;
        }
        // every function, the nested ones too: each is read without the functions nested in it
        for (int open = text.indexOf("${"); open >= 0; open = text.indexOf("${", open + 2)) {
            int close = closingBrace(text, open);
            if (close < 0) {
                continue;
            }
            String inner = text.substring(open + 2, close);
            if (OTHER_LANGUAGE.matcher(inner).lookingAt()) {
                continue;
            }
            int at = filterIndex(inner);
            if (at >= 0) {
                return filterMessage(inner, at, null);
            }
            // a filter right after the closing brace, after a null-safe ?. or not
            String after = text.substring(close + 1);
            after = after.startsWith("?.") ? after.substring(1) : after;
            if (filterIndex(after) == 0) {
                return filterMessage(inner + after, inner.length(), text.substring(open, close + 1));
            }
        }
        return null;
    }

    /**
     * The index of the first filter or projection of the text of a function, outside its quoted literals and nested
     * functions: [?( or @. of JsonPath, .?[ .^[ .$[ or .![ of SpEL; -1 when it has none.
     */
    private static int filterIndex(String inner) {
        char quote = 0;
        for (int i = 0; i < inner.length(); i++) {
            char ch = inner.charAt(i);
            if (quote != 0) {
                if (ch == quote) {
                    quote = 0;
                }
            } else if (ch == '\'' || ch == '"') {
                quote = ch;
            } else if (inner.startsWith("${", i)) {
                int close = closingBrace(inner, i);
                if (close < 0) {
                    return -1;
                }
                i = close;
            } else if (inner.startsWith("[?(", i) || inner.startsWith("@.", i)
                    || ch == '.' && i + 2 < inner.length() && "?^$!".indexOf(inner.charAt(i + 1)) >= 0
                            && inner.charAt(i + 2) == '[') {
                return i;
            }
        }
        return -1;
    }

    /**
     * The message for a filter at the index of the text of a function, with the jsonpath step that picks it; the
     * function is the one the filter was written after, or null when it is inside it.
     */
    private static String filterMessage(String inner, int at, String function) {
        boolean spel = inner.charAt(at) == '.';
        String written = spel ? inner.substring(at, at + 3) + "...]" : inner.startsWith("@.", at) ? "@." : "[?(...)]";
        StringBuilder sb = new StringBuilder("Simple has no ")
                .append(spel && inner.charAt(at + 1) == '!' ? "projection" : "filter").append(": ").append(written)
                .append(spel ? " is SpEL" : " is JsonPath");
        if (function != null) {
            sb.append(", and after ").append(function).append(" it is text, not a part of the function. ");
        } else {
            sb.append(", which Simple reads as a ").append(spel ? "method" : "key").append(", so it fails at runtime. ");
        }
        // the path to the List the filter is on, and where it is when not in the body
        String root = "$";
        String source = null;
        Matcher filtered = FILTERED.matcher(inner.substring(0, at));
        if (filtered.matches()) {
            Matcher field = FIELD.matcher(filtered.group(4));
            StringBuilder fields = new StringBuilder();
            while (field.find()) {
                fields.append('.').append(field.group());
            }
            root += fields;
            if (filtered.group(2) != null) {
                String kind = filtered.group(2);
                source = (kind.startsWith("header") ? "header" : kind.startsWith("variable") ? "variable" : "property")
                         + ":" + filtered.group(3);
            }
        }
        String path;
        boolean one = false;
        Matcher m = (spel ? SPEL_SELECTION : JSONPATH_FILTER).matcher(inner.substring(at));
        Matcher projection = SPEL_PROJECTION.matcher(inner.substring(at));
        if (m.lookingAt()) {
            String value = m.group(3);
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
                value = "'" + value.substring(1, value.length() - 1) + "'";
            } else if (value.startsWith("${") && value.endsWith("}")) {
                value = "'" + value + "'";
            } else if (!value.matches("'.*'|-?\\d+(\\.\\d+)?|true|false|null")) {
                // a name of the other language, such as headers.id in SpEL: here the value is a header
                value = "'${header.id}'";
            }
            one = "==".equals(m.group(2));
            path = root + "[?(@." + m.group(1) + " " + m.group(2) + " " + value + ")]";
            sb.append(one
                    ? "To pick the element whose " + m.group(1) + " is " + value
                      + ", set the body with jsonpath before this step; the body is then that element, or null when"
                      + " none or several match (test that with ${body} == null):"
                    : "To keep the elements whose " + m.group(1) + " " + m.group(2) + " " + value
                      + ", set the body with jsonpath before this step; the body is then a List of them:");
        } else if (spel && projection.lookingAt()) {
            path = root + "[*]." + projection.group(1);
            sb.append("To get the ").append(projection.group(1)).append(" of each element, set the body with jsonpath")
                    .append(" before this step; the body is then a List of them:");
        } else {
            one = true;
            path = root + "[?(@.field == '${header.id}')]";
            sb.append("To pick the element whose field has a given value, such as the header id, set the body with")
                    .append(" jsonpath before this step; the body is then that element, or null when none or several")
                    .append(" match:");
        }
        sb.append("\n- setBody:\n    expression:\n      jsonpath:\n        expression: ")
                .append(path.contains("\"") ? "'" + path.replace("'", "''") + "'" : "\"" + path + "\"");
        if (source != null) {
            sb.append("\n        source: ").append(source);
        }
        if (one) {
            sb.append("\n        resultType: java.util.Map");
        }
        return sb.toString();
    }

    /** The Camel version from which the elvis operator may be written inside ${...} (CAMEL-26401). */
    static final String ELVIS_INSIDE_SINCE = "4.24";

    /**
     * An elvis operator inside ${...} that does not do what it looks like, as the message to report, or null. Without a
     * space on both sides it is not an operator but part of the function, so <tt>${header.offset ?:0}</tt> reads a
     * header named <tt>offset ?:0</tt>: the expression is valid and evaluates to null, which a model took for a form
     * that works, wrote it into its SQL and got a BadSqlGrammarException (CAMEL-26401). With the spaces it is the
     * operator from Camel 4.24, and an older Camel refuses it with "${ cannot accept ?:".
     * <p/>
     * The rewrite with the operator after the function comes first: it works in every version.
     *
     * @param  text         the simple text
     * @param  camelVersion the Camel version the text is for, or null for the CLI's own
     * @return              the message, or null when the text has no such operator
     */
    static String elvisInsideFunction(String text, String camelVersion) {
        if (text == null || !text.contains("?:") || !text.contains("${")) {
            return null;
        }
        boolean readsInside = readsElvisInside(camelVersion);
        List<String> messages = new ArrayList<>();
        List<String> written = new ArrayList<>();
        // every function, the nested ones too: each is checked with its own text
        for (int open = text.indexOf("${"); open >= 0; open = text.indexOf("${", open + 2)) {
            int close = closingBrace(text, open);
            if (close < 0) {
                continue;
            }
            String inner = text.substring(open + 2, close);
            int at = elvisIndex(inner);
            if (at < 0) {
                continue;
            }
            String left = inner.substring(0, at).strip();
            String right = inner.substring(at + 2).strip();
            boolean spaced = at > 0 && inner.charAt(at - 1) == ' ' && at + 2 < inner.length() && inner.charAt(at + 2) == ' ';
            if (left.isEmpty() || right.isEmpty() || spaced && readsInside) {
                continue;
            }
            String function = text.substring(open, close + 1);
            String outside = "${" + left + "} ?: " + quoted(right);
            written.add(function);
            if (spaced) {
                messages.add("`" + function + "` needs Camel " + ELVIS_INSIDE_SINCE + " or later, where the elvis operator"
                             + " may be written inside ${...} (here Camel " + camelVersion + "): write it after the"
                             + " function: `" + outside + "`");
            } else {
                messages.add("`" + function + "` " + readAs(inner) + ": the elvis operator needs a space on both sides"
                             + " and goes after the function: `" + outside + "`"
                             + (readsInside
                                     ? " (from Camel " + ELVIS_INSIDE_SINCE + " also `${" + left + " ?: " + quoted(right)
                                       + "}`, the form for a single ${...} such as a camel-sql :#${...} parameter)"
                                     : ""));
            }
        }
        if (messages.isEmpty()) {
            return null;
        }
        return messages.get(0)
               + (written.size() > 1 ? "; the same for " + String.join(", ", written.subList(1, written.size())) : "");
    }

    /**
     * What the text of a function with an elvis operator that is not one reads, such as a header named 'offset ?:0'.
     */
    private static String readAs(String inner) {
        String[][] kinds = {
                { "header.", "a header" }, { "headers.", "a header" }, { "exchangeProperty.", "an exchange property" },
                { "variable.", "a variable" } };
        for (String[] kind : kinds) {
            if (inner.startsWith(kind[0]) && inner.length() > kind[0].length()) {
                return "reads " + kind[1] + " named '" + inner.substring(kind[0].length()) + "'";
            }
        }
        return "is one function, '" + inner + "', not a value with a fallback";
    }

    /** Whether the Camel version reads the elvis operator inside ${...}: unknown is the CLI's own, which does. */
    static boolean readsElvisInside(String camelVersion) {
        if (camelVersion == null || !camelVersion.matches("\\d+\\.\\d+(?:\\..*)?")) {
            return true;
        }
        return VersionHelper.isGE(VersionHelper.getMajorMinorVersion(camelVersion), ELVIS_INSIDE_SINCE);
    }

    private static String catalogVersion(CamelCatalog catalog) {
        try {
            return catalog != null ? catalog.getCatalogVersion() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The index of the first ?: of the text of a function outside quotes, brackets, parentheses and nested functions,
     * or -1: the places where the parser reads it as the elvis operator when it has its spaces.
     */
    private static int elvisIndex(String inner) {
        char quote = 0;
        int depth = 0;
        for (int i = 0; i < inner.length() - 1; i++) {
            char ch = inner.charAt(i);
            if (quote != 0) {
                if (ch == quote) {
                    quote = 0;
                }
            } else if (ch == '\'' || ch == '"') {
                quote = ch;
            } else if (ch == '(' || ch == '[' || ch == '{') {
                depth++;
            } else if (ch == ')' || ch == ']' || ch == '}') {
                depth--;
            } else if (depth == 0 && ch == '?' && inner.charAt(i + 1) == ':') {
                return i;
            }
        }
        return -1;
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
        int colon = question < 0 ? -1 : indexOfTopLevel(text, " : ", question + 3);
        if (colon < 0) {
            return null;
        }
        // the expression itself, rewritten: with only an example, models wrote the same top-level ternary up to 20
        // times in a row (a step of the local-model benchmark)
        String rewritten = ternaryInsideOneFunction(text, question, colon);
        if (rewritten != null && rewritten.startsWith("resource:") && !text.startsWith("resource:")) {
            // the resource: of the values went before the function: say why, or the model writes it inside again (r27
            // of the local-model benchmark: the body was the text resource:file:order.json, then a JsonParseException)
            String scheme = rewritten.substring(0, rewritten.indexOf("${"));
            return "Simple has no top-level ternary: the ? at index " + (question + 1)
                   + " and its : are outside ${...}, so they are literal text and the operator is never evaluated."
                   + " Write the whole ternary inside one function, and " + scheme + " before it: " + rewritten
                   + " (a function's result is never loaded as a resource: with " + scheme + " inside the function,"
                   + " the body is the text " + unquoted(text.substring(question + 3, colon).trim()) + ", not the file)";
        }
        return "Simple has no top-level ternary: the ? at index " + (question + 1)
               + " and its : are outside ${...}, so they are literal text and the operator is never evaluated."
               + (rewritten != null
                       ? " Write the whole ternary inside one function: " + rewritten
                       : " Write the whole ternary inside one function, as in ${body.size() == 0 ? ${null} : ${body[0]}}");
    }

    /** A binary operator of Simple at the end of the text, after a function: the function is part of the condition. */
    private static final Pattern OPERATOR_AFTER_A_FUNCTION = Pattern.compile(
            "}\\s*(?:==|!=|>=|<=|>|<|=~|!=~|contains|!contains|~~|regex|!regex|in|!in|range|!range|startsWith|endsWith"
                                                                             + "|is|!is)\\s*$");

    /**
     * The top-level ternary written inside one function, or null when no function comes before the <tt>?</tt>: the
     * function the condition starts with is opened before the condition and closed after the second value, and a value
     * written as bare text is quoted. <tt>resource:file:${exchangeProperty.CamelTimerCounter} == 1 ? order.json :
     * order-bad-email.json</tt> is <tt>resource:file:${exchangeProperty.CamelTimerCounter == 1 ? 'order.json' :
     * 'order-bad-email.json'}</tt>, and so is
     * <tt>${exchangeProperty.CamelTimerCounter} == 1 ? resource:file:order.json :
     * resource:file:order-bad-email.json</tt>, with the values quoted or not.
     */
    static String ternaryInsideOneFunction(String text, int question, int colon) {
        // the functions at the top level before the ?, as {start, end} with end the index of the closing brace
        List<int[]> functions = new ArrayList<>();
        for (int i = 0; i < question; i++) {
            if (text.charAt(i) == '$' && i < text.length() - 1 && text.charAt(i + 1) == '{') {
                int close = closingBrace(text, i);
                if (close < 0 || close > question) {
                    return null;
                }
                functions.add(new int[] { i, close });
                i = close;
            }
        }
        if (functions.isEmpty()) {
            return null;
        }
        // the last function before the ?, or for ${a} == ${b} ? x : y the function before the operator
        int index = functions.size() - 1;
        int[] last = functions.get(index);
        Matcher op = OPERATOR_AFTER_A_FUNCTION.matcher(text.substring(0, last[0]));
        if (index > 0 && op.find() && op.start() == functions.get(index - 1)[1]) {
            index--;
        }
        int start = functions.get(index)[0];
        int end = functions.get(index)[1];
        String condition = text.substring(start + 2, end) + text.substring(end + 1, question);
        String prefix = text.substring(0, start);
        String first = text.substring(question + 3, colon).trim();
        String second = text.substring(colon + 3).trim();
        // resource: is read only at the start of the expression: inside the function, 'resource:file:order.json' is the
        // text the body becomes, so a resource: both values start with goes before the function (the local-model
        // benchmark: the body was the text resource:file:order.json, and the next step failed to parse it as JSON).
        // The values may be quoted already, as r27 wrote them: 'resource:file:order.json'
        String firstText = unquoted(first);
        String secondText = unquoted(second);
        Matcher scheme = RESOURCE_SCHEME.matcher(firstText);
        if (prefix.isEmpty() && scheme.lookingAt() && secondText.startsWith(scheme.group())
                && firstText.length() > scheme.end() && secondText.length() > scheme.end()) {
            prefix = scheme.group();
            first = firstText.substring(scheme.end());
            second = secondText.substring(scheme.end());
        }
        return prefix + "${" + condition + " ? " + quoted(first) + " : " + quoted(second) + "}";
    }

    /** A value of the ternary without the quotes of a quoted literal: 'order.json' is order.json. */
    private static String unquoted(String value) {
        if (value.length() > 1 && (value.startsWith("'") && value.endsWith("'")
                || value.startsWith("\"") && value.endsWith("\""))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    /** The resource: and its scheme a value starts with, such as <tt>resource:file:</tt>. */
    private static final Pattern RESOURCE_SCHEME = Pattern.compile("resource:[a-z]+:");

    /** A quoted literal of a resource: and its scheme, as a whole value: 'resource:file:order.json'. */
    private static final Pattern QUOTED_RESOURCE = Pattern.compile("(['\"])(resource:[a-z]+:)(.+)\\1");

    /**
     * A resource: written as a value a function gives: a value of a ternary or an elvis inside ${...}. The value is the
     * text, such as resource:file:order.json, and nothing loads it: resource: is read only at the start of the
     * expression. r27 of the local-model benchmark wrote
     * <tt>${exchangeProperty.CamelTimerCounter == 1 ? 'resource:file:order.json' : 'resource:file:order-bad-email.json'}</tt>,
     * the write was accepted, and the next step failed to parse that text as JSON (12 errors in a run). Not said for an
     * expression that starts with resource:, whose location is loaded, nor for an elvis after a function, which a route
     * that worked had as a step whose body the next step replaced.
     *
     * @param  text the simple text, used as an expression
     * @return      the message, or null when no value is a resource:
     */
    static String resourceAsAValue(String text) {
        if (text == null || text.startsWith("resource:") || !text.contains("resource:") || !text.contains("${")) {
            return null;
        }
        // the values of a ternary and of an elvis inside each function, the nested ones too
        for (int open = text.indexOf("${"); open >= 0; open = text.indexOf("${", open + 2)) {
            int close = closingBrace(text, open);
            if (close < 0) {
                continue;
            }
            String inner = text.substring(open + 2, close);
            List<String> values = new ArrayList<>();
            int question = indexOfTopLevel(inner, " ? ", 0);
            int colon = question < 0 ? -1 : indexOfTopLevel(inner, " : ", question + 3);
            if (colon > 0) {
                values.add(inner.substring(question + 3, colon).trim());
                values.add(inner.substring(colon + 3).trim());
            }
            int elvis = indexOfTopLevel(inner, " ?: ", 0);
            if (elvis > 0) {
                values.add(inner.substring(elvis + 4).trim());
            }
            // the condition, for the rewrite: only when the function is the whole expression
            String condition = colon > 0 && open == 0 && close == text.length() - 1 ? inner.substring(0, question) : null;
            String message = resourceValueMessage(text.substring(open, close + 1), values, condition);
            if (message != null) {
                return message;
            }
        }
        return null;
    }

    /**
     * The message for the values of an expression of which one is a quoted resource:, with resource: before the
     * function when both values of the ternary of the condition are of the same scheme; null when none is a resource:.
     */
    private static String resourceValueMessage(String written, List<String> values, String condition) {
        List<Matcher> resources = new ArrayList<>();
        for (String value : values) {
            Matcher m = QUOTED_RESOURCE.matcher(value);
            if (m.matches()) {
                resources.add(m);
            }
        }
        if (resources.isEmpty()) {
            return null;
        }
        String scheme = resources.get(0).group(2);
        StringBuilder sb = new StringBuilder("`").append(written).append("` gives the text ").append(scheme)
                .append(resources.get(0).group(3)).append(", not the file: a function's result is never loaded as a")
                .append(" resource, only a resource: at the start of the expression is.");
        if (condition != null && values.size() == 2 && resources.size() == 2
                && scheme.equals(resources.get(1).group(2))) {
            sb.append(" Write ").append(scheme).append(" before the function: ").append(scheme).append("${")
                    .append(condition).append(" ? ").append(quoted(resources.get(0).group(3))).append(" : ")
                    .append(quoted(resources.get(1).group(3))).append("}");
        } else {
            sb.append(" Write ").append(scheme).append(" at the start of the expression, and a function only for the")
                    .append(" part of the name that changes, as in ").append(scheme).append("order-${header.id}.json");
        }
        return sb.toString();
    }

    /** The index of the brace that closes the function opened at the index, or -1. */
    private static int closingBrace(String text, int open) {
        char quote = 0;
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
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
            } else if (ch == '{') {
                // a brace of the function's own text, such as a closure ${body.find { it.x }}
                depth++;
            } else if (ch == '}' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /**
     * A value of the ternary: bare text is quoted, a quoted literal, a function, a number, true, false and null not.
     * Text with a single quote of its own (O'Brien) is quoted with double quotes, and text with both quotes stays as
     * written.
     */
    private static String quoted(String value) {
        if (value.isEmpty() || value.startsWith("'") || value.startsWith("\"") || value.startsWith("${")
                || value.matches("-?\\d+(\\.\\d+)?|true|false|null")) {
            return value;
        }
        if (value.contains("'")) {
            return value.contains("\"") ? value : "\"" + value + "\"";
        }
        return "'" + value + "'";
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
     * Whether the error is about something only the application has: a class of its own (${type:com.acme.Codes.OK},
     * which the classpath of the check does not have), or a custom function it registers (${function(name)}).
     */
    static boolean isOfTheApplication(String error) {
        return error.contains("ClassNotFoundException") || error.startsWith("No custom simple function");
    }

    /**
     * A function served by a language or a component that is not on the classpath of the check (${jsonpath(...)},
     * ${a2a:text}): the route works when the dependency is there, which the check cannot know, so it is not reported.
     */
    static boolean isMissingDependency(String error) {
        return error.startsWith("No language could be found for:")
                || error.contains("requires camel-semantic")
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

    static boolean isPredicateOption(EipModel eip, String option) {
        return eip.getOptions().stream()
                .anyMatch(o -> option.equals(o.getName()) && "expression".equals(o.getKind()) && o.isAsPredicate());
    }

    /**
     * ${size} or ${count} written inside an aggregate: the parser's did-you-mean (${length}) is about the function,
     * what the author wants is the number of aggregated messages, an exchange property.
     */
    static String aggregatedSizeHint(String error) {
        if (!error.contains("Unknown function: size") && !error.contains("Unknown function: count")) {
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
