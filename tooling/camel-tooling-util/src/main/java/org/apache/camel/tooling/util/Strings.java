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
package org.apache.camel.tooling.util;

import java.util.Collection;
import java.util.Locale;

/**
 * Some String helper methods
 */
public final class Strings {

    private Strings() {
        //Helper class
    }

    public static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * Returns true if the given text is null or empty string or has <tt>null</tt> as the value
     */
    public static boolean isNullOrEmpty(String text) {
        return text == null || text.length() == 0 || "null".equals(text);
    }

    public static String safeNull(String text) {
        if (isNullOrEmpty(text)) {
            return "";
        } else {
            return text;
        }
    }

    /**
     * Returns the value or the defaultValue if it is null
     */
    public static String getOrElse(String text, String defaultValue) {
        return (text != null) ? text : defaultValue;
    }

    /**
     * Returns the string after the given token
     *
     * @param  text  the text
     * @param  after the token
     * @return       the text after the token, or <tt>null</tt> if text does not contain the token
     */
    public static String after(String text, String after) {
        int index = text.indexOf(after);
        if (index < 0) {
            return null;
        }
        return text.substring(index + after.length());
    }

    /**
     * Returns the canonical class name by removing any generic type information.
     */
    public static String canonicalClassName(String className) {
        // remove generics
        int pos = className.indexOf('<');
        if (pos != -1) {
            return className.substring(0, pos);
        } else {
            return className;
        }
    }

    /**
     * Returns the text wrapped double quotes
     */
    public static String doubleQuote(String text) {
        return quote(text, "\"");
    }

    /**
     * Returns the text wrapped single quotes
     */
    public static String singleQuote(String text) {
        return quote(text, "'");
    }

    /**
     * Wraps the text in the given quote text
     *
     * @param  text  the text to wrap in quotes
     * @param  quote the quote text added to the prefix and postfix of the text
     *
     * @return       the text wrapped in the given quotes
     */
    public static String quote(String text, String quote) {
        return quote + text + quote;
    }

    /**
     * Clips the text between the start and end markers
     */
    public static String between(String text, String after, String before) {
        text = after(text, after);
        if (text == null) {
            return null;
        }
        return before(text, before);
    }

    /**
     * Capitalizes the name as a title
     *
     * @param  name the name
     * @return      as a title
     */
    public static String asTitle(String name) {
        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            boolean upper = Character.isUpperCase(c);
            boolean first = sb.length() == 0;
            if (first) {
                sb.append(Character.toUpperCase(c));
            } else if (upper) {
                char prev = sb.charAt(sb.length() - 1);
                if (!Character.isUpperCase(prev)) {
                    // append space if previous is not upper
                    sb.append(' ');
                }
                sb.append(c);
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString().trim();
    }

    public static String before(String text, String before) {
        int index = text.indexOf(before);
        if (index < 0) {
            return null;
        }
        return text.substring(0, index);
    }

    public static String indentCollection(String indent, Collection<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String text : list) {
            sb.append(indent).append(text);
        }
        return sb.toString();
    }

    /**
     * Converts the value to use title style instead of dash cased
     */
    public static String camelDashToTitle(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        boolean dash = false;

        for (char c : value.toCharArray()) {
            if ('-' == c) {
                dash = true;
                continue;
            }

            if (dash) {
                sb.append(' ');
                sb.append(Character.toUpperCase(c));
            } else {
                // upper case first
                if (sb.length() == 0) {
                    sb.append(Character.toUpperCase(c));
                } else {
                    sb.append(c);
                }
            }
            dash = false;
        }
        return sb.toString();
    }

    /**
     * Converts the string from camel case into dash format (helloGreatWorld -> hello-great-world)
     *
     * @param  text the string
     * @return      the string camel cased
     */
    public static String camelCaseToDash(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder answer = new StringBuilder();

        Character prev = null;
        Character next = null;
        char[] arr = text.toCharArray();
        for (int i = 0; i < arr.length; i++) {
            char ch = arr[i];
            if (i < arr.length - 1) {
                next = arr[i + 1];
            } else {
                next = null;
            }
            if (ch == '-' || ch == '_') {
                answer.append("-");
            } else if (Character.isUpperCase(ch) && prev != null && !Character.isUpperCase(prev)) {
                if (prev != '-' && prev != '_') {
                    answer.append("-");
                }
                answer.append(ch);
            } else if (Character.isUpperCase(ch) && prev != null && next != null && Character.isLowerCase(next)) {
                if (prev != '-' && prev != '_') {
                    answer.append("-");
                }
                answer.append(ch);
            } else {
                answer.append(ch);
            }
            prev = ch;
        }

        return answer.toString().toLowerCase(Locale.ENGLISH);
    }

    /**
     * A simpler version of StringHelper#capitlize for usage in the tooling code
     *
     * @param  text the string
     * @return      the string capitalized (upper case first character) or null if the input is null
     */
    public static String capitalize(final String text) {
        if (text == null) {
            return null;
        }

        int length = text.length();
        final char[] chars = new char[length];
        text.getChars(0, length, chars, 0);

        // We are OK with the limitations of Character.toUpperCase. The symbols and ideographs
        // for which it does not return the capitalized value should not be used here (this is
        // mostly used to capitalize setters/getters)
        chars[0] = Character.toUpperCase(chars[0]);
        return new String(chars);
    }

}
