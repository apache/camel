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
package org.apache.camel.jsonpath.easypredicate;

import java.util.ArrayList;
import java.util.List;

import static org.apache.camel.jsonpath.easypredicate.EasyPredicateOperators.hasOperator;
import static org.apache.camel.jsonpath.easypredicate.EasyPredicateOperators.isOperator;

/**
 * To allow defining very easy jsonpath predicates using the syntax: left OP right
 * <p/>
 * The easy parser is only in use if the predicate do not start with the <tt>$</tt> sign which is used by jsonpath. The
 * parser is intended for predicates only.
 */
public class EasyPredicateParser {

    /**
     * Parses the predicate
     *
     * @param  predicate the predicate
     * @return           the parsed predicate or the original predicate if easy parser did not kick-in
     */
    public String parse(String predicate) {

        boolean rooted = false;
        if (predicate.startsWith("$")) {
            if (predicate.contains("[?(") || !hasOperator(predicate)) {
                // regular json path (a filter, or a plain path) so skip
                return predicate;
            }
            // $.status == 'paid': the easy form written with its root, the way a path is written (CAMEL-24841);
            // the filter goes on the parent of the field, the root here
            rooted = true;
            predicate = predicate.substring(1);
            if (predicate.startsWith(".")) {
                predicate = predicate.substring(1);
            }
        }

        // must have an operator
        if (!hasOperator(predicate)) {
            return predicate;
        }

        StringBuilder sb = new StringBuilder();

        // grab before operator
        String[] parts = tokens(predicate);

        // only support one operator currently
        if (parts.length == 3) {
            String prev = parts[0];
            String op = parts[1];
            String next = parts[2];
            if (isOperator(op)) {
                String before;
                String after;
                int pos = prev.lastIndexOf('.');
                if (pos == -1) {
                    // without a root the field is looked for anywhere ($..*), with a root ($.status) at the root
                    before = rooted ? "" : "..*";
                    after = prev;
                } else {
                    before = prev.substring(0, pos);
                    after = prev.substring(pos + 1);
                }
                sb.append("$");
                if (!before.isEmpty() && !before.startsWith(".")) {
                    sb.append(".");
                }
                sb.append(before);
                sb.append("[?(@.");
                sb.append(after);
                sb.append(" ");
                sb.append(op);
                sb.append(" ");
                sb.append(next);
                sb.append(")]");
            }
            return sb.toString();
        }

        // not able to parse so return as-is
        return predicate;
    }

    /**
     * Splits the predicate into: left OP right
     *
     * @param  predicate the predicate
     * @return           the splitted parts
     */
    private static String[] tokens(String predicate) {
        List<String> list = new ArrayList<>();

        StringBuilder part = new StringBuilder();
        // an operator inside a quoted value (such as 'Java in Action') is part of the value
        char quote = 0;
        for (int i = 0; i < predicate.length(); i++) {

            // is there a new operator
            String s = predicate.substring(i);
            String op = quote == 0 ? EasyPredicateOperators.getOperatorAtStart(s) : null;
            if (op != null) {
                if (part.length() > 0) {
                    list.add(part.toString());
                    part.setLength(0);
                }
                list.add(op.trim());
                // move i to the last char of the operator, and past the space after it (if any)
                i = i + op.length();
                if (i + 1 < predicate.length() && predicate.charAt(i + 1) == ' ') {
                    i++;
                }
            } else {
                char ch = predicate.charAt(i);
                if (quote != 0 && ch == '\\' && i + 1 < predicate.length()) {
                    // an escaped char (such as \' in 'it\'s') does not end the quote
                    part.append(ch);
                    ch = predicate.charAt(++i);
                } else if (quote == 0 && (ch == '\'' || ch == '"')) {
                    quote = ch;
                } else if (ch == quote) {
                    quote = 0;
                }
                part.append(ch);
            }
        }

        // ant leftovers
        if (part.length() > 0) {
            list.add(part.toString());
        }

        return list.toArray(new String[0]);
    }

}
