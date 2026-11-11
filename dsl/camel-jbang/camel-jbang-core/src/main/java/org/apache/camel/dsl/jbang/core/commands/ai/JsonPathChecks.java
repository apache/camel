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

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.catalog.CamelCatalog;

import static org.apache.camel.dsl.jbang.core.commands.ai.YamlLines.countLeadingSpaces;
import static org.apache.camel.dsl.jbang.core.commands.ai.YamlLines.extractYamlValue;

/**
 * Checks on the jsonpath expressions of a YAML route. A comparison written on the path ($.status == 'paid') is a
 * condition in a when or filter (the easy predicate syntax, CAMEL-24841) and not a path anywhere else: there the
 * runtime fails with a Jayway message about blank characters, and the check says what to write instead. Any other
 * expression is compiled with JsonPath, as the runtime does.
 */
public final class JsonPathChecks {

    private static final Pattern COMPARISON = Pattern.compile(" (==|!=|<=|>=|<|>|=~|in|nin|size|empty) ");
    // [] with nothing in it, which JsonPath does not have
    private static final Pattern EMPTY_BRACKETS = Pattern.compile("\\[\\s*]");
    // where a Jayway message points in the path it compiled (not "error on position", which is in the filter)
    private static final Pattern POSITION = Pattern.compile("(?<!on )(position:? )(\\d+)");
    // an index after a filter: $[?(@.orderId == 'ORD-1')][0]
    private static final Pattern INDEX_AFTER_FILTER = Pattern.compile("(\\[\\?\\(.*\\)])\\s*\\[\\s*-?\\d+\\s*]");
    private static final String ONE_ELEMENT = "set resultType: java.util.Map (the element when one matches, null when"
                                              + " none or several do)";
    // in a when or filter a filter is the condition: an empty list is false (ObjectHelper.evaluateValuePredicate)
    private static final String FILTER_IS_THE_CONDITION = "in a when or filter, the filter alone is the condition (true"
                                                          + " when an element matches)";

    private JsonPathChecks() {
    }

    public static List<String> validateYamlJsonPath(String content, CamelCatalog catalog) {
        List<String> errors = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return errors;
        }
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank() || line.trim().startsWith("#")) {
                continue;
            }
            String trimmed = line.trim();
            String key = trimmed.startsWith("- ") ? trimmed.substring(2) : trimmed;
            String text = null;
            int lineNum = i + 1;
            if (key.startsWith("jsonpath:") && !key.equals("jsonpath:")) {
                text = extractYamlValue(key, "jsonpath");
            } else if (key.equals("jsonpath:")) {
                for (int j = i + 1; j < lines.length; j++) {
                    String next = lines[j].trim();
                    if (next.isBlank()) {
                        continue;
                    }
                    if (next.startsWith("expression:")) {
                        text = extractYamlValue(next, "expression");
                        lineNum = j + 1;
                    }
                    break;
                }
            }
            if (text == null || text.isEmpty()) {
                continue;
            }
            boolean predicate = SimpleChecks.isPredicate(catalog, lines, i, countLeadingSpaces(line));
            String filter = filterProblem(text, predicate);
            if (filter != null) {
                errors.add("Line " + lineNum + ": " + filter);
                continue;
            }
            if (text.contains("[?(") || !COMPARISON.matcher(text).find() || predicate) {
                // a path, or a condition (the easy predicate syntax reads $.status == 'paid' as $[?(@.status ==
                // 'paid')]): compiled as the runtime does
                String compile = compileProblem(catalog, text, predicate, lines[lineNum - 1]);
                if (compile != null) {
                    errors.add("Line " + lineNum + ": " + compile);
                }
                continue;
            }
            String field = text.startsWith("$.") ? text.substring(2) : text.startsWith("$") ? text.substring(1) : text;
            int sp = field.indexOf(' ');
            String left = sp > 0 ? field.substring(0, sp) : field;
            String rest = sp > 0 ? field.substring(sp) : "";
            // ${body[key]} reads one key of a Map: the Simple form is offered for a top-level field only
            String simple = left.contains(".") ? "" : " and compare it in simple (${body[" + left + "]}" + rest + ")";
            errors.add("Line " + lineNum + ": jsonpath is a path, not a comparison: here it must give a value, write the"
                       + " path $." + left + simple + "; as a condition in a when or filter, " + text
                       + " is read as $[?(@." + left + rest + ")]");
        }
        return errors;
    }

    /**
     * What is wrong around the filter of a path. Jayway applies an index after a filter to each element the filter
     * matched, so $[?(@.orderId == 'ORD-1')][0] gives an empty list; and &amp;&amp; or || outside the filter is no
     * JsonPath (500 in a REST route). As a value, the one element comes with resultType java.util.Map, and a field of
     * it with the type of the field; in a when or filter, the filter alone is the condition.
     *
     * @param text      the jsonpath expression
     * @param predicate whether it is the condition of a when or filter
     */
    static String filterProblem(String text, boolean predicate) {
        if (!text.contains("[?(")) {
            return null;
        }
        Matcher index = INDEX_AFTER_FILTER.matcher(text);
        if (index.find()) {
            String problem = "an index after a filter gives an empty list in JsonPath ([?(...)][0] applies the index to"
                             + " each element the filter matched): ";
            if (predicate) {
                return problem + "leave the index out: " + FILTER_IS_THE_CONDITION;
            }
            String rest = text.substring(index.end()).strip();
            if (rest.isEmpty()) {
                return problem + "to get the one element, leave the index out and " + ONE_ELEMENT;
            }
            // $[?(...)][0].qty: the path without the index gives the qty of each match, a single match unwrapped
            return problem + "to get a value of the one element, leave the index out, as in "
                   + text.substring(0, index.start()) + index.group(1) + rest
                   + ", and set resultType to the type of that value, such as java.lang.Integer for a number (the"
                   + " value when one element matches)";
        }
        String outside = outsideFilters(text);
        if (outside.contains("&&") || outside.contains("||")) {
            String problem = "&& and || work inside a filter only, such as $[?(@.status == 'paid' && @.qty > 0)]";
            if (predicate) {
                return problem + ": " + FILTER_IS_THE_CONDITION + ", so move the other conditions inside it";
            }
            return problem + "; to test whether an element matches, use the filter as the condition: "
                   + FILTER_IS_THE_CONDITION;
        }
        return null;
    }

    /**
     * Why the expression does not compile in JsonPath, or null when it does or JsonPath cannot be had. Camel evaluates
     * the ${...} placeholders first, so each one is filled in with a number and then with a word (an index, a number to
     * compare, or a text in quotes); an expression that compiles in neither way is reported when JsonPath points at a
     * place outside the placeholders, which the placeholders do not change. r28 rest-code-first step 1: $[?(@.orderId
     * == '${header.id}')][] was accepted, and failed at runtime with "Could not parse token starting at position 29"
     * (16 errors in one run).
     *
     * @param predicate whether it is the condition of a when or filter, where a comparison on the path is the easy
     *                  predicate syntax, which camel-jsonpath turns into a filter first
     */
    static String compileProblem(CamelCatalog catalog, String text, boolean predicate) {
        return compileProblem(catalog, text, predicate, null);
    }

    /**
     * As {@link #compileProblem(CamelCatalog, String, boolean)}, with the YAML line that has the expression: the note
     * on [] ends with that line to write, which the write tool applies when the model sends the same lines a third time
     * (CAMEL-26758; r30 rest-code-first step 1 sent ...')][] 15 times in two runs, told to write it without the []).
     */
    static String compileProblem(CamelCatalog catalog, String text, boolean predicate, String line) {
        if (predicate && !text.contains("[?(") && COMPARISON.matcher(text).find()) {
            // the easy predicate syntax, which camel-jsonpath turns into a filter first
            return null;
        }
        if (text.startsWith("\"") || text.startsWith("'")) {
            // a quote the value does not close: the line is no YAML a value can be read from, which other checks say
            return null;
        }
        Method compile = compiler(catalog);
        if (compile == null) {
            return null;
        }
        Object noFilters = Array.newInstance(compile.getParameterTypes()[1].getComponentType(), 0);
        Filled failed = null;
        String error = null;
        for (String value : text.contains("${") || text.contains("{{") ? List.of("0", "x") : List.of("0")) {
            Filled filled = new Filled(text, value);
            try {
                compile.invoke(null, filled.text, noFilters);
                return null;
            } catch (InvocationTargetException e) {
                if (failed == null) {
                    failed = filled;
                    error = e.getTargetException().getMessage();
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                // JsonPath cannot check it here
                return null;
            }
        }
        Matcher position = error != null ? POSITION.matcher(error) : null;
        int at = position != null && position.find() ? failed.original(Integer.parseInt(position.group(2))) : -1;
        if (error == null || failed.placeholders && (at < 0 || failed.inPlaceholder(at))) {
            // what does not compile may be a placeholder: Camel fills it in first
            return null;
        }
        String why = error;
        if (at >= 0) {
            // the position in the text as written, and the text there
            why = error.substring(0, position.start(2)) + at + " (at "
                  + text.substring(at, Math.min(text.length(), at + 12)).strip() + ")" + error.substring(position.end(2));
        }
        String problem = "jsonpath " + text + " does not compile: " + why.strip();
        if (EMPTY_BRACKETS.matcher(text).find()) {
            String fixed = EMPTY_BRACKETS.matcher(text).replaceAll("");
            // the whole line when it has the expression as written once; else the expression alone
            boolean once = line != null && line.contains(text) && line.indexOf(text) == line.lastIndexOf(text);
            return problem + "; JsonPath has no [], and a filter or [*] gives a list already: "
                   + (once ? "write the line as " + line.replace(text, fixed).strip() : "write " + fixed);
        }
        return problem;
    }

    /**
     * JsonPath.compile of the JsonPath library: of this process when it has the library, else of camel-jsonpath, which
     * is downloaded for it once, as for an evaluation (CAMEL-24907); null when it cannot be had.
     */
    private static Method compiler(CamelCatalog catalog) {
        Method compile = compiler(JsonPathChecks.class.getClassLoader());
        if (compile == null) {
            ClassLoader loader = ExpressionEvaluator.languageLoader(catalog, "jsonpath");
            compile = loader != null ? compiler(loader) : null;
        }
        return compile;
    }

    private static Method compiler(ClassLoader loader) {
        try {
            Class<?> filter = Class.forName("com.jayway.jsonpath.Predicate", false, loader);
            return Class.forName("com.jayway.jsonpath.JsonPath", true, loader).getMethod("compile", String.class,
                    filter.arrayType());
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    /** A text with each ${...} (braces balanced) and {{...}} filled in, and where its characters are in the text. */
    private static final class Filled {
        final String text;
        final boolean placeholders;
        private final int[] original;
        private final List<int[]> spans = new ArrayList<>();
        private final int length;

        Filled(String written, String value) {
            StringBuilder sb = new StringBuilder();
            List<Integer> from = new ArrayList<>();
            int i = 0;
            while (i < written.length()) {
                int end = -1;
                if (written.startsWith("${", i)) {
                    int depth = 0;
                    for (int j = i + 1; j < written.length() && end < 0; j++) {
                        if (written.charAt(j) == '{') {
                            depth++;
                        } else if (written.charAt(j) == '}' && --depth == 0) {
                            end = j + 1;
                        }
                    }
                } else if (written.startsWith("{{", i) && written.indexOf("}}", i) > 0) {
                    end = written.indexOf("}}", i) + 2;
                }
                if (end > 0) {
                    spans.add(new int[] { i, end });
                    for (int k = 0; k < value.length(); k++) {
                        sb.append(value.charAt(k));
                        from.add(i);
                    }
                    i = end;
                } else {
                    sb.append(written.charAt(i));
                    from.add(i++);
                }
            }
            this.text = sb.toString();
            this.placeholders = !spans.isEmpty();
            this.original = from.stream().mapToInt(Integer::intValue).toArray();
            this.length = written.length();
        }

        /** Where a position of the filled text is in the text as written. */
        int original(int position) {
            return position < original.length ? original[position] : length;
        }

        /** Whether a position of the text as written is in a placeholder, or right after one. */
        boolean inPlaceholder(int position) {
            return spans.stream().anyMatch(s -> position >= s[0] && position <= s[1]);
        }
    }

    // the text with each filter [?(...)] taken out
    private static String outsideFilters(String text) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            if (text.startsWith("[?(", i)) {
                int depth = 0;
                int j = i + 2;
                for (; j < text.length(); j++) {
                    char c = text.charAt(j);
                    if (c == '(') {
                        depth++;
                    } else if (c == ')' && --depth == 0) {
                        break;
                    }
                }
                // past the closing )]
                i = Math.min(text.length(), j + 2);
            } else {
                sb.append(text.charAt(i++));
            }
        }
        return sb.toString();
    }
}
