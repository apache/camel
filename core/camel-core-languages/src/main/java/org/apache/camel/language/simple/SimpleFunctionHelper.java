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

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import org.apache.camel.util.StringHelper;

public final class SimpleFunctionHelper {

    private SimpleFunctionHelper() {
    }

    public static String ifStartsWithReturnRemainder(String prefix, String text) {
        if (text.startsWith(prefix)) {
            String remainder = text.substring(prefix.length());
            if (!remainder.isEmpty()) {
                return remainder;
            }
        }
        return null;
    }

    public static String parseInHeader(String function) {
        String remainder;
        remainder = ifStartsWithReturnRemainder("in.headers", function);
        if (remainder == null) {
            remainder = ifStartsWithReturnRemainder("in.header", function);
        }
        if (remainder == null) {
            remainder = ifStartsWithReturnRemainder("headers", function);
        }
        if (remainder == null) {
            remainder = ifStartsWithReturnRemainder("header", function);
        }
        return keyRemainder(remainder);
    }

    public static String parseVariable(String function) {
        String remainder = ifStartsWithReturnRemainder("variables", function);
        if (remainder == null) {
            remainder = ifStartsWithReturnRemainder("variable", function);
        }
        return keyRemainder(remainder);
    }

    /**
     * The remainder after a name such as header must start the key, so ${headerfoo} is not the header foo (and a custom
     * function named such as headerCount can be called).
     */
    private static String keyRemainder(String remainder) {
        if (remainder != null && !remainder.isEmpty()) {
            char c = remainder.charAt(0);
            if (c != '.' && c != ':' && c != '?' && c != '[') {
                return null;
            }
        }
        return remainder;
    }

    /**
     * The index of the parenthesis that closes the argument list, where the text is what comes after the opening
     * parenthesis. Parentheses inside nested functions and quotes are skipped, so the type in
     * ${convertTo(${header.foo.trim()},Integer)} is found. Returns -1 if there is no closing parenthesis.
     */
    public static int indexOfClosingParenthesis(String text) {
        int depth = 0;
        boolean single = false;
        boolean dubble = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'' && !dubble) {
                single = !single;
            } else if (c == '"' && !single) {
                dubble = !dubble;
            } else if (!single && !dubble) {
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    if (depth == 0) {
                        return i;
                    }
                    depth--;
                }
            }
        }
        return -1;
    }

    /**
     * Splits the arguments of a function at the commas outside quotes and outside nested functions, so
     * {@code ${replace(b,${header.s.substring(1,3)})}} has two arguments. The white space around an unquoted argument
     * is not part of it, so {@code ${sort(${body}, true)}} and {@code ${replace('o', '0')}} work, while a quoted
     * argument such as {@code ' '} is kept as written (without its quotes). An argument of only white space is kept as
     * it is, so {@code ${replace( ,-)}} replaces a space. An argument left out (nothing between the commas) is kept in
     * its place as {@code null}, so {@code ${replace(b,,${header.s})}} has three arguments, the second {@code null}; a
     * quoted empty argument ({@code ''}) is the empty text.
     */
    public static String[] splitArguments(String values) {
        String[] tokens = splitArgumentsAsWritten(values);
        for (int i = 0; i < tokens.length; i++) {
            tokens[i] = tokens[i].isEmpty() ? null : trimArgument(tokens[i]);
        }
        return tokens;
    }

    /**
     * Splits the arguments of a function as written (with their quotes and the white space around them) at the commas
     * outside quotes and outside nested functions: a comma inside a nested {@code ${...}}, also inside its parentheses,
     * such as in {@code ${header.s.substring(1,3)}}, does not split. A parenthesis or brace outside a nested function
     * is text, as before, so {@code ${substringBetween(${body},(,))}} has the arguments {@code ${body}}, {@code (} and
     * {@code )}. Every argument is kept, also an empty one, so {@code a,,b} gives three arguments and {@code a,} two.
     */
    public static String[] splitArgumentsAsWritten(String values) {
        List<String> answer = new ArrayList<>();
        boolean single = false;
        boolean dubble = false;
        int depth = 0;
        int start = 0;
        for (int i = 0; i < values.length(); i++) {
            char c = values.charAt(i);
            if (c == '\'' && !dubble) {
                single = !single;
            } else if (c == '"' && !single) {
                dubble = !dubble;
            } else if (!single && !dubble) {
                if (c == '$' && i + 1 < values.length() && values.charAt(i + 1) == '{') {
                    // a nested function: its commas, parentheses and braces belong to it
                    depth++;
                    i++;
                } else if (depth > 0 && (c == '(' || c == '{')) {
                    depth++;
                } else if (depth > 0 && (c == ')' || c == '}')) {
                    depth--;
                } else if (c == ',' && depth == 0) {
                    answer.add(values.substring(start, i));
                    start = i + 1;
                }
            }
        }
        answer.add(values.substring(start));
        return answer.toArray(new String[0]);
    }

    /**
     * Splits the arguments of a function as {@link #splitArgumentsAsWritten(String)} does (an empty argument is kept in
     * its place, a comma inside a nested function does not split), with the white space around each argument removed
     * and its quotes kept, for a function that removes the quotes of some of its arguments only. An argument left out
     * (nothing, or only white space, between the commas) is {@code null}.
     */
    public static String[] splitArgumentsKeepQuotes(String values) {
        String[] tokens = splitArgumentsAsWritten(values);
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i].trim();
            tokens[i] = token.isEmpty() ? null : token;
        }
        return tokens;
    }

    /**
     * The expression argument of a function as {@link #splitArguments(String)} gives it, or {@code ${body}} when it is
     * left out, such as in {@code ${replace(a,b,)}}.
     */
    public static String expressionOrBody(String token) {
        return token == null ? "${body}" : token;
    }

    /**
     * The arguments from the given index on, joined again with a comma (for a function whose last argument is an
     * expression that may contain commas), or {@code null} when one of them is left out, such as with a trailing comma.
     */
    public static String joinArguments(String[] tokens, int from) {
        StringJoiner joiner = new StringJoiner(",");
        for (int i = from; i < tokens.length; i++) {
            if (tokens[i] == null) {
                return null;
            }
            joiner.add(tokens[i]);
        }
        return joiner.toString();
    }

    /**
     * An argument as {@link #splitArguments(String)} gives it, from the argument as written (split with
     * {@link #splitArgumentsAsWritten(String)}): without the white space and the quotes around it, unless it is only
     * white space.
     */
    public static String trimArgument(String token) {
        String trimmed = token.trim();
        if (!trimmed.isEmpty()) {
            // a whitespace only argument such as a new line is kept as-is
            token = trimmed;
        }
        return StringHelper.removeLeadingAndEndingQuotes(token);
    }
}
