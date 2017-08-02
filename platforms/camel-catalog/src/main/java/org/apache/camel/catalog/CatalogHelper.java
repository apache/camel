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
package org.apache.camel.catalog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.List;

public final class CatalogHelper {

    private CatalogHelper() {
    }

    /**
     * Loads the entire stream into memory as a String and returns it.
     * <p/>
     * <b>Notice:</b> This implementation appends a <tt>\n</tt> as line
     * terminator at the of the text.
     * <p/>
     * Warning, don't use for crazy big streams :)
     */
    public static void loadLines(InputStream in, List<String> lines) throws IOException {
        try (final InputStreamReader isr = new InputStreamReader(in);
            final BufferedReader reader = new LineNumberReader(isr)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
    }

    /**
     * Loads the entire stream into memory as a String and returns it.
     * <p/>
     * <b>Notice:</b> This implementation appends a <tt>\n</tt> as line
     * terminator at the of the text.
     * <p/>
     * Warning, don't use for crazy big streams :)
     */
    public static String loadText(InputStream in) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (final InputStreamReader isr = new InputStreamReader(in);
            final BufferedReader reader = new LineNumberReader(isr)) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
                builder.append("\n");
            }
            return builder.toString();
        }
    }

    /**
     * Matches the name with the pattern.
     *
     * @param name  the name
     * @param pattern the pattern
     * @return <tt>true</tt> if matched, or <tt>false</tt> if not
     */
    public static boolean matchWildcard(String name, String pattern) {
        // we have wildcard support in that hence you can match with: file* to match any file endpoints
        if (pattern.endsWith("*") && name.startsWith(pattern.substring(0, pattern.length() - 1))) {
            return true;
        }
        return false;
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
     * Returns the string before the given token
     *
     * @param text  the text
     * @param before the token
     * @return the text before the token, or <tt>null</tt> if text does not contain the token
     */
    public static String before(String text, String before) {
        if (!text.contains(before)) {
            return null;
        }
        return text.substring(0, text.indexOf(before));
    }

    /**
     * Returns the string between the given tokens
     *
     * @param text  the text
     * @param after the before token
     * @param before the after token
     * @return the text between the tokens, or <tt>null</tt> if text does not contain the tokens
     */
    public static String between(String text, String after, String before) {
        text = after(text, after);
        if (text == null) {
            return null;
        }
        return before(text, before);
    }

    /**
     * Tests whether the value is <tt>null</tt> or an empty string.
     *
     * @param value  the value, if its a String it will be tested for text length as well
     * @return true if empty
     */
    public static boolean isEmpty(Object value) {
        return !isNotEmpty(value);
    }

    /**
     * Tests whether the value is <b>not</b> <tt>null</tt> or an empty string.
     *
     * @param value  the value, if its a String it will be tested for text length as well
     * @return true if <b>not</b> empty
     */
    public static boolean isNotEmpty(Object value) {
        if (value == null) {
            return false;
        } else if (value instanceof String) {
            String text = (String) value;
            return text.trim().length() > 0;
        } else {
            return true;
        }
    }

    /**
     * Removes all leading and ending quotes (single and double) from the string
     *
     * @param s  the string
     * @return the string without leading and ending quotes (single and double)
     */
    public static String removeLeadingAndEndingQuotes(String s) {
        if (isEmpty(s)) {
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

}
