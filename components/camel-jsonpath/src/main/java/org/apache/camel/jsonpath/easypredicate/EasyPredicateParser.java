/**
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
 * The easy parser is only in use if the predicate do not start with the <tt>$</tt> sign which is used by jsonpath.
 * The parser is intended for predicates only.
 */
public class EasyPredicateParser {

    /**
     * Parses the predicate
     *
     * @param predicate the predicate
     * @return the parsed predicate or the original predicate if easy parser did not kick-in
     */
    public String parse(String predicate) {

        if (predicate.startsWith("$")) {
            // regular json path so skip
            return predicate;
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
                int pos = prev.lastIndexOf(".");
                if (pos == -1) {
                    before = "..*";
                    after = prev;
                } else {
                    before = prev.substring(0, pos);
                    after = prev.substring(pos + 1);
                }
                sb.append("$");
                if (!before.startsWith(".")) {
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
     * @param predicate the predicate
     * @return the splitted parts
     */
    private static String[] tokens(String predicate) {
        List<String> list = new ArrayList<>();

        StringBuilder part = new StringBuilder();
        for (int i = 0; i < predicate.length(); i++) {

            // is there a new operator
            String s = predicate.substring(i);
            String op = EasyPredicateOperators.getOperatorAtStart(s);
            if (op != null) {
                if (part.length() > 0) {
                    list.add(part.toString());
                    part.setLength(0);
                }
                list.add(op.trim());
                // move i ahead
                i = i + op.length() + 1;
            } else {
                char ch = predicate.charAt(i);
                part.append(ch);
            }
        }

        // ant leftovers
        if (part.length() > 0) {
            list.add(part.toString());
        }

        return list.toArray(new String[list.size()]);
    }

}
