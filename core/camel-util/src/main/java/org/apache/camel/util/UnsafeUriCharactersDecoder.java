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


import java.util.HashMap;
import java.util.Map;

public final class UnsafeUriCharactersDecoder {
    private static final Map<String,String> unsafeStringsRfc1738;

    static {
        unsafeStringsRfc1738 = new HashMap<>();
        unsafeStringsRfc1738.put("%22","\"");
        unsafeStringsRfc1738.put("%3C","<");
        unsafeStringsRfc1738.put("%3E",">");
        unsafeStringsRfc1738.put("%7B","{");
        unsafeStringsRfc1738.put("%7D","}");
        unsafeStringsRfc1738.put("%7C","|");
        unsafeStringsRfc1738.put("%5C","\\\\");
        unsafeStringsRfc1738.put("%5E","^");
        unsafeStringsRfc1738.put("%7E","~");
        unsafeStringsRfc1738.put("%5B","[");
        unsafeStringsRfc1738.put("%5D","]");
        unsafeStringsRfc1738.put("%60","`");
        unsafeStringsRfc1738.put("%20"," ");
        unsafeStringsRfc1738.put("%23","#");
    }

    public static String decode(String uri){
        int len = uri.length();
        StringBuilder sb = new StringBuilder(len > 500 ? len / 2 : len);
        for (int i = 0; i < len; i++) {
            char ch = uri.charAt(i);
            if (ch == '%') {
                char next = i + 1 < len ? uri.charAt(i + 1) : ' ';
                char next2 = i + 2 < len ? uri.charAt(i + 2) : ' ';
                String encodedString = String.valueOf(ch) + next + next2;
                if (isHexDigit(next) && isHexDigit(next2) && unsafeStringsRfc1738.containsKey(encodedString.toUpperCase())) {
                    i = i + 2;
                    sb.append(unsafeStringsRfc1738.get(encodedString));
                } else {
                    sb.append(ch);
                }
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static boolean isHexDigit(char ch) {
        // 0..9 A..F a..f
        return ch >= 48 && ch <= 57 || ch >= 65 && ch <= 70 || ch >= 97 && ch <= 102;
    }

}