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

package org.apache.camel.web.util;

import org.apache.camel.Predicate;

/**
 * render a predicate
 */
public class PredicateRenderer {

    public static void render(StringBuilder buffer, Predicate predicate) {
        String pre = predicate.toString();
        if (pre.startsWith("not (")) {
            renderNot(buffer, pre);
        } else if (pre.contains(") and (")) {
            renderAnd(buffer, pre);
        } else if (pre.contains(") or (")) {
            renderOr(buffer, pre);
        } else if (pre.startsWith("in (")) {
            renderIn(buffer, pre);
        } else if (pre.contains(".matches(")) {
            renderMatches(buffer, pre);
        } else {
            render(buffer, pre);
        }
    }

    public static void render(StringBuilder buffer, String predicate) {
        String left = predicate.substring(0, predicate.indexOf(" "));
        String operation = predicate.substring(predicate.indexOf(" ") + 1, predicate.lastIndexOf(" "));
        String right = predicate.substring(predicate.lastIndexOf(" ") + 1);

        renderLeft(buffer, left);
        renderOperation(buffer, operation);
        renderRight(buffer, right);

    }

    private static void renderAnd(StringBuilder buffer, String predicate) {
        // (predicate1 and predicate2)
        buffer.append("and(");
        String predicate1 = predicate.substring(1, predicate.indexOf(") and ("));
        String predicate2 = predicate.substring(predicate.indexOf(") and (") + 7, predicate.length() - 1);
        render(buffer, predicate1);
        buffer.append(", ");
        render(buffer, predicate2);
        buffer.append(")");
    }

    private static void renderIn(StringBuilder buffer, String predicate) {
        String predicates[] = predicate.split("in \\(\\[|,\\s+|\\]\\)");
        if (predicate.contains("convertToEvaluatedType")) {
            // valueIn
            String left = predicate.substring(predicate.indexOf("in ([") + 5, predicate.indexOf(" == "));
            renderLeft(buffer, left);
            buffer.append(".in(");
            for (String pre : predicates) {
                if (pre.equals("")) {
                    continue;
                }
                String value = pre.substring(pre.indexOf(" == ") + 4, pre.indexOf(".convertToEvaluatedType("));
                buffer.append("\"").append(value).append("\"");
                if (pre != predicates[predicates.length - 1]) {
                    buffer.append(", ");
                }
            }
            buffer.append(")");
        } else {
            // predicateIn
            buffer.append("in(");
            for (String pre : predicates) {
                if (pre.equals("")) {
                    continue;
                }
                render(buffer, pre);
                if (pre != predicates[predicates.length - 1]) {
                    buffer.append(", ");
                }
            }
            buffer.append(")");
        }
    }

    private static void renderLeft(StringBuilder buffer, String left) {
        if (left.contains("(")) {
            // header(foo) -> header("foo")
            buffer.append(left.replaceAll("\\(", "(\"").replaceAll("\\)", "\")"));
        } else {
            // body -> body()
            buffer.append(left).append("()");
        }
    }

    private static void renderMatches(StringBuilder buffer, String pre) {
        // header(foo).matches('pattern')
        pre = pre.replaceFirst("\\(", "(\"").replaceFirst("\\)", "\")");
        pre = pre.replaceFirst(".matches\\('", ".regex(\"").replaceFirst("'\\)", "\")");
        buffer.append(pre);
    }

    private static void renderNot(StringBuilder buffer, String predicate) {
        // not(predicate)
        buffer.append("not(");
        String notPredicate = predicate.substring(predicate.indexOf("(") + 1, predicate.length() - 1);
        render(buffer, notPredicate);
        buffer.append(")");
    }

    private static void renderOperation(StringBuilder buffer, String operation) {
        // process the operations
        if (operation.equals("==")) {
            buffer.append(".isEqualTo");
        } else if (operation.equals("!=")) {
            buffer.append(".isNotEqualTo");
        } else if (operation.equals("<")) {
            buffer.append(".isLessThan");
        } else if (operation.equals("<=")) {
            buffer.append(".isLessThanOrEqualTo");
        } else if (operation.equals(">")) {
            buffer.append(".isGreaterThan");
        } else if (operation.equals(">=")) {
            buffer.append(".isGreaterThanOrEqualTo");
        } else if (operation.equals("contains")) {
            buffer.append(".contains");
        } else if (operation.equals("is")) {
            buffer.append(".isNull()");
        } else if (operation.equals("is not")) {
            buffer.append(".isNotNull()");
        } else if (operation.equals("instanceof")) {
            buffer.append(".isInstanceOf");
        } else if (operation.equals("startsWith")) {
            buffer.append(".startsWith");
        } else if (operation.equals("endsWith")) {
            buffer.append(".endsWith");
        } else if (operation.equals("matches")) {
            buffer.append(".regex");
        }
    }

    private static void renderOr(StringBuilder buffer, String predicate) {
     // (predicate1 or predicate2)
        buffer.append("or(");
        String predicate1 = predicate.substring(1, predicate.indexOf(") or ("));
        String predicate2 = predicate.substring(predicate.indexOf(") or (") + 6, predicate.length() - 1);
        render(buffer, predicate1);
        buffer.append(", ");
        render(buffer, predicate2);
        buffer.append(")");
    }

    private static void renderRight(StringBuilder buffer, String right) {
        if (right.matches("-?\\d+")) {
            // number -> number
            buffer.append("(").append(right).append(")");
        } else if (right.equals("") || right.equals("null")) {
            // for isNull() and isNotNull()
            return;
        } else {
            // string -> "string"
            buffer.append("(\"").append(right).append("\")");
        }
    }

}
