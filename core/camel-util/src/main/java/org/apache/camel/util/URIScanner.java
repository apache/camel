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

import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.apache.camel.util.URISupport.RAW_TOKEN_END;
import static org.apache.camel.util.URISupport.RAW_TOKEN_PREFIX;
import static org.apache.camel.util.URISupport.RAW_TOKEN_START;

/**
 * RAW syntax aware URI scanner that provides various URI manipulations.
 */
class URIScanner {
    private static final Charset CHARSET = StandardCharsets.UTF_8;
    private static final char END = '\u0000';

    private final StringBuilder key;
    private final StringBuilder value;
    private boolean keyMode = true;
    private boolean isRaw;
    private char rawTokenEnd;

    URIScanner() {
        /*
         * By default, StringBuffer has an internal buffer of 16 chars. Our keys and values may usually be larger than,
         * therefore, start with a value slightly larger than default to avoid resizing the array in most cases.
         */
        this.key = new StringBuilder(32);
        this.value = new StringBuilder(32);
    }

    private void initState() {
        this.keyMode = true;
        this.key.setLength(0);
        this.value.setLength(0);
        this.isRaw = false;
    }

    public Map<String, Object> parseQuery(String uri, boolean useRaw) {
        // need to parse the uri query parameters manually as we cannot rely on splitting by &,
        // as & can be used in a parameter value as well.

        // use a linked map so the parameters is in the same order
        Map<String, Object> answer = new LinkedHashMap<>();

        // parse the uri parameters char by char
        final int len = uri.length();
        for (int i = 0; i < len; i++) {
            // current char
            final char ch = uri.charAt(i);

            if (keyMode) {
                // if there is a = sign then the key ends and we are in value mode
                if (ch == '=') {
                    keyMode = false;
                    continue;
                }

                if (ch != '&') {
                    // regular char so add it to the key
                    key.append(ch);
                }
            } else {
                // are we a raw value
                isRaw = checkRaw();

                // if we are in raw mode, then we keep adding until we hit the end marker
                if (isRaw) {
                    value.append(ch);

                    // look ahead of the next char
                    final char next = i <= len - 2 ? uri.charAt(i + 1) : END;
                    if (isAtEnd(ch, next)) {
                        // raw value end, so add that as a parameter, and reset flags
                        addParameter(answer, useRaw || isRaw);
                        initState();
                        // skip to next as we are in raw mode and have already added the value
                        i++;
                    }
                    continue;
                }

                if (ch != '&') {
                    // regular char so add it to the value
                    value.append(ch);
                }
            }

            // the & denote parameter is ended
            if (ch == '&') {
                // parameter is ended, as we hit & separator
                addParameter(answer, useRaw || isRaw);
                initState();
            }
        }

        // any left over parameters, then add that
        if (!key.isEmpty()) {
            addParameter(answer, useRaw || isRaw);
        }

        return answer;
    }

    private boolean checkRaw() {
        rawTokenEnd = 0;

        if (value.length() < 4) {
            return false;
        }

        // optimize to not create new objects
        char char1 = value.charAt(0);
        char char2 = value.charAt(1);
        char char3 = value.charAt(2);
        char char4 = value.charAt(3);
        if (char1 == 'R' && char2 == 'A' && char3 == 'W') {
            if (char4 == '(') {
                rawTokenEnd = RAW_TOKEN_END[0];
                return true;
            } else if (char4 == '{') {
                rawTokenEnd = RAW_TOKEN_END[1];
                return true;
            }
        }

        return false;
    }

    private boolean isAtEnd(char ch, char next) {
        // we only end the raw marker if it's ")&", "}&", or at the end of the value
        return ch == rawTokenEnd && (next == '&' || next == END);
    }

    private void addParameter(Map<String, Object> answer, boolean isRaw) {
        String name = URLDecoder.decode(key.toString(), CHARSET);
        String text;
        if (isRaw) {
            text = value.toString();
        } else {
            // need to replace % with %25 to avoid losing "%" when decoding
            final String s = replacePercent(value.toString());

            text = URLDecoder.decode(s, CHARSET);
        }

        // does the key already exist?
        if (answer.containsKey(name)) {
            // yes it does, so make sure we can support multiple values, but using a list
            // to hold the multiple values
            Object existing = answer.get(name);
            List<String> list;
            if (existing instanceof List) {
                list = CastUtils.cast((List<?>) existing);
            } else {
                // create a new list to hold the multiple values
                list = new ArrayList<>();
                String s = existing != null ? existing.toString() : null;
                if (s != null) {
                    list.add(s);
                }
            }
            list.add(text);
            answer.put(name, list);
        } else {
            answer.put(name, text);
        }
    }

    public static List<Pair<Integer>> scanRaw(String str) {
        if (str == null || ObjectHelper.isEmpty(str)) {
            return Collections.emptyList();
        }

        List<Pair<Integer>> answer = new ArrayList<>();
        int offset = 0;
        int start = str.indexOf(RAW_TOKEN_PREFIX);
        while (start >= 0 && offset < str.length()) {
            offset = start + RAW_TOKEN_PREFIX.length();
            for (int i = 0; i < RAW_TOKEN_START.length; i++) {
                String tokenStart = RAW_TOKEN_PREFIX + RAW_TOKEN_START[i];
                char tokenEnd = RAW_TOKEN_END[i];
                if (str.startsWith(tokenStart, start)) {
                    offset = scanRawToEnd(str, start, tokenStart, tokenEnd, answer);
                }
            }
            start = str.indexOf(RAW_TOKEN_PREFIX, offset);
        }
        return answer;
    }

    private static int scanRawToEnd(
            String str, int start, String tokenStart, char tokenEnd,
            List<Pair<Integer>> answer) {
        // we search the first end bracket to close the RAW token
        // as opposed to parsing query, this doesn't allow the occurrences of end brackets
        // inbetween because this may be used on the host/path parts of URI
        // and thus we cannot rely on '&' for detecting the end of a RAW token
        int end = str.indexOf(tokenEnd, start + tokenStart.length());
        if (end < 0) {
            // still return a pair even if RAW token is not closed
            answer.add(new Pair<>(start, str.length()));
            return str.length();
        }
        answer.add(new Pair<>(start, end));
        return end + 1;
    }

    public static String resolveRaw(String str) {
        int len = str.length();
        if (len <= 4) {
            return null;
        }

        int endPos = len - 1;
        char last = str.charAt(endPos);

        // optimize to not create new objects
        if (last == ')') {
            char char1 = str.charAt(0);
            char char2 = str.charAt(1);
            char char3 = str.charAt(2);
            char char4 = str.charAt(3);
            if (char1 == 'R' && char2 == 'A' && char3 == 'W' && char4 == '(') {
                return str.substring(4, endPos);
            }
        } else if (last == '}') {
            char char1 = str.charAt(0);
            char char2 = str.charAt(1);
            char char3 = str.charAt(2);
            char char4 = str.charAt(3);
            if (char1 == 'R' && char2 == 'A' && char3 == 'W' && char4 == '{') {
                return str.substring(4, endPos);
            }
        }

        // not RAW value
        return null;
    }

    public static String replacePercent(String input) {
        if (input.contains("%")) {
            return input.replace("%", "%25");
        }

        return input;
    }

}
