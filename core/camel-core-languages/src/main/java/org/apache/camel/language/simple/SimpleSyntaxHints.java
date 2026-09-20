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
package org.apache.camel.language.simple;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Hints that turn a Simple parser error into a message that says what to write (CAMEL-24703). Every consumer of the
 * parser (routes at startup, the catalog validator, camel validate, the TUI and the MCP tools) shows the message, so a
 * hint here reaches humans and AI agents alike.
 */
public final class SimpleSyntaxHints {

    public static final String OPERATORS = "==, =~, !=, <, <=, >, >=, contains, !contains, ~~, !~~, regex, !regex, in, !in, "
                                           + "is, !is, range, !range, startsWith, endsWith, && and ||";

    /** The value forms an operator accepts on its right hand side. */
    public static final String VALUE_FORMS = "a quoted literal 'x', a number, true, false, null, or a function ${...}";

    /** Function names a model is likely to write, for the did-you-mean suggestion. */
    static final List<String> FUNCTIONS = List.of("body", "bodyAs", "mandatoryBodyAs", "bodyOneLine", "prettyBody",
            "originalBody", "header", "headerAs", "headers", "exchangeProperty", "exchangePropertyAs",
            "exchangeProperties", "variable", "variableAs", "variables", "exception", "exchange", "camelContext",
            "camelId", "routeId", "routeGroup", "stepId", "id", "messageTimestamp", "threadName", "threadId",
            "hostname", "date", "date-with-timezone", "random", "skip", "collate", "join", "sum", "avg", "min", "max",
            "replace", "substring", "substringBefore", "substringAfter", "substringBetween", "contains", "pad",
            "concat", "val", "length", "empty", "newEmpty", "iif", "hash", "convertTo", "throwException", "assert",
            "load", "uuid", "env", "sys", "ref", "bean", "properties", "propertiesExist", "type", "messageAs",
            "messageHistory", "pretty", "toJson", "toPrettyJson", "jq", "jsonpath", "xpath", "simpleJsonpath",
            "function", "list", "map", "range", "split", "sort", "forEach", "filter", "listAdd", "listRemove",
            "mapAdd", "mapRemove", "file", "null");

    /**
     * Functions that delegate to another language, all of them written {@code ${name(exp)}}. Unlike {@code bean:} or
     * {@code date:} they take no colon form, which is the mistake CAMEL-24845 is about.
     */
    static final Set<String> QUERY_FUNCTIONS = Set.of("jq", "jsonpath", "xpath", "simpleJsonpath");

    /** Names from older Camel versions or other languages that a model still writes. */
    static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("property", "exchangeProperty"),
            Map.entry("in.body", "body"),
            Map.entry("out.body", "body"),
            Map.entry("in.header", "header"),
            Map.entry("out.header", "header"),
            Map.entry("in.headers", "headers"),
            Map.entry("var", "variable"),
            Map.entry("prop", "exchangeProperty"),
            Map.entry("json", "jsonpath"),
            Map.entry("upper", "bodyAs(String).toUpperCase()"),
            Map.entry("lower", "bodyAs(String).toLowerCase()"),
            Map.entry("trim", "bodyAs(String).trim()"),
            Map.entry("padding", "pad"),
            Map.entry("now", "date:now:yyyy-MM-dd'T'HH:mm:ss"),
            Map.entry("timestamp", "messageTimestamp"),
            Map.entry("size", "length"),
            Map.entry("count", "length"));

    private static final String[] OPERATOR_WORDS = {
            "==", "!=", ">=", "<=", ">", "<", "=~", "!=~", "~~", "!~~", "contains",
            "!contains", "regex", "!regex", "in", "!in", "is", "!is", "range", "!range", "startsWith", "endsWith", "&&", "||" };

    private SimpleSyntaxHints() {
    }

    /** The whole word (up to whitespace on both sides) around the index in the expression. */
    public static String wordAt(String expression, int index) {
        if (expression == null || expression.isEmpty()) {
            return "";
        }
        int start = Math.min(Math.max(index, 0), expression.length());
        int end = start;
        while (start > 0 && !Character.isWhitespace(expression.charAt(start - 1))) {
            start--;
        }
        while (end < expression.length() && !Character.isWhitespace(expression.charAt(end))) {
            end++;
        }
        return expression.substring(start, end);
    }

    /** The message for a token the grammar does not know at the given index. */
    public static String unexpectedToken(String expression, int index) {
        String word = wordAt(expression, index);
        if (word.isEmpty()) {
            return "Unexpected token at location " + index;
        }
        String lower = word.toLowerCase(Locale.ROOT);
        switch (lower) {
            case "=":
            case "===":
            case "=<":
            case "=>":
                return "Unknown operator " + word + ": did you mean ==? Operators are " + OPERATORS;
            case "<>":
            case "=!=":
            case "!==":
            case "~=":
                return "Unknown operator " + word + ": did you mean !=? Operators are " + OPERATORS;
            case "and":
            case "&":
                return "Unknown operator " + word + ": use && for and, and || for or";
            case "or":
            case "|":
                return "Unknown operator " + word + ": use || for or, and && for and";
            case "not":
            case "!":
                return "Unknown operator " + word + ": negate the operator instead, e.g. != or !contains";
            default:
        }
        String name = functionName(word);
        if (isKnownFunction(name)) {
            String rewrite = expression.replace(word, "${" + word + "}");
            return "Unexpected token " + word + ": text outside ${...} is a literal, functions are written as ${body}, "
                   + "${header.name}; did you mean " + rewrite + "?";
        }
        return "Unexpected token " + word + ": text outside ${...} must be " + VALUE_FORMS + ", or an operator ("
               + OPERATORS + ")";
    }

    /** The message when an operator has no usable value next to it. */
    public static String unsupportedOperand(String kind, Object operator, String expression, int index) {
        String word = wordAt(expression, index);
        if ("Logical".equals(kind)) {
            return kind + " operator " + operator + " needs a predicate on the right hand side, e.g. ${header.foo} == 'bar'"
                   + (word.isEmpty() ? "" : "; was: " + word);
        }
        if (word.isEmpty() || word.equals(String.valueOf(operator))) {
            return kind + " operator " + operator + " needs a value on the right hand side: " + VALUE_FORMS;
        }
        return kind + " operator " + operator + " does not accept " + word + " on the right hand side: write it as "
               + VALUE_FORMS + (isKnownFunction(functionName(word)) ? ", e.g. ${" + word + "}" : ", e.g. '" + word + "'");
    }

    /**
     * When an operator is written inside the function (${body == 'x'}), the rewrite with the operator outside, else
     * null.
     */
    public static String operatorsOutside(String function) {
        int best = -1;
        String bestOp = null;
        for (String op : OPERATOR_WORDS) {
            int i = function.indexOf(" " + op + " ");
            if (i > 0 && (best < 0 || i < best)) {
                best = i;
                bestOp = op;
            }
        }
        if (bestOp == null) {
            return null;
        }
        String head = function.substring(0, best);
        if (head.contains("'") || head.contains("\"")) {
            return null;
        }
        int open = 0;
        for (int i = 0; i < head.length(); i++) {
            if (head.charAt(i) == '(') {
                open++;
            } else if (head.charAt(i) == ')') {
                open--;
            }
        }
        if (open > 0) {
            // the operator is inside an argument list that may hold a predicate (iif, filter, forEach)
            return null;
        }
        return "${" + head + "}" + function.substring(best);
    }

    /** A hint for an unknown function, or null. */
    public static String unknownFunction(String function) {
        if (function == null) {
            return null;
        }
        String trimmed = function.trim();
        if (!trimmed.equals(function)) {
            return "remove the spaces: ${" + trimmed + "}";
        }
        String rewrite = operatorsOutside(function);
        if (rewrite != null) {
            return "operators go outside the function: " + rewrite;
        }
        String bare = function.contains("(") ? function.substring(0, function.indexOf('(')) : function;
        bare = bare.contains(".") ? bare.substring(0, bare.indexOf('.')) : bare;
        if (bare.equals("simple")) {
            // ${simple} in a log message or a simple: expression: the text is already simple
            return "simple is the language, not a function: the text is already a simple expression, write the values"
                   + " with ${body}, ${header.name}, ${date:now:HH:mm:ss} and leave the rest as plain text";
        }
        if (bare.matches("groovy|xquery|mvel|ognl|spel|js|python|java|constant|tokenize|method")) {
            return bare + " is a language, not a simple function: another language cannot be nested inside ${...}; write"
                   + " the expression with its own key, for example " + bare + ": \"...\"";
        }
        // ${jsonpath:$.status}: written the way bean: and date: are, but these four take parentheses (CAMEL-24845)
        int colon = function.indexOf(':');
        if (colon > 0 && QUERY_FUNCTIONS.contains(function.substring(0, colon))) {
            return parentheses(function.substring(0, colon), function.substring(colon + 1));
        }
        if (QUERY_FUNCTIONS.contains(bare)) {
            // ${jsonpath}: a simple function of its own since QueryLanguageFunctionFactory, not a nested language
            return parentheses(bare, "exp");
        }
        if (function.matches(".*\\S\\s+[-+*/%]\\s+\\S.*")) {
            // ${exchangeCounter % 3}, ${header.total * 2}: there is no arithmetic in simple
            return "simple has no arithmetic operators (+ - * / %): compute the value in a groovy expression"
                   + " (groovy: \"...\") or in a bean, and use the result here";
        }
        String name = functionName(function);
        String rest = function.substring(name.length());
        String alias = ALIASES.get(name);
        if (alias == null && name.contains(".")) {
            alias = ALIASES.get(name.substring(0, name.indexOf('.')));
            if (alias != null) {
                alias = alias + name.substring(name.indexOf('.'));
            }
        }
        if (alias != null) {
            if (rest.startsWith(":") && QUERY_FUNCTIONS.contains(alias)) {
                // ${json:$.status}: the alias resolves to jsonpath, so the argument moves into parentheses too
                return parentheses(alias, rest.substring(1));
            }
            return "did you mean ${" + alias + rest + "}?";
        }
        for (String f : FUNCTIONS) {
            if (f.equalsIgnoreCase(name) && !f.equals(name)) {
                return "function names are case sensitive: ${" + f + rest + "}";
            }
        }
        String closest = closest(name);
        if (closest != null && !closest.equals(name)) {
            // a suggestion that repeats the rejected text teaches nothing, and the reader writes it again (CAMEL-24845)
            return "did you mean ${" + closest + rest + "}?";
        }
        return "the functions are documented on the simple language page (functions)";
    }

    /** The did-you-mean for a function whose argument was written after a colon instead of in parentheses. */
    private static String parentheses(String name, String argument) {
        return "the argument goes in parentheses: did you mean ${" + name + "(" + argument + ")}?";
    }

    static String functionName(String function) {
        int end = 0;
        while (end < function.length()) {
            char ch = function.charAt(end);
            if (ch == '(' || ch == ':' || ch == '[' || ch == '?' || Character.isWhitespace(ch)) {
                break;
            }
            if (ch == '.' && !function.startsWith("in.", 0) && !function.startsWith("out.", 0)
                    && !function.startsWith("date-with", 0)) {
                // header.foo and exchangeProperty.foo: the name is the part before the dot; in.body keeps its dot
                break;
            }
            end++;
        }
        return function.substring(0, end);
    }

    static boolean isKnownFunction(String name) {
        return !name.isEmpty() && (FUNCTIONS.contains(name) || ALIASES.containsKey(name));
    }

    static String closest(String name) {
        if (name.length() < 3) {
            return null;
        }
        // bodyxxx, headerz: a known function name followed by something that is not OGNL
        String prefix = null;
        for (String f : FUNCTIONS) {
            if (f.length() >= 3 && name.startsWith(f) && (prefix == null || f.length() > prefix.length())) {
                prefix = f;
            }
        }
        if (prefix != null) {
            return prefix;
        }
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        int threshold = Math.max(2, name.length() / 3);
        String lower = name.toLowerCase(Locale.ROOT);
        for (String f : FUNCTIONS) {
            int d = distance(lower, f.toLowerCase(Locale.ROOT));
            if (d <= threshold && (d < bestDistance || d == bestDistance && f.length() < best.length())) {
                best = f;
                bestDistance = d;
            }
        }
        return best;
    }

    static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }
}
