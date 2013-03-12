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

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for parsing quoted string which is intended for parameters, separated by comma.
 */
public final class StringQuoteHelper {

    private StringQuoteHelper() {
    }

    /**
     * Splits the input safely honoring if values is enclosed in quotes.
     * <p/>
     * Though this method does not support double quoting values. A quoted value
     * must start with the same start and ending quote, which is either a single
     * quote or double quote value.
     * <p/>
     * Will <i>trim</i> each splitted value by default.
     *
     * @param input    the input
     * @param separator the separator char to split the input, for example a comma.
     * @return the input splitted, or <tt>null</tt> if the input is null.
     */
    public static String[] splitSafeQuote(String input, char separator) {
        return splitSafeQuote(input, separator, true);
    }

    /**
     * Splits the input safely honoring if values is enclosed in quotes.
     * <p/>
     * Though this method does not support double quoting values. A quoted value
     * must start with the same start and ending quote, which is either a single
     * quote or double quote value.
     * \
     * @param input    the input
     * @param separator the separator char to split the input, for example a comma.
     * @param trim      whether to trim each splitted value
     * @return the input splitted, or <tt>null</tt> if the input is null.
     */
    public static String[] splitSafeQuote(String input, char separator, boolean trim) {
        if (input == null) {
            return null;
        }

        List<String> answer = new ArrayList<String>();
        StringBuilder sb = new StringBuilder();

        boolean singleQuoted = false;
        boolean doubleQuoted = false;

        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);

            if (ch == '\'') {
                singleQuoted = !singleQuoted;
                continue;
            } else if (ch == '"') {
                doubleQuoted = !doubleQuoted;
                continue;
            } else if (ch == separator) {
                // add as answer if we are not in a quote
                if (!singleQuoted && !doubleQuoted && sb.length() > 0) {
                    String text = sb.toString();
                    if (trim) {
                        text = text.trim();
                    }
                    answer.add(text);
                    sb.setLength(0);
                    continue;
                }
            }

            sb.append(ch);
        }

        // any leftover
        if (sb.length() > 0) {
            String text = sb.toString();
            if (trim) {
                text = text.trim();
            }
            answer.add(text);
        }

        return answer.toArray(new String[answer.size()]);
    }

}
