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
package org.apache.camel.catalog.impl;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Copied from org.apache.camel.util.URISupport
 */
public final class URISupport {

    public static final String RAW_TOKEN_PREFIX = "RAW";
    public static final char[] RAW_TOKEN_START = { '(', '{' };
    public static final char[] RAW_TOKEN_END = { ')', '}' };

    private static final String CHARSET = "UTF-8";

    private URISupport() {
        // Helper class
    }

    /**
     * Normalizes the URI so unsafe characters is encoded
     *
     * @param  uri                the input uri
     * @return                    as URI instance
     * @throws URISyntaxException is thrown if syntax error in the input uri
     */
    public static URI normalizeUri(String uri) throws URISyntaxException {
        return new URI(UnsafeUriCharactersEncoder.encode(uri, true));
    }

    public static Map<String, Object> extractProperties(Map<String, Object> properties, String optionPrefix) {
        Map<String, Object> rc = new LinkedHashMap<>(properties.size());

        for (Iterator<Map.Entry<String, Object>> it = properties.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, Object> entry = it.next();
            String name = entry.getKey();
            if (name.startsWith(optionPrefix)) {
                Object value = properties.get(name);
                name = name.substring(optionPrefix.length());
                rc.put(name, value);
                it.remove();
            }
        }

        return rc;
    }

    /**
     * Strips the query parameters from the uri
     *
     * @param  uri the uri
     * @return     the uri without the query parameter
     */
    public static String stripQuery(String uri) {
        int idx = uri.indexOf('?');
        if (idx > -1) {
            uri = uri.substring(0, idx);
        }
        return uri;
    }

    /**
     * Extracts the query parameters from the uri
     *
     * @param  uri the uri
     * @return     query parameters, or <tt>null</tt> if no parameters
     */
    public static String extractQuery(String uri) {
        int idx = uri.indexOf('?');
        if (idx > -1) {
            return uri.substring(idx + 1);
        }
        return null;
    }

    /**
     * Parses the query parameters of the uri (eg the query part).
     *
     * @param  uri                the uri
     * @return                    the parameters, or an empty map if no parameters (eg never null)
     * @throws URISyntaxException is thrown if uri has invalid syntax.
     */
    public static Map<String, Object> parseParameters(URI uri) throws URISyntaxException {
        String query = uri.getQuery();
        if (query == null) {
            String schemeSpecificPart = uri.getSchemeSpecificPart();
            int idx = schemeSpecificPart.indexOf('?');
            if (idx < 0) {
                // return an empty map
                return new LinkedHashMap<>(0);
            } else {
                query = schemeSpecificPart.substring(idx + 1);
            }
        } else {
            query = stripPrefix(query, "?");
        }
        return parseQuery(query);
    }

    /**
     * Strips the prefix from the value.
     * <p/>
     * Returns the value as-is if not starting with the prefix.
     *
     * @param  value  the value
     * @param  prefix the prefix to remove from value
     * @return        the value without the prefix
     */
    public static String stripPrefix(String value, String prefix) {
        if (value != null && value.startsWith(prefix)) {
            return value.substring(prefix.length());
        }
        return value;
    }

    /**
     * Parses the query part of the uri (eg the parameters).
     * <p/>
     * The URI parameters will by default be URI encoded. However you can define a parameter values with the syntax:
     * <tt>key=RAW(value)</tt> which tells Camel to not encode the value, and use the value as is (eg key=value) and the
     * value has <b>not</b> been encoded.
     *
     * @param  uri                the uri
     * @return                    the parameters, or an empty map if no parameters (eg never null)
     * @throws URISyntaxException is thrown if uri has invalid syntax.
     * @see                       #RAW_TOKEN_START
     * @see                       #RAW_TOKEN_END
     */
    public static Map<String, Object> parseQuery(String uri) throws URISyntaxException {
        return parseQuery(uri, false);
    }

    /**
     * Parses the query part of the uri (eg the parameters).
     * <p/>
     * The URI parameters will by default be URI encoded. However you can define a parameter values with the syntax:
     * <tt>key=RAW(value)</tt> which tells Camel to not encode the value, and use the value as is (eg key=value) and the
     * value has <b>not</b> been encoded.
     *
     * @param  uri                the uri
     * @param  useRaw             whether to force using raw values
     * @return                    the parameters, or an empty map if no parameters (eg never null)
     * @throws URISyntaxException is thrown if uri has invalid syntax.
     * @see                       #RAW_TOKEN_START
     * @see                       #RAW_TOKEN_END
     */
    public static Map<String, Object> parseQuery(String uri, boolean useRaw) throws URISyntaxException {
        if (isEmpty(uri)) {
            // return an empty map
            return new LinkedHashMap<>(0);
        }

        // must check for trailing & as the uri.split("&") will ignore those
        if (uri.endsWith("&")) {
            throw new URISyntaxException(
                    uri, "Invalid uri syntax: Trailing & marker found. "
                         + "Check the uri and remove the trailing & marker.");
        }

        // need to parse the uri query parameters manually as we cannot rely on splitting by &,
        // as & can be used in a parameter value as well.

        try {
            // use a linked map so the parameters is in the same order
            Map<String, Object> rc = new LinkedHashMap<>();

            boolean isKey = true;
            boolean isValue = false;
            boolean isRaw = false;
            StringBuilder key = new StringBuilder();
            StringBuilder value = new StringBuilder();

            // parse the uri parameters char by char
            for (int i = 0; i < uri.length(); i++) {
                // current char
                char ch = uri.charAt(i);
                // look ahead of the next char
                char next;
                if (i <= uri.length() - 2) {
                    next = uri.charAt(i + 1);
                } else {
                    next = '\u0000';
                }

                // are we a raw value
                char rawTokenEnd = 0;
                for (int j = 0; j < RAW_TOKEN_START.length; j++) {
                    String rawTokenStart = RAW_TOKEN_PREFIX + RAW_TOKEN_START[j];
                    isRaw = value.toString().startsWith(rawTokenStart);
                    if (isRaw) {
                        rawTokenEnd = RAW_TOKEN_END[j];
                        break;
                    }
                }

                // if we are in raw mode, then we keep adding until we hit the end marker
                if (isRaw) {
                    if (isKey) {
                        key.append(ch);
                    } else if (isValue) {
                        value.append(ch);
                    }

                    // we only end the raw marker if it's ")&", "}&", or at the end of the value

                    boolean end = ch == rawTokenEnd && (next == '&' || next == '\u0000');
                    if (end) {
                        // raw value end, so add that as a parameter, and reset flags
                        addParameter(key.toString(), value.toString(), rc, useRaw || isRaw);
                        key.setLength(0);
                        value.setLength(0);
                        isKey = true;
                        isValue = false;
                        isRaw = false;
                        // skip to next as we are in raw mode and have already added the value
                        i++;
                    }
                    continue;
                }

                // if its a key and there is a = sign then the key ends and we are in value mode
                if (isKey && ch == '=') {
                    isKey = false;
                    isValue = true;
                    isRaw = false;
                    continue;
                }

                // the & denote parameter is ended
                if (ch == '&') {
                    // parameter is ended, as we hit & separator
                    String aKey = key.toString();
                    // the key may be a placeholder of options which we then do not know what is
                    boolean validKey = !aKey.startsWith("{{") && !aKey.endsWith("}}");
                    if (validKey) {
                        addParameter(aKey, value.toString(), rc, useRaw || isRaw);
                    }
                    key.setLength(0);
                    value.setLength(0);
                    isKey = true;
                    isValue = false;
                    isRaw = false;
                    continue;
                }

                // regular char so add it to the key or value
                if (isKey) {
                    key.append(ch);
                } else if (isValue) {
                    value.append(ch);
                }
            }

            // any left over parameters, then add that
            if (key.length() > 0) {
                String aKey = key.toString();
                // the key may be a placeholder of options which we then do not know what is
                boolean validKey = !aKey.startsWith("{{") && !aKey.endsWith("}}");
                if (validKey) {
                    addParameter(aKey, value.toString(), rc, useRaw || isRaw);
                }
            }

            return rc;

        } catch (UnsupportedEncodingException e) {
            URISyntaxException se = new URISyntaxException(e.toString(), "Invalid encoding");
            se.initCause(e);
            throw se;
        }
    }

    @SuppressWarnings("unchecked")
    private static void addParameter(String name, String value, Map<String, Object> map, boolean isRaw)
            throws UnsupportedEncodingException {
        name = URLDecoder.decode(name, CHARSET);
        if (!isRaw) {
            // need to replace % with %25
            value = URLDecoder.decode(value.replace("%", "%25"), CHARSET);
        }

        // does the key already exist?
        if (map.containsKey(name)) {
            // yes it does, so make sure we can support multiple values, but using a list
            // to hold the multiple values
            Object existing = map.get(name);
            List<String> list;
            if (existing instanceof List) {
                list = (List<String>) existing;
            } else {
                // create a new list to hold the multiple values
                list = new ArrayList<>();
                String s = existing != null ? existing.toString() : null;
                if (s != null) {
                    list.add(s);
                }
            }
            list.add(value);
            map.put(name, list);
        } else {
            map.put(name, value);
        }
    }

    public static List<Pair<Integer>> scanRaw(String str) {
        List<Pair<Integer>> answer = new ArrayList<>();
        if (str == null || isEmpty(str)) {
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

    private static boolean resolveRaw(String str, BiConsumer<String, String> consumer) {
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

    /**
     * Assembles a query from the given map.
     *
     * @param  options            the map with the options (eg key/value pairs)
     * @param  ampersand          to use & for Java code, and &amp; for XML
     * @return                    a query string with <tt>key1=value&key2=value2&...</tt>, or an empty string if there
     *                            is no options.
     * @throws URISyntaxException is thrown if uri has invalid syntax.
     */
    public static String createQueryString(Map<String, String> options, String ampersand, boolean encode)
            throws URISyntaxException {
        try {
            if (options.size() > 0) {
                StringBuilder rc = new StringBuilder();
                boolean first = true;
                for (String key : options.keySet()) {
                    if (first) {
                        first = false;
                    } else {
                        rc.append(ampersand);
                    }

                    Object value = options.get(key);

                    // use the value as a String
                    String s = value != null ? value.toString() : null;
                    appendQueryStringParameter(key, s, rc, encode);
                }
                return rc.toString();
            } else {
                return "";
            }
        } catch (UnsupportedEncodingException e) {
            URISyntaxException se = new URISyntaxException(e.toString(), "Invalid encoding");
            se.initCause(e);
            throw se;
        }
    }

    private static void appendQueryStringParameter(String key, String value, StringBuilder rc, boolean encode)
            throws UnsupportedEncodingException {
        if (encode) {
            String encoded = URLEncoder.encode(key, CHARSET);
            rc.append(encoded);
        } else {
            rc.append(key);
        }
        if (value == null) {
            return;
        }
        // only append if value is not null
        rc.append("=");
        boolean isRaw = resolveRaw(value, (str, raw) -> {
            // do not encode RAW parameters
            rc.append(str);
        });
        if (!isRaw) {
            if (encode) {
                String encoded = URLEncoder.encode(value, CHARSET);
                rc.append(encoded);
            } else {
                rc.append(value);
            }
        }
    }

    /**
     * Tests whether the value is <tt>null</tt> or an empty string.
     *
     * @param  value the value, if its a String it will be tested for text length as well
     * @return       true if empty
     */
    public static boolean isEmpty(Object value) {
        return !isNotEmpty(value);
    }

    /**
     * Tests whether the value is <b>not</b> <tt>null</tt> or an empty string.
     *
     * @param  value the value, if its a String it will be tested for text length as well
     * @return       true if <b>not</b> empty
     */
    public static boolean isNotEmpty(Object value) {
        if (value == null) {
            return false;
        } else if (value instanceof String text) {
            return text.trim().length() > 0;
        } else {
            return true;
        }
    }

}
