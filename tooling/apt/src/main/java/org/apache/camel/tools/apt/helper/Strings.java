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
package org.apache.camel.tools.apt.helper;

/**
 * Some String helper methods
 */
public final class Strings {

    private Strings() {
        //Helper class
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
     * @param text  the text
     * @param after the token
     * @return the text after the token, or <tt>null</tt> if text does not contain the token
     */
    public static String after(String text, String after) {
        if (!text.contains(after)) {
            return null;
        }
        return text.substring(text.indexOf(after) + after.length());
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
     * @param text the text to wrap in quotes
     * @param quote the quote text added to the prefix and postfix of the text
     *
     * @return the text wrapped in the given quotes
     */
    public static String quote(String text, String quote) {
        return quote + text + quote;
    }

    /**
     * Clips the text between the start and end markers
     */
    public static String between(String text, String start, String end) {
        int pos = text.indexOf(start);
        if (pos > 0) {
            text = text.substring(pos + 1);
        }
        int pos2 = text.lastIndexOf(end);
        if (pos2 > 0) {
            text = text.substring(0, pos2);
        }
        return text;
    }

    /**
     * Capitalizes the name as a title
     *
     * @param name  the name
     * @return as a title
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
}
