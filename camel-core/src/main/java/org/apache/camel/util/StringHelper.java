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
package org.apache.camel.util;

import static org.apache.camel.util.StringQuoteHelper.doubleQuote;

/**
 * Helper methods for working with Strings.
 */
public final class StringHelper {

    /**
     * Constructor of utility class should be private.
     */
    private StringHelper() {
    }

    /**
     * Ensures that <code>s</code> is friendly for a URL or file system.
     *
     * @param s String to be sanitized.
     * @return sanitized version of <code>s</code>.
     * @throws NullPointerException if <code>s</code> is <code>null</code>.
     */
    public static String sanitize(String s) {
        return s
            .replace(':', '-')
            .replace('_', '-')
            .replace('.', '-')
            .replace('/', '-')
            .replace('\\', '-');
    }

    /**
     * Counts the number of times the given char is in the string
     *
     * @param s  the string
     * @param ch the char
     * @return number of times char is located in the string
     */
    public static int countChar(String s, char ch) {
        if (ObjectHelper.isEmpty(s)) {
            return 0;
        }

        int matches = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (ch == c) {
                matches++;
            }
        }

        return matches;
    }

    public static String removeQuotes(String s) {
        if (ObjectHelper.isEmpty(s)) {
            return s;
        }

        s = replaceAll(s, "'", "");
        s = replaceAll(s, "\"", "");
        return s;
    }

    public static String removeLeadingAndEndingQuotes(String s) {
        if (ObjectHelper.isEmpty(s)) {
            return s;
        }

        String copy = s.trim();
        if (copy.startsWith("'") && copy.endsWith("'")) {
            return copy.substring(1, copy.length() - 1);
        }
        if (copy.startsWith("\"") && copy.endsWith("\"")) {
            return copy.substring(1, copy.length() - 1);
        }

        // no quotes, so return as-is
        return s;
    }

    public static boolean isQuoted(String s) {
        if (ObjectHelper.isEmpty(s)) {
            return false;
        }

        if (s.startsWith("'") && s.endsWith("'")) {
            return true;
        }
        if (s.startsWith("\"") && s.endsWith("\"")) {
            return true;
        }

        return false;
    }

    /**
     * Encodes the text into safe XML by replacing < > and & with XML tokens
     *
     * @param text  the text
     * @return the encoded text
     */
    public static String xmlEncode(String text) {
        if (text == null) {
            return "";
        }
        // must replace amp first, so we dont replace &lt; to amp later
        text = replaceAll(text, "&", "&amp;");
        text = replaceAll(text, "\"", "&quot;");
        text = replaceAll(text, "<", "&lt;");
        text = replaceAll(text, ">", "&gt;");
        return text;
    }

    /**
     * Determines if the string has at least one letter in upper case
     * @param text the text
     * @return <tt>true</tt> if at least one letter is upper case, <tt>false</tt> otherwise
     */
    public static boolean hasUpperCase(String text) {
        if (text == null) {
            return false;
        }

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isUpperCase(ch)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Determines if the string is a fully qualified class name
     */
    public static boolean isClassName(String text) {
        boolean result = false;
        if (text != null) {
            String[] split = text.split("\\.");
            if (split.length > 0) {
                String lastToken = split[split.length - 1];
                if (lastToken.length() > 0) {
                    result = Character.isUpperCase(lastToken.charAt(0));
                }
            }
        }
        return result;
    }

    /**
     * Does the expression have the language start token?
     *
     * @param expression the expression
     * @param language the name of the language, such as simple
     * @return <tt>true</tt> if the expression contains the start token, <tt>false</tt> otherwise
     */
    public static boolean hasStartToken(String expression, String language) {
        if (expression == null) {
            return false;
        }

        // for the simple language the expression start token could be "${"
        if ("simple".equalsIgnoreCase(language) && expression.contains("${")) {
            return true;
        }

        if (language != null && expression.contains("$" + language + "{")) {
            return true;
        }

        return false;
    }

    /**
     * Replaces all the from tokens in the given input string.
     * <p/>
     * This implementation is not recursive, not does it check for tokens in the replacement string.
     *
     * @param input  the input string
     * @param from   the from string, must <b>not</b> be <tt>null</tt> or empty
     * @param to     the replacement string, must <b>not</b> be empty
     * @return the replaced string, or the input string if no replacement was needed
     * @throws IllegalArgumentException if the input arguments is invalid
     */
    public static String replaceAll(String input, String from, String to) {
        if (ObjectHelper.isEmpty(input)) {
            return input;
        }
        if (from == null) {
            throw new IllegalArgumentException("from cannot be null");
        }
        if (to == null) {
            // to can be empty, so only check for null
            throw new IllegalArgumentException("to cannot be null");
        }

        // fast check if there is any from at all
        if (!input.contains(from)) {
            return input;
        }

        final int len = from.length();
        final int max = input.length();
        StringBuilder sb = new StringBuilder(max);
        for (int i = 0; i < max;) {
            if (i + len <= max) {
                String token = input.substring(i, i + len);
                if (from.equals(token)) {
                    sb.append(to);
                    // fast forward
                    i = i + len;
                    continue;
                }
            }

            // append single char
            sb.append(input.charAt(i));
            // forward to next
            i++;
        }
        return sb.toString();
    }

    /**
     * Creates a json tuple with the given name/value pair.
     *
     * @param name  the name
     * @param value the value
     * @param isMap whether the tuple should be map
     * @return the json
     */
    public static String toJson(String name, String value, boolean isMap) {
        if (isMap) {
            return "{ " + doubleQuote(name) + ": " + doubleQuote(value) + " }";
        } else {
            return doubleQuote(name) + ": " + doubleQuote(value);
        }
    }

}
