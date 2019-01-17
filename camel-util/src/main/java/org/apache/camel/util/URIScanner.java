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

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static org.apache.camel.util.URISupport.RAW_TOKEN_END;
import static org.apache.camel.util.URISupport.RAW_TOKEN_PREFIX;
import static org.apache.camel.util.URISupport.RAW_TOKEN_START;

/**
 * RAW syntax aware URI scanner that provides various URI manipulations.
 */
class URIScanner {

    private enum Mode {
        KEY, VALUE
    }

    private static final char END = '\u0000';

    private final String charset;
    private final StringBuilder key;
    private final StringBuilder value;
    private Mode mode;
    private boolean isRaw;
    private char rawTokenEnd;

    public URIScanner(String charset) {
        this.charset = charset;
        key = new StringBuilder();
        value = new StringBuilder();
    }

    private void initState() {
        mode = Mode.KEY;
        key.setLength(0);
        value.setLength(0);
        isRaw = false;
    }

    private String getDecodedKey() throws UnsupportedEncodingException {
        return URLDecoder.decode(key.toString(), charset);
    }

    private String getDecodedValue() throws UnsupportedEncodingException {
        // need to replace % with %25
        String s = StringHelper.replaceAll(value.toString(), "%", "%25");
        String answer = URLDecoder.decode(s, charset);
        return answer;
    }

    public Map<String, Object> parseQuery(String uri, boolean useRaw) throws URISyntaxException {
        // need to parse the uri query parameters manually as we cannot rely on splitting by &,
        // as & can be used in a parameter value as well.

        try {
            // use a linked map so the parameters is in the same order
            Map<String, Object> answer = new LinkedHashMap<>();

            initState();

            // parse the uri parameters char by char
            for (int i = 0; i < uri.length(); i++) {
                // current char
                char ch = uri.charAt(i);
                // look ahead of the next char
                char next;
                if (i <= uri.length() - 2) {
                    next = uri.charAt(i + 1);
                } else {
                    next = END;
                }

                switch (mode) {
                case KEY:
                    // if there is a = sign then the key ends and we are in value mode
                    if (ch == '=') {
                        mode = Mode.VALUE;
                        continue;
                    }

                    if (ch != '&') {
                        // regular char so add it to the key
                        key.append(ch);
                    }
                    break;
                case VALUE:
                    // are we a raw value
                    isRaw = checkRaw();

                    // if we are in raw mode, then we keep adding until we hit the end marker
                    if (isRaw) {
                        value.append(ch);

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
                    break;
                default:
                    throw new IllegalStateException("Unknown mode: " + mode);
                }

                // the & denote parameter is ended
                if (ch == '&') {
                    // parameter is ended, as we hit & separator
                    addParameter(answer, useRaw || isRaw);
                    initState();
                }
            }

            // any left over parameters, then add that
            if (key.length() > 0) {
                addParameter(answer, useRaw || isRaw);
            }

            return answer;

        } catch (UnsupportedEncodingException e) {
            URISyntaxException se = new URISyntaxException(e.toString(), "Invalid encoding");
            se.initCause(e);
            throw se;
        }
    }

    private boolean checkRaw() {
        rawTokenEnd = 0;

        for (int i = 0; i < RAW_TOKEN_START.length; i++) {
            String rawTokenStart = RAW_TOKEN_PREFIX + RAW_TOKEN_START[i];
            boolean isRaw = value.toString().startsWith(rawTokenStart);
            if (isRaw) {
                rawTokenEnd = RAW_TOKEN_END[i];
                return true;
            }
        }

        return false;
    }

    private boolean isAtEnd(char ch, char next) {
        // we only end the raw marker if it's ")&", "}&", or at the end of the value
        return ch == rawTokenEnd && (next == '&' || next == END);
    }

    private void addParameter(Map<String, Object> answer, boolean isRaw) throws UnsupportedEncodingException {
        String name = getDecodedKey();
        String value = isRaw ? this.value.toString() : getDecodedValue();

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
            list.add(value);
            answer.put(name, list);
        } else {
            answer.put(name, value);
        }
    }

    public static List<Pair<Integer>> scanRaw(String str) {
        List<Pair<Integer>> answer = new ArrayList<>();
        if (str == null || ObjectHelper.isEmpty(str)) {
            return answer;
        }

        int offset = 0;
        int start = str.indexOf(RAW_TOKEN_PREFIX);
        while (start >= 0 && offset < str.length()) {
            offset = start + RAW_TOKEN_PREFIX.length();
            for (int i = 0; i < RAW_TOKEN_START.length; i++) {
                String tokenStart = RAW_TOKEN_PREFIX + RAW_TOKEN_START[i];
                char tokenEnd = RAW_TOKEN_END[i];
                if (str.startsWith(tokenStart, start)) {
                    offset = scanRawToEnd(str, start, tokenStart, tokenEnd, answer);
                    continue;
                }
            }
            start = str.indexOf(RAW_TOKEN_PREFIX, offset);
        }
        return answer;
    }

    private static int scanRawToEnd(String str, int start, String tokenStart, char tokenEnd,
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

    public static boolean isRaw(int index, List<Pair<Integer>> pairs) {
        for (Pair<Integer> pair : pairs) {
            if (index < pair.getLeft()) {
                return false;
            }
            if (index <= pair.getRight()) {
                return true;
            }
        }
        return false;
    }

    public static boolean resolveRaw(String str, BiConsumer<String, String> consumer) {
        for (int i = 0; i < RAW_TOKEN_START.length; i++) {
            String tokenStart = RAW_TOKEN_PREFIX + RAW_TOKEN_START[i];
            String tokenEnd = String.valueOf(RAW_TOKEN_END[i]);
            if (str.startsWith(tokenStart) && str.endsWith(tokenEnd)) {
                String raw = str.substring(tokenStart.length(), str.length() - 1);
                consumer.accept(str, raw);
                return true;
            }
        }
        // not RAW value
        return false;
    }

}
