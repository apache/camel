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
package org.apache.camel.dsl.yaml.validator;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The text of a jsonpath expression read as a path before the runtime reads it: what the JsonPath compiler refuses with
 * InvalidPathException on every message is said where the route is written. The validator does not have the JsonPath
 * library (with the JSON parser it needs, four more jars in the CLI for this one check), so the reading is light: the
 * brackets, parentheses and quotes of the path are closed, and nothing comes after the path. Only what the compiler
 * refuses is reported; it accepts more than JsonPath allows, such as $.a) with a parenthesis that closes nothing.
 */
final class JsonPathText {

    /**
     * What may follow a path with a space: an operator. In a when or a filter, $.status == 'paid' is the easy predicate
     * syntax of camel-jsonpath; anywhere else the comparison check of the AI tools says what to write.
     */
    private static final Pattern OPERATOR = Pattern.compile(
            "(===?|!==?|<=?|>=?|=~|&&|\\|\\||(in|nin|size|empty|subsetof|anyof|noneof|contains)(\\s|$))");

    /** The option written into the text: {resultType: java.util.Map}, or "with resultType java.util.Map". */
    private static final Pattern RESULT_TYPE = Pattern.compile("resultType\\W*([A-Za-z_][\\w.$]*)");

    private JsonPathText() {
    }

    /**
     * What makes the text of a jsonpath expression no path, as the message to report, or null when it reads as one:
     * text after the path, a bracket or a quote that is not closed, or a JSON text. The ${...} of Simple, which
     * camel-jsonpath evaluates before it compiles the path, and a {{...}} property placeholder are each read as one
     * value.
     *
     * @param text the text of the expression, as written
     * @param key  the key the text is written under: jsonpath in the short form, expression in the long one
     */
    static String problem(String text, String key) {
        String t = text.strip();
        if (t.startsWith("{") && !t.startsWith("{{")) {
            // r25 rest-code-first step 1: the JSON answer of a 404 written as jsonpath, which failed on a null body
            String language = t.contains("${") ? "simple" : "constant";
            return "the text is a JSON text, not a path: jsonpath reads a value of the body, with a path such as"
                   + " $.orderId. A JSON text is set with " + language + ". Write it as:\n" + language
                   + ":\n  expression: " + YamlValidator.yamlQuoted(t);
        }
        // the index of each bracket or parenthesis that is open
        Deque<Integer> open = new ArrayDeque<>();
        char quote = 0;
        int quoteAt = -1;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (t.startsWith("${", i) || t.startsWith("{{", i)) {
                int close = c == '$' ? t.indexOf('}', i) : t.indexOf("}}", i) + 1;
                if (close <= 0) {
                    return null;
                }
                i = close;
            } else if (quote != 0) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
            } else if (open.isEmpty()) {
                // a space ends the path, as for the compiler; a line break or a tab it takes into a property name
                if (c == ' ') {
                    String rest = t.substring(i).strip();
                    return OPERATOR.matcher(rest).lookingAt() ? null : textAfter(t.substring(0, i), rest, i, key);
                }
                if (c == '[' || c == '(') {
                    open.push(i);
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
                quoteAt = i;
            } else if (c == '/' && afterRegexOperator(t, i)) {
                // the regular expression of =~, such as /.*REES/i, which may have brackets of its own
                int end = i + 1;
                while (end < t.length() && (t.charAt(end) != '/' || t.charAt(end - 1) == '\\')) {
                    end++;
                }
                if (end >= t.length()) {
                    return null;
                }
                i = end;
            } else if (c == '[' || c == '(') {
                open.push(i);
            } else if (c == ']' || c == ')') {
                open.pop();
            }
        }
        if (quote != 0) {
            return notClosed(quote, quoteAt);
        }
        return open.isEmpty() ? null : notClosed(t.charAt(open.peekLast()), open.peekLast());
    }

    /**
     * Text after the path, said with the line to write. r25 rest-code-first step 1: the words "with resultType
     * java.util.Map" of a message were copied into the text as {resultType: java.util.Map}, and every request failed
     * with "InvalidPathException: Could not parse token starting at position 29".
     */
    private static String textAfter(String path, String rest, int at, String key) {
        String problem = "the path ends at position " + at + ", and the text after it is not part of a path: " + rest
                         + ". The runtime reads the whole text as the path and fails with InvalidPathException";
        Matcher type = RESULT_TYPE.matcher(rest);
        if (type.find()) {
            return problem + ". resultType is an option of jsonpath, a key of its own next to expression. Write it"
                   + " as:\njsonpath:\n  expression: " + YamlValidator.yamlQuoted(path) + "\n  resultType: "
                   + type.group(1);
        }
        return problem + ". Write the line as:\n" + key + ": " + YamlValidator.yamlQuoted(path);
    }

    private static String notClosed(char c, int at) {
        String close = c == '[' ? "]" : c == '(' ? ")" : String.valueOf(c);
        return "the " + c + " at position " + at + " has no closing " + close + ", so the text is not a path: the"
               + " runtime fails with InvalidPathException on every message";
    }

    /** Whether the / at the index starts the regular expression of =~. */
    private static boolean afterRegexOperator(String t, int i) {
        int j = i - 1;
        while (j >= 0 && t.charAt(j) == ' ') {
            j--;
        }
        return j >= 1 && t.charAt(j) == '~' && t.charAt(j - 1) == '=';
    }
}
