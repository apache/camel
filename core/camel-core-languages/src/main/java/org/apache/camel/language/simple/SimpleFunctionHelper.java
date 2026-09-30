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
}
