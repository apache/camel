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
package org.apache.camel.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper for Camel OGNL (Object-Graph Navigation Language) expressions.
 */
public final class OgnlHelper {

    private static final Pattern INDEX_PATTERN = Pattern.compile("^(.*)\\[(.*)\\]$");

    private OgnlHelper() {
    }

    /**
     * Tests whether or not the given String is a Camel OGNL expression.
     * <p/>
     * An expression is considered an OGNL expression when it contains either one of the following chars: . or [
     *
     * @param expression  the String
     * @return <tt>true</tt> if a Camel OGNL expression, otherwise <tt>false</tt>. 
     */
    public static boolean isValidOgnlExpression(String expression) {
        if (ObjectHelper.isEmpty(expression)) {
            return false;
        }

        // the brackets should come in a pair
        int bracketBegin = StringHelper.countChar(expression, '[');
        int bracketEnd = StringHelper.countChar(expression, ']');
        if (bracketBegin > 0 && bracketEnd > 0) {
            return bracketBegin == bracketEnd;
        }

        return expression.contains(".");
    }

    public static boolean isInvalidValidOgnlExpression(String expression) {
        if (ObjectHelper.isEmpty(expression)) {
            return false;
        }

        if (!expression.contains(".") && !expression.contains("[") && !expression.contains("]")) {
            return false;
        }

        // the brackets should come in pair
        int bracketBegin = StringHelper.countChar(expression, '[');
        int bracketEnd = StringHelper.countChar(expression, ']');
        if (bracketBegin > 0 || bracketEnd > 0) {
            return bracketBegin != bracketEnd;
        }
        
        // check for double dots
        if (expression.contains("..")) {
            return true;
        }

        return false;
    }

    /**
     * Validates whether the method name is using valid java identifiers in the name
     * Will throw {@link IllegalArgumentException} if the method name is invalid.
     */
    public static void validateMethodName(String method) {
        if (ObjectHelper.isEmpty(method)) {
            return;
        }
        for (int i = 0; i < method.length(); i++) {
            char ch = method.charAt(i);
            if (i == 0 && '.' == ch) {
                // its a dot before a method name
                continue;
            }
            if (ch == '(' || ch == '[' || ch == '.' || ch == '?') {
                // break when method name ends and sub method or arguments begin
                break;
            }
            if (i == 0 && !Character.isJavaIdentifierStart(ch)) {
                throw new IllegalArgumentException("Method name must start with a valid java identifier at position: 0 in method: " + method);
            } else if (!Character.isJavaIdentifierPart(ch)) {
                throw new IllegalArgumentException("Method name must be valid java identifier at position: " + i + " in method: " + method);
            }
        }
    }

    /**
     * Tests whether or not the given Camel OGNL expression is using the null safe operator or not.
     *
     * @param ognlExpression the Camel OGNL expression
     * @return <tt>true</tt> if the null safe operator is used, otherwise <tt>false</tt>.
     */
    public static boolean isNullSafeOperator(String ognlExpression) {
        if (ObjectHelper.isEmpty(ognlExpression)) {
            return false;
        }

        return ognlExpression.startsWith("?");
    }

    /**
     * Removes any leading operators from the Camel OGNL expression.
     * <p/>
     * Will remove any leading of the following chars: ? or .
     *
     * @param ognlExpression  the Camel OGNL expression
     * @return the Camel OGNL expression without any leading operators.
     */
    public static String removeLeadingOperators(String ognlExpression) {
        if (ObjectHelper.isEmpty(ognlExpression)) {
            return ognlExpression;
        }

        if (ognlExpression.startsWith("?")) {
            ognlExpression = ognlExpression.substring(1);
        }
        if (ognlExpression.startsWith(".")) {
            ognlExpression = ognlExpression.substring(1);
        }

        return ognlExpression;
    }

    /**
     * Removes any trailing operators from the Camel OGNL expression.
     *
     * @param ognlExpression  the Camel OGNL expression
     * @return the Camel OGNL expression without any trailing operators.
     */
    public static String removeTrailingOperators(String ognlExpression) {
        if (ObjectHelper.isEmpty(ognlExpression)) {
            return ognlExpression;
        }

        if (ognlExpression.contains("[")) {
            return StringHelper.before(ognlExpression, "[");
        }
        return ognlExpression;
    }

    public static String removeOperators(String ognlExpression) {
        return removeLeadingOperators(removeTrailingOperators(ognlExpression));
    }

    public static KeyValueHolder<String, String> isOgnlIndex(String ognlExpression) {
        Matcher matcher = INDEX_PATTERN.matcher(ognlExpression);
        if (matcher.matches()) {

            // to avoid empty strings as we want key/value to be null in such cases
            String key = matcher.group(1);
            if (ObjectHelper.isEmpty(key)) {
                key = null;
            }

            // to avoid empty strings as we want key/value to be null in such cases
            String value = matcher.group(2);
            if (ObjectHelper.isEmpty(value)) {
                value = null;
            }

            return new KeyValueHolder<>(key, value);
        }

        return null;
    }

    /**
     * Regular expression with repeating groups is a pain to get right
     * and then nobody understands the reg exp afterwards.
     * So we use a bit ugly/low-level Java code to split the OGNL into methods.
     *
     * @param ognl the ognl expression
     * @return a list of methods, will return an empty list, if ognl expression has no methods
     * @throws IllegalArgumentException if the last method has a missing ending parenthesis
     */
    public static List<String> splitOgnl(String ognl) {
        List<String> methods = new ArrayList<>();

        // return an empty list if ognl is empty
        if (ObjectHelper.isEmpty(ognl)) {
            return methods;
        }

        StringBuilder sb = new StringBuilder();

        int j = 0; // j is used as counter per method
        int squareBracketCnt = 0; // special to keep track if and how deep we are inside a square bracket block, eg: [foo]
        int parenthesisBracketCnt = 0; // special to keep track if and how deep we are inside a parenthesis block, eg: bar(${body}, ${header.foo})

        for (int i = 0; i < ognl.length(); i++) {
            char ch = ognl.charAt(i);
            // special for starting a new method
            if (j == 0 || (j == 1 && ognl.charAt(i - 1) == '?')
                    || (ch != '.' && ch != '?' && ch != ']')) {
                sb.append(ch);
                // special if we are doing square bracket
                if (ch == '[' && parenthesisBracketCnt == 0) {
                    squareBracketCnt++;
                } else if (ch == '(') {
                    parenthesisBracketCnt++;
                } else if (ch == ')') {
                    parenthesisBracketCnt--;
                }
                j++; // advance
            } else {
                if (ch == '.' && squareBracketCnt == 0 && parenthesisBracketCnt == 0) {
                    // only treat dot as a method separator if not inside a square bracket block
                    // as dots can be used in key names when accessing maps

                    // a dit denotes end of this method and a new method is to be invoked
                    String s = sb.toString();

                    // reset sb
                    sb.setLength(0);

                    // pass over ? to the new method
                    if (s.endsWith("?")) {
                        sb.append("?");
                        s = s.substring(0, s.length() - 1);
                    }

                    // add the method
                    methods.add(s);

                    // reset j to begin a new method
                    j = 0;
                } else if (ch == ']' && parenthesisBracketCnt == 0) {
                    // append ending ] to method name
                    sb.append(ch);
                    String s = sb.toString();

                    // reset sb
                    sb.setLength(0);

                    // add the method
                    methods.add(s);

                    // reset j to begin a new method
                    j = 0;

                    // no more square bracket
                    squareBracketCnt--;
                }

                // and don't lose the char if its not an ] end marker (as we already added that)
                if (ch != ']' || parenthesisBracketCnt > 0) {
                    sb.append(ch);
                }

                // only advance if already begun on the new method
                if (j > 0) {
                    j++;
                }
            }
        }

        // add remainder in buffer when reached end of data
        if (sb.length() > 0) {
            methods.add(sb.toString());
        }

        String last = methods.isEmpty() ? null : methods.get(methods.size() - 1);
        if (parenthesisBracketCnt > 0 && last != null) {
            // there is an unclosed parenthesis bracket on the last method, so it should end with a parenthesis
            if (last.contains("(") && !last.endsWith(")")) {
                throw new IllegalArgumentException("Method should end with parenthesis, was " + last);
            }
        }

        return methods;
    }

}
