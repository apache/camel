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
package org.apache.camel.component.exec.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Utility class for parsing, used by the Camel Exec component.<br>
 * Note: the class should be dropped, when the commons-exec library
 * implements similar functionality.
 */
public final class ExecParseUtils {

    public static final String WHITESPACE = " ";

    public static final String QUOTE_CHAR = "\"";

    private ExecParseUtils() {
    }

    /**
     * Splits the input line string by {@link #WHITESPACE}. Supports quoting the
     * white-spaces with a {@link #QUOTE_CHAR}. A quote itself can also be
     * enclosed within #{@link #QUOTE_CHAR}#{@link #QUOTE_CHAR}. More than two
     * double-quotes in a sequence is not allowed. Nested quotes are not
     * allowed.<br>
     * E.g. The string
     * <code>"arg 1"  arg2<code> will return the tokens <code>arg 1</code>,
     * <code>arg2</code><br>
     * The string
     * <code>""arg 1""  "arg2" arg 3<code> will return the tokens <code>"arg 1"</code>
     * , <code>arg2</code>,<code>arg</code> and <code>3</code> <br>
     * 
     * @param input the input to split.
     * @return a not-null list of tokens
     */
    public static List<String> splitToWhiteSpaceSeparatedTokens(String input) {
        if (input == null) {
            return new ArrayList<>();
        }
        StringTokenizer tokenizer = new StringTokenizer(input.trim(), QUOTE_CHAR + WHITESPACE, true);
        List<String> tokens = new ArrayList<>();

        StringBuilder quotedText = new StringBuilder();

        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            if (QUOTE_CHAR.equals(token)) {
                // if we have a quote, add the next tokens to the quoted text
                // until the quoting has finished
                quotedText.append(QUOTE_CHAR);
                String buffer = quotedText.toString();
                if (isSingleQuoted(buffer) || isDoubleQuoted(buffer)) {
                    tokens.add(buffer.substring(1, buffer.length() - 1));
                    quotedText = new StringBuilder();
                }
            } else if (WHITESPACE.equals(token)) {
                // a white space, if in quote, add the white space, otherwise
                // skip it
                if (quotedText.length() > 0) {
                    quotedText.append(WHITESPACE);
                }
            } else {
                if (quotedText.length() > 0) {
                    quotedText.append(token);
                } else {
                    tokens.add(token);
                }
            }
        }
        if (quotedText.length() > 0) {
            throw new IllegalArgumentException("Invalid quoting found in args " + quotedText);
        }
        return tokens;
    }

    /**
     * Tests if the input is enclosed within {@link #QUOTE_CHAR} characters
     * 
     * @param input a not null String
     * @return true if the regular expression is matched
     */
    protected static boolean isSingleQuoted(String input) {
        if (input == null || input.trim().length() == 0) {
            return false;
        }
        return input.matches("(^" + QUOTE_CHAR + "{1}([^" + QUOTE_CHAR + "]+)" + QUOTE_CHAR + "{1})");
    }

    /**
     * Tests if the input is enclosed within a double-{@link #QUOTE_CHAR} string
     * 
     * @param input a not null String
     * @return true if the regular expression is matched
     */
    protected static boolean isDoubleQuoted(String input) {
        if (input == null || input.trim().length() == 0) {
            return false;
        }
        return input.matches("(^" + QUOTE_CHAR + "{2}([^" + QUOTE_CHAR + "]+)" + QUOTE_CHAR + "{2})");
    }
}
