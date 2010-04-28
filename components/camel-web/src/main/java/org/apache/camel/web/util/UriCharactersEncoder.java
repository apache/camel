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
package org.apache.camel.web.util;

import java.util.BitSet;

public final class UriCharactersEncoder {
    private static BitSet unsafeCharacters;    
    private static final char[] HEX_DIGITS = {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    static {
        unsafeCharacters = new BitSet(256);
        unsafeCharacters.set(' ');
        unsafeCharacters.set('"');
        unsafeCharacters.set('<');
        unsafeCharacters.set('>');
        unsafeCharacters.set('#');
        unsafeCharacters.set('%');
        unsafeCharacters.set('{');
        unsafeCharacters.set('}');
        unsafeCharacters.set('|');
        unsafeCharacters.set('\\');
        unsafeCharacters.set('^');
        unsafeCharacters.set('~');
        unsafeCharacters.set('[');
        unsafeCharacters.set(']');
        unsafeCharacters.set('`');
        unsafeCharacters.set('/');
        unsafeCharacters.set('$');
    }

    private UriCharactersEncoder() {
        // util class
    }

    public static String encode(String s) {
        int n = s.length();
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
        StringBuilder sb = new StringBuilder();
        for (char ch : chars) {
            if (ch > 0 && ch < 128 && unsafeCharacters.get(ch)) {
                appendEscape(sb, (byte)ch);
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

}
