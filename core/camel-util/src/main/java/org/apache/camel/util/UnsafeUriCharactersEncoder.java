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

import java.util.BitSet;
import java.util.List;

/**
 * Encoder for unsafe URI characters.
 * <p/>
 * A good source for details is <a href="http://en.wikipedia.org/wiki/Url_encode">wikipedia url encode</a> article.
 */
public final class UnsafeUriCharactersEncoder {
    private static final BitSet unsafeCharactersFastParser;
    private static final BitSet unsafeCharactersRfc1738;
    private static final BitSet unsafeCharactersHttp;
    private static final char[] HEX_DIGITS = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C',
            'D', 'E', 'F', 'a', 'b', 'c', 'd', 'e', 'f' };

    static {
        unsafeCharactersFastParser = new BitSet(14);
        unsafeCharactersFastParser.set(' ');
        unsafeCharactersFastParser.set('"');
        unsafeCharactersFastParser.set('<');
        unsafeCharactersFastParser.set('>');
        unsafeCharactersFastParser.set('%');
        unsafeCharactersFastParser.set('{');
        unsafeCharactersFastParser.set('}');
        unsafeCharactersFastParser.set('|');
        unsafeCharactersFastParser.set('\\');
        unsafeCharactersFastParser.set('^');
        unsafeCharactersFastParser.set('~');
        unsafeCharactersFastParser.set('[');
        unsafeCharactersFastParser.set(']');
        unsafeCharactersFastParser.set('`');
        // we allow # as a safe when using the fast parser as its used for
        // looking up beans in the registry (foo=#myBar)
    }

    static {
        unsafeCharactersRfc1738 = new BitSet(15);
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
        unsafeCharactersHttp = new BitSet(13);
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

    public static boolean isSafeFastParser(char ch) {
        return !unsafeCharactersFastParser.get(ch);
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

    // Just skip the encode for isRAW part
    public static String encode(String s, BitSet unsafeCharacters, boolean checkRaw) {
        if (s == null) {
            return null;
        }
        int len = s.length();
        if (len == 0) {
            return s;
        }

        // first check whether we actually need to encode
        boolean safe = true;
        for (int i = 0; i < len; i++) {
            char ch = s.charAt(i);
            // just deal with the ascii character
            if (ch > 0 && ch < 128 && unsafeCharacters.get(ch)) {
                safe = false;
                break;
            }
        }
        if (safe) {
            return s;
        }

        List<Pair<Integer>> rawPairs = null;
        if (checkRaw) {
            rawPairs = URISupport.scanRaw(s);
        }

        // add a bit of extra space as initial capacity
        int initial = len + 8;

        // okay there are some unsafe characters so we do need to encode
        // see details at: http://en.wikipedia.org/wiki/Url_encode
        StringBuilder sb = new StringBuilder(initial);
        for (int i = 0; i < len; i++) {
            char ch = s.charAt(i);
            if (ch > 0 && ch < 128 && unsafeCharacters.get(ch)) {
                // special for % sign as it may be a decimal encoded value
                if (ch == '%') {
                    char next = i + 1 < len ? s.charAt(i + 1) : ' ';
                    char next2 = i + 2 < len ? s.charAt(i + 2) : ' ';

                    if (isHexDigit(next) && isHexDigit(next2) && !URISupport.isRaw(i, rawPairs)) {
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
        // 0..9 A..F a..f
        return ch >= 48 && ch <= 57 || ch >= 65 && ch <= 70 || ch >= 97 && ch <= 102;
    }

}
