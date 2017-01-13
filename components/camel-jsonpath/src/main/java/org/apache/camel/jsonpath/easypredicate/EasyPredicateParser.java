/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.jsonpath.easypredicate;

import static org.apache.camel.jsonpath.easypredicate.EasyPredicateOperators.hasOperator;
import static org.apache.camel.jsonpath.easypredicate.EasyPredicateOperators.isOperator;
import static org.apache.camel.jsonpath.easypredicate.EasyPredicateOperators.tokens;

public class EasyPredicateParser {

    public String parse(String exp) {
        if (exp.startsWith("$")) {
            // regular json path so skip
            return exp;
        }

        // must have an operator
        if (!hasOperator(exp)) {
            return exp;
        }

        StringBuilder sb = new StringBuilder();

        // grab before operator
        String[] parts = tokens(exp);

        // only support one operator currently
        if (parts.length == 3) {
            String prev = parts[0];
            String op = parts[1];
            String next = parts[2];
            if (isOperator(op)) {
                int pos = prev.lastIndexOf(".");
                String before = prev.substring(0, pos);
                String after = prev.substring(pos + 1);
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
        return exp;
    }

}
