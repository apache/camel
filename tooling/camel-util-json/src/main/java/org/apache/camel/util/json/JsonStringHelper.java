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
package org.apache.camel.util.json;

/**
 * Helpers that work on JSON held as a String: check it is valid JSON, or compact it.
 * <p>
 * The JSON is checked strictly against RFC 8259 (unlike {@link Jsoner}, a trailing comma is not accepted), without
 * recursion, so deeply nested input cannot overflow the stack. Strings and numbers are kept exactly as written.
 */
public final class JsonStringHelper {

    private static final int VALUE = 0;
    private static final int FIRST_VALUE_OR_END = 1;
    private static final int FIRST_KEY_OR_END = 2;
    private static final int KEY = 3;
    private static final int COLON = 4;
    private static final int AFTER_VALUE = 5;

    private JsonStringHelper() {
    }

    /**
     * Whether the value is valid JSON: an object, an array, or a single string, number, {@code true}, {@code false} or
     * {@code null}, with nothing but whitespace around it.
     *
     * @param  json the JSON
     * @return      <code>true</code> if the value is valid JSON, <code>false</code> otherwise (also for
     *              <code>null</code> or a blank value)
     */
    public static boolean isValid(String json) {
        return json != null && scan(json, null);
    }

    /**
     * Removes the whitespace between the tokens, so the JSON is on a single line (the inverse of pretty printing). The
     * whitespace inside strings is kept.
     *
     * @param  json the JSON
     * @return      the compact JSON, or <code>null</code> if the value is <code>null</code> or not valid JSON
     */
    public static String compact(String json) {
        if (json == null) {
            return null;
        }
        StringBuilder out = new StringBuilder(json.length());
        return scan(json, out) ? out.toString() : null;
    }

    private static boolean scan(String json, StringBuilder out) {
        // the open objects and arrays, innermost last
        StringBuilder open = new StringBuilder();
        int state = VALUE;
        int i = 0;
        int n = json.length();
        while (true) {
            while (i < n && isWhitespace(json.charAt(i))) {
                i++;
            }
            if (i == n) {
                return state == AFTER_VALUE && open.isEmpty();
            }
            char ch = json.charAt(i);
            int end;
            switch (state) {
                case VALUE, FIRST_VALUE_OR_END:
                    if (ch == ']' && state == FIRST_VALUE_OR_END) {
                        open.setLength(open.length() - 1);
                        end = i + 1;
                        state = AFTER_VALUE;
                    } else if (ch == '{' || ch == '[') {
                        open.append(ch);
                        end = i + 1;
                        state = ch == '{' ? FIRST_KEY_OR_END : FIRST_VALUE_OR_END;
                    } else {
                        end = scanValue(json, i);
                        state = AFTER_VALUE;
                    }
                    break;
                case FIRST_KEY_OR_END, KEY:
                    if (ch == '}' && state == FIRST_KEY_OR_END) {
                        open.setLength(open.length() - 1);
                        end = i + 1;
                        state = AFTER_VALUE;
                    } else {
                        end = ch == '"' ? scanString(json, i) : -1;
                        state = COLON;
                    }
                    break;
                case COLON:
                    end = ch == ':' ? i + 1 : -1;
                    state = VALUE;
                    break;
                default:
                    // AFTER_VALUE: only inside an object or array can more follow
                    char container = open.isEmpty() ? 0 : open.charAt(open.length() - 1);
                    if (ch == ',' && container != 0) {
                        state = container == '{' ? KEY : VALUE;
                    } else if (ch == '}' && container == '{' || ch == ']' && container == '[') {
                        open.setLength(open.length() - 1);
                    } else {
                        return false;
                    }
                    end = i + 1;
                    break;
            }
            if (end == -1) {
                return false;
            }
            if (out != null) {
                out.append(json, i, end);
            }
            i = end;
        }
    }

    /** A string, number or literal at the position, returning the position after it, or -1 if there is none. */
    private static int scanValue(String json, int start) {
        char ch = json.charAt(start);
        if (ch == '"') {
            return scanString(json, start);
        } else if (ch == '-' || isDigit(ch)) {
            return scanNumber(json, start);
        }
        for (String literal : new String[] { "true", "false", "null" }) {
            if (json.startsWith(literal, start)) {
                return start + literal.length();
            }
        }
        return -1;
    }

    private static int scanString(String json, int start) {
        int i = start + 1;
        while (i < json.length()) {
            char ch = json.charAt(i);
            if (ch == '"') {
                return i + 1;
            } else if (ch < 0x20) {
                return -1;
            } else if (ch == '\\') {
                if (i + 1 == json.length()) {
                    return -1;
                }
                char esc = json.charAt(i + 1);
                if (esc == 'u') {
                    if (i + 6 > json.length()) {
                        return -1;
                    }
                    for (int j = i + 2; j < i + 6; j++) {
                        if (!isHex(json.charAt(j))) {
                            return -1;
                        }
                    }
                    i += 6;
                } else if ("\"\\/bfnrt".indexOf(esc) != -1) {
                    i += 2;
                } else {
                    return -1;
                }
            } else {
                i++;
            }
        }
        return -1;
    }

    private static int scanNumber(String json, int start) {
        int i = start;
        if (json.charAt(i) == '-') {
            i++;
        }
        // an integer part without leading zeros
        if (i < json.length() && json.charAt(i) == '0') {
            i++;
        } else {
            int digits = skipDigits(json, i);
            if (digits == i) {
                return -1;
            }
            i = digits;
        }
        if (i < json.length() && json.charAt(i) == '.') {
            int digits = skipDigits(json, i + 1);
            if (digits == i + 1) {
                return -1;
            }
            i = digits;
        }
        if (i < json.length() && (json.charAt(i) == 'e' || json.charAt(i) == 'E')) {
            i++;
            if (i < json.length() && (json.charAt(i) == '+' || json.charAt(i) == '-')) {
                i++;
            }
            int digits = skipDigits(json, i);
            if (digits == i) {
                return -1;
            }
            i = digits;
        }
        return i;
    }

    private static int skipDigits(String json, int start) {
        int i = start;
        while (i < json.length() && isDigit(json.charAt(i))) {
            i++;
        }
        return i;
    }

    private static boolean isDigit(char ch) {
        return ch >= '0' && ch <= '9';
    }

    private static boolean isHex(char ch) {
        return isDigit(ch) || ch >= 'a' && ch <= 'f' || ch >= 'A' && ch <= 'F';
    }

    private static boolean isWhitespace(char ch) {
        return ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r';
    }
}
