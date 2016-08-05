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
package org.apache.camel.karaf.commands.internal;

import java.util.Locale;

/**
 * Util class to manipulate String, especially around escape/unescape.
 *
 * This class is a copy of org.apache.karaf.util.StringEscapeUtils.
 */
public final class KarafStringEscapeUtils {

    /**
     * Constant for the radix of hex numbers.
     */
    private static final int HEX_RADIX = 16;

    /**
     * Constant for the length of a unicode literal.
     */
    private static final int UNICODE_LEN = 4;

    private KarafStringEscapeUtils() {
    }

    /**
     * <p>Unescapes any Java literals found in the <code>String</code> to a
     * <code>Writer</code>.</p> This is a slightly modified version of the
     * StringEscapeUtils.unescapeJava() function in commons-lang that doesn't
     * drop escaped separators (i.e '\,').
     *
     * @param str the <code>String</code> to unescape, may be null
     * @return the processed string
     * @throws IllegalArgumentException if the Writer is <code>null</code>
     */
    public static String unescapeJava(String str) {
        if (str == null) {
            return null;
        }
        int sz = str.length();
        StringBuffer out = new StringBuffer(sz);
        StringBuffer unicode = new StringBuffer(UNICODE_LEN);
        boolean hadSlash = false;
        boolean inUnicode = false;
        for (int i = 0; i < sz; i++) {
            char ch = str.charAt(i);
            if (inUnicode) {
                // if in unicode, then we're reading unicode
                // values in somehow
                unicode.append(ch);
                if (unicode.length() == UNICODE_LEN) {
                    // unicode now contains the four hex digits
                    // which represents our unicode character
                    try {
                        int value = Integer.parseInt(unicode.toString(), HEX_RADIX);
                        out.append((char) value);
                        unicode.setLength(0);
                        inUnicode = false;
                        hadSlash = false;
                    } catch (NumberFormatException nfe) {
                        throw new IllegalArgumentException("Unable to parse unicode value: " + unicode, nfe);
                    }
                }
                continue;
            }

            if (hadSlash) {
                // handle an escaped value
                hadSlash = false;
                switch (ch) {
                case '\\':
                    out.append('\\');
                    break;
                case '\'':
                    out.append('\'');
                    break;
                case '\"':
                    out.append('"');
                    break;
                case 'r':
                    out.append('\r');
                    break;
                case 'f':
                    out.append('\f');
                    break;
                case 't':
                    out.append('\t');
                    break;
                case 'n':
                    out.append('\n');
                    break;
                case 'b':
                    out.append('\b');
                    break;
                case 'u':
                    // uh-oh, we're in unicode country....
                    inUnicode = true;
                    break;
                default:
                    out.append(ch);
                    break;
                }
                continue;
            } else if (ch == '\\') {
                hadSlash = true;
                continue;
            }
            out.append(ch);
        }

        if (hadSlash) {
            // then we're in the weird case of a \ at the end of the
            // string, let's output it anyway.
            out.append('\\');
        }

        return out.toString();
    }

    /**
     * <p>Escapes the characters in a <code>String</code> using Java String rules.</p>
     * <p/>
     * <p>Deals correctly with quotes and control-chars (tab, backslash, cr, ff, etc.) </p>
     * <p/>
     * <p>So a tab becomes the characters <code>'\\'</code> and
     * <code>'t'</code>.</p>
     * <p/>
     * <p>The only difference between Java strings and JavaScript strings
     * is that in JavaScript, a single quote must be escaped.</p>
     * <p/>
     * Example:
     * <pre>
     * input string: He didn't say, "Stop!"
     * output string: He didn't say, \"Stop!\"
     * </pre>
     *
     * @param str String to escape values in, may be null
     * @return String with escaped values, <code>null</code> if null string input
     */
    public static String escapeJava(String str) {
        if (str == null) {
            return null;
        }
        int sz = str.length();
        StringBuffer out = new StringBuffer(sz * 2);
        for (int i = 0; i < sz; i++) {
            char ch = str.charAt(i);
            // handle unicode
            if (ch > 0xfff) {
                out.append("\\u").append(hex(ch));
            } else if (ch > 0xff) {
                out.append("\\u0").append(hex(ch));
            } else if (ch > 0x7f) {
                out.append("\\u00").append(hex(ch));
            } else if (ch < 32) {
                switch (ch) {
                case '\b':
                    out.append('\\');
                    out.append('b');
                    break;
                case '\n':
                    out.append('\\');
                    out.append('n');
                    break;
                case '\t':
                    out.append('\\');
                    out.append('t');
                    break;
                case '\f':
                    out.append('\\');
                    out.append('f');
                    break;
                case '\r':
                    out.append('\\');
                    out.append('r');
                    break;
                default:
                    if (ch > 0xf) {
                        out.append("\\u00").append(hex(ch));
                    } else {
                        out.append("\\u000").append(hex(ch));
                    }
                    break;
                }
            } else {
                switch (ch) {
                case '"':
                    out.append('\\');
                    out.append('"');
                    break;
                case '\\':
                    out.append('\\');
                    out.append('\\');
                    break;
                default:
                    out.append(ch);
                    break;
                }
            }
        }
        return out.toString();
    }

    /**
     * <p>Returns an upper case hexadecimal <code>String</code> for the given
     * character.</p>
     *
     * @param ch The character to convert.
     * @return An upper case hexadecimal <code>String</code>
     */
    public static String hex(char ch) {
        return Integer.toHexString(ch).toUpperCase(Locale.ENGLISH);
    }
}
