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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Encoder for unsafe URI characters.
 * <p/>
 * A good source for details is <a href="http://en.wikipedia.org/wiki/Url_encode">wikipedia url encode</a> article.
 */
public final class UnsafeUriCharactersEncoder {
    private static BitSet unsafeCharactersRfc1738;
    private static BitSet unsafeCharactersHttp;
    private static final char[] HEX_DIGITS = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C',
        'D', 'E', 'F', 'a', 'b', 'c', 'd', 'e', 'f'};
    private static final Pattern RAW_PATTERN = Pattern.compile("RAW\\([^\\)]+\\)");

    static {
        unsafeCharactersRfc1738 = new BitSet(256);
        unsafeCharactersRfc1738.set(' ');
        unsafeCharactersRfc1738.set('"');
        unsafeCharactersRfc1738.set('<');
        unsafeCharactersRfc1738.set('>');
        unsafeCharactersRfc1738.set('#');
        unsafeCharactersRfc1738.set('%');
        unsafeCharactersRfc1738.set('{');
        unsafeCharactersRfc1738.set('}');
        unsafeCharactersRfc1738.set('|');
        unsafeCharactersRfc1738.set('\\');
        unsafeCharactersRfc1738.set('^');
        unsafeCharactersRfc1738.set('~');
        unsafeCharactersRfc1738.set('[');
        unsafeCharactersRfc1738.set(']');
        unsafeCharactersRfc1738.set('`');
    }

    static {
        unsafeCharactersHttp = new BitSet(256);
        unsafeCharactersHttp.set(' ');
        unsafeCharactersHttp.set('"');
        unsafeCharactersHttp.set('<');
        unsafeCharactersHttp.set('>');
        unsafeCharactersHttp.set('#');
        unsafeCharactersHttp.set('%');
        unsafeCharactersHttp.set('{');
        unsafeCharactersHttp.set('}');
        unsafeCharactersHttp.set('|');
        unsafeCharactersHttp.set('\\');
        unsafeCharactersHttp.set('^');
        unsafeCharactersHttp.set('~');
        unsafeCharactersHttp.set('`');
    }

    private UnsafeUriCharactersEncoder() {
        // util class
    }

    public static String encode(String s) {
        return encode(s, unsafeCharactersRfc1738);
    }

    public static String encodeHttpURI(String s) {
        return encode(s, unsafeCharactersHttp);
    }

    public static String encode(String s, BitSet unsafeCharacters) {
        return encode(s, unsafeCharacters, false);
    }

    public static String encode(String s, boolean checkRaw) {
        return encode(s, unsafeCharactersRfc1738, checkRaw);
    }

    public static String encodeHttpURI(String s, boolean checkRaw) {
        return encode(s, unsafeCharactersHttp, checkRaw);
    }

    private static List<Pair> checkRAW(String s) {
        Matcher matcher = RAW_PATTERN.matcher(s);
        List<Pair> answer = new ArrayList<Pair>();
        // Check all occurrences
        while (matcher.find()) {
            answer.add(new Pair(matcher.start(), matcher.end()));
        }
        return answer;
    }

    private static boolean isRaw(int index, List<Pair> pairs) {
        for (Pair pair : pairs) {
            if (index < pair.left) {
                return false;
            } else {
                if (index >= pair.left) {
                    if (index <= pair.right) {
                        return true;
                    } else {
                        continue;
                    }
                }
            }
        }
        return false;
    }

    private static class Pair {
        int left;
        int right;

        Pair(int left, int right) {
            this.left = left;
            this.right = right;
        }
    }

    // Just skip the encode for isRAW part
    public static String encode(String s, BitSet unsafeCharacters, boolean checkRaw) {
        List<Pair> rawPairs;
        if (checkRaw) {
            rawPairs = checkRAW(s);
        } else {
            rawPairs = new ArrayList<Pair>();
        }

        int n = s == null ? 0 : s.length();
        if (n == 0) {
            return s;
        }

        // First check whether we actually need to encode
        char chars[] = s.toCharArray();
        for (int i = 0;;) {
            // just deal with the ascii character
            if (chars[i] > 0 && chars[i] < 128) {
                if (unsafeCharacters.get(chars[i])) {
                    break;
                }
            }
            if (++i >= chars.length) {
                return s;
            }
        }

        // okay there are some unsafe characters so we do need to encode
        // see details at: http://en.wikipedia.org/wiki/Url_encode
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chars.length; i++) {
            char ch = chars[i];
            if (ch > 0 && ch < 128 && unsafeCharacters.get(ch)) {
                // special for % sign as it may be a decimal encoded value
                if (ch == '%') {
                    char next = i + 1 < chars.length ? chars[i + 1] : ' ';
                    char next2 = i + 2 < chars.length ? chars[i + 2] : ' ';

                    if (isHexDigit(next) && isHexDigit(next2) && !isRaw(i, rawPairs)) {
                        // its already encoded (decimal encoded) so just append as is
                        sb.append(ch);
                    } else {
                        // must escape then, as its an unsafe character
                        appendEscape(sb, (byte) ch);
                    }
                } else {
                    // must escape then, as its an unsafe character
                    appendEscape(sb, (byte) ch);
                }
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static void appendEscape(StringBuilder sb, byte b) {
        sb.append('%');
        sb.append(HEX_DIGITS[(b >> 4) & 0x0f]);
        sb.append(HEX_DIGITS[(b >> 0) & 0x0f]);
    }

    private static boolean isHexDigit(char ch) {
        for (char hex : HEX_DIGITS) {
            if (hex == ch) {
                return true;
            }
        }
        return false;
    }

}
