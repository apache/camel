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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.catalog.CamelCatalog;

import static org.apache.camel.dsl.jbang.core.commands.ai.YamlLines.countLeadingSpaces;
import static org.apache.camel.dsl.jbang.core.commands.ai.YamlLines.extractYamlValue;

/**
 * Checks on the jsonpath expressions of a YAML route. A comparison written on the path ($.status == 'paid') is a
 * condition in a when or filter (the easy predicate syntax, CAMEL-24841) and not a path anywhere else: there the
 * runtime fails with a Jayway message about blank characters, and the check says what to write instead.
 */
public final class JsonPathChecks {

    private static final Pattern COMPARISON = Pattern.compile(" (==|!=|<=|>=|<|>|=~|in|nin|size|empty) ");
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
            String filter = filterProblem(text, SimpleChecks.isPredicate(catalog, lines, i, countLeadingSpaces(line)));
            if (filter != null) {
                errors.add("Line " + lineNum + ": " + filter);
                continue;
            }
            if (text.contains("[?(") || !COMPARISON.matcher(text).find()) {
                continue;
            }
            if (SimpleChecks.isPredicate(catalog, lines, i, countLeadingSpaces(line))) {
                // a condition: the easy predicate syntax reads it as $[?(@.status == 'paid')]
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
