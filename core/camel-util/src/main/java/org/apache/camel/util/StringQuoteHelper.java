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

/**
 * Utility class for parsing quoted string which is intended for parameters, separated by comma.
 */
public final class StringQuoteHelper {

    private StringQuoteHelper() {
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
     * Splits the input safely honoring if values is enclosed in quotes.
     * <p/>
     * Though this method does not support double quoting values. A quoted value must start with the same start and
     * ending quote, which is either a single quote or double quote value.
     * <p/>
     * Will <i>trim</i> each split value by default.
     *
     * @param  input     the input
     * @param  separator the separator char to split the input, for example a comma.
     * @return           the input split, or <tt>null</tt> if the input is null.
     */
    public static String[] splitSafeQuote(String input, char separator) {
        return splitSafeQuote(input, separator, true, false);
    }

    /**
     * Splits the input safely honoring if values is enclosed in quotes.
     * <p/>
     * Though this method does not support double quoting values. A quoted value must start with the same start and
     * ending quote, which is either a single quote or double quote value.
     *
     * @param  input     the input
     * @param  separator the separator char to split the input, for example a comma.
     * @param  trim      whether to trim each split value
     * @return           the input split, or <tt>null</tt> if the input is null.
     */
    public static String[] splitSafeQuote(String input, char separator, boolean trim) {
        return splitSafeQuote(input, separator, trim, false);
    }

    /**
     * Splits the input safely honoring if values is enclosed in quotes.
     * <p/>
     * Though this method does not support double quoting values. A quoted value must start with the same start and
     * ending quote, which is either a single quote or double quote value.
     *
     * @param  input      the input
     * @param  separator  the separator char to split the input, for example a comma.
     * @param  trim       whether to trim each split value
     * @param  keepQuotes whether to keep quotes
     * @return            the input split, or <tt>null</tt> if the input is null.
     */
    public static String[] splitSafeQuote(String input, char separator, boolean trim, boolean keepQuotes) {
        if (input == null) {
            return null;
        }

        if (input.indexOf(separator) == -1) {
            if (input.length() > 1) {
                char ch = input.charAt(0);
                char ch2 = input.charAt(input.length() - 1);
                boolean singleQuoted = ch == '\'' && ch2 == '\'';
                boolean doubleQuoted = ch == '"' && ch2 == '"';
                if (!keepQuotes && (singleQuoted || doubleQuoted)) {
                    input = input.substring(1, input.length() - 1);
                }
            }
            // no separator in data, so return single string with input as is
            return new String[] { trim ? input.trim() : input };
        }

        List<String> answer = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        boolean skipLeadingWhitespace = true;

        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            char prev = i > 0 ? input.charAt(i - 1) : 0;
            boolean isQuoting = singleQuoted || doubleQuoted;
            boolean last = i == input.length() - 1;

            if (!doubleQuoted && ch == '\'') {
                if (singleQuoted && prev == ch && sb.length() == 0) {
                    // its an empty quote so add empty text
                    if (keepQuotes) {
                        answer.add("''");
                    } else {
                        answer.add("");
                    }
                }
                // special logic needed if this quote is the end
                if (last) {
                    if (singleQuoted && sb.length() > 0) {
                        String text = sb.toString();
                        // do not trim a quoted string
                        if (keepQuotes) {
                            answer.add(text + "'"); // append ending quote
                        } else {
                            answer.add(text);
                        }
                        sb.setLength(0);
                    }
                    break; // break out as we are finished
                }
                singleQuoted = !singleQuoted;
                if (keepQuotes) {
                    sb.append(ch);
                }
                continue;
            } else if (!singleQuoted && ch == '"') {
                if (doubleQuoted && prev == ch && sb.length() == 0) {
                    // its an empty quote so add empty text
                    if (keepQuotes) {
                        answer.add("\""); // append ending quote
                    } else {
                        answer.add("");
                    }
                }
                // special logic needed if this quote is the end
                if (last) {
                    if (doubleQuoted && sb.length() > 0) {
                        String text = sb.toString();
                        // do not trim a quoted string
                        if (keepQuotes) {
                            answer.add(text + "\"");
                        } else {
                            answer.add(text);
                        }
                        sb.setLength(0);
                    }
                    break; // break out as we are finished
                }
                doubleQuoted = !doubleQuoted;
                if (keepQuotes) {
                    sb.append(ch);
                }
                continue;
            } else if (!isQuoting && separator != ' ' && ch == ' ') {
                if (skipLeadingWhitespace) {
                    continue;
                }
            } else if (!isQuoting && ch == separator) {
                // add as answer if we are not in a quote
                if (sb.length() > 0) {
                    String text = sb.toString();
                    if (trim) {
                        text = text.trim();
                    }
                    answer.add(text);
                    sb.setLength(0);
                }
                // we should avoid adding the separator
                continue;
            }

            // append char
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

        return answer.toArray(new String[0]);
    }

}
