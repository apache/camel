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

/**
 * Copied from org.apache.camel.util.URISupport
 */
public final class URISupport {

    public static final String RAW_TOKEN_START = "RAW(";
    public static final String RAW_TOKEN_END = ")";

    private static final String CHARSET = "UTF-8";

    private URISupport() {
        // Helper class
    }

    /**
     * Normalizes the URI so unsafe characters is encoded
     *
     * @param uri the input uri
     * @return as URI instance
     * @throws URISyntaxException is thrown if syntax error in the input uri
     */
    public static URI normalizeUri(String uri) throws URISyntaxException {
        return new URI(UnsafeUriCharactersEncoder.encode(uri, true));
    }

    public static Map<String, Object> extractProperties(Map<String, Object> properties, String optionPrefix) {
        Map<String, Object> rc = new LinkedHashMap<String, Object>(properties.size());

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
     * @param uri  the uri
     * @return the uri without the query parameter
     */
    public static String stripQuery(String uri) {
        int idx = uri.indexOf('?');
        if (idx > -1) {
            uri = uri.substring(0, idx);
        }
        return uri;
    }

    /**
     * Parses the query parameters of the uri (eg the query part).
     *
     * @param uri the uri
     * @return the parameters, or an empty map if no parameters (eg never null)
     * @throws URISyntaxException is thrown if uri has invalid syntax.
     */
    public static Map<String, Object> parseParameters(URI uri) throws URISyntaxException {
        String query = uri.getQuery();
        if (query == null) {
            String schemeSpecificPart = uri.getSchemeSpecificPart();
            int idx = schemeSpecificPart.indexOf('?');
            if (idx < 0) {
                // return an empty map
                return new LinkedHashMap<String, Object>(0);
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
     * @param value  the value
     * @param prefix the prefix to remove from value
     * @return the value without the prefix
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
     * The URI parameters will by default be URI encoded. However you can define a parameter
     * values with the syntax: <tt>key=RAW(value)</tt> which tells Camel to not encode the value,
     * and use the value as is (eg key=value) and the value has <b>not</b> been encoded.
     *
     * @param uri the uri
     * @return the parameters, or an empty map if no parameters (eg never null)
     * @throws URISyntaxException is thrown if uri has invalid syntax.
     * @see #RAW_TOKEN_START
     * @see #RAW_TOKEN_END
     */
    public static Map<String, Object> parseQuery(String uri) throws URISyntaxException {
        return parseQuery(uri, false);
    }

    /**
     * Parses the query part of the uri (eg the parameters).
     * <p/>
     * The URI parameters will by default be URI encoded. However you can define a parameter
     * values with the syntax: <tt>key=RAW(value)</tt> which tells Camel to not encode the value,
     * and use the value as is (eg key=value) and the value has <b>not</b> been encoded.
     *
     * @param uri the uri
     * @param useRaw whether to force using raw values
     * @return the parameters, or an empty map if no parameters (eg never null)
     * @throws URISyntaxException is thrown if uri has invalid syntax.
     * @see #RAW_TOKEN_START
     * @see #RAW_TOKEN_END
     */
    public static Map<String, Object> parseQuery(String uri, boolean useRaw) throws URISyntaxException {
        // must check for trailing & as the uri.split("&") will ignore those
        if (uri != null && uri.endsWith("&")) {
            throw new URISyntaxException(uri, "Invalid uri syntax: Trailing & marker found. "
                    + "Check the uri and remove the trailing & marker.");
        }

        if (isEmpty(uri)) {
            // return an empty map
            return new LinkedHashMap<String, Object>(0);
        }

        // need to parse the uri query parameters manually as we cannot rely on splitting by &,
        // as & can be used in a parameter value as well.

        try {
            // use a linked map so the parameters is in the same order
            Map<String, Object> rc = new LinkedHashMap<String, Object>();

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
                isRaw = value.toString().startsWith(RAW_TOKEN_START);

                // if we are in raw mode, then we keep adding until we hit the end marker
                if (isRaw) {
                    if (isKey) {
                        key.append(ch);
                    } else if (isValue) {
                        value.append(ch);
                    }

                    // we only end the raw marker if its )& or at the end of the value

                    boolean end = ch == RAW_TOKEN_END.charAt(0) && (next == '&' || next == '\u0000');
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
    private static void addParameter(String name, String value, Map<String, Object> map, boolean isRaw) throws UnsupportedEncodingException {
        name = URLDecoder.decode(name, CHARSET);
        if (!isRaw) {
            // need to replace % with %25
            value = URLDecoder.decode(value.replaceAll("%", "%25"), CHARSET);
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
                list = new ArrayList<String>();
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

    /**
     * Assembles a query from the given map.
     *
     * @param options  the map with the options (eg key/value pairs)
     * @param ampersand to use & for Java code, and &amp; for XML
     * @return a query string with <tt>key1=value&key2=value2&...</tt>, or an empty string if there is no options.
     * @throws URISyntaxException is thrown if uri has invalid syntax.
     */
    public static String createQueryString(Map<String, String> options, String ampersand, boolean encode) throws URISyntaxException {
        try {
            if (options.size() > 0) {
                StringBuilder rc = new StringBuilder();
                boolean first = true;
                for (Object o : options.keySet()) {
                    if (first) {
                        first = false;
                    } else {
                        rc.append(ampersand);
                    }

                    String key = (String) o;
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

    private static void appendQueryStringParameter(String key, String value, StringBuilder rc, boolean encode) throws UnsupportedEncodingException {
        if (encode) {
            rc.append(URLEncoder.encode(key, CHARSET));
        } else {
            rc.append(key);
        }
        // only append if value is not null
        if (value != null) {
            rc.append("=");
            if (value.startsWith(RAW_TOKEN_START) && value.endsWith(RAW_TOKEN_END)) {
                // do not encode RAW parameters
                rc.append(value);
            } else {
                if (encode) {
                    rc.append(URLEncoder.encode(value, CHARSET));
                } else {
                    rc.append(value);
                }
            }
        }
    }

    /**
     * Tests whether the value is <tt>null</tt> or an empty string.
     *
     * @param value  the value, if its a String it will be tested for text length as well
     * @return true if empty
     */
    public static boolean isEmpty(Object value) {
        return !isNotEmpty(value);
    }

    /**
     * Tests whether the value is <b>not</b> <tt>null</tt> or an empty string.
     *
     * @param value  the value, if its a String it will be tested for text length as well
     * @return true if <b>not</b> empty
     */
    public static boolean isNotEmpty(Object value) {
        if (value == null) {
            return false;
        } else if (value instanceof String) {
            String text = (String) value;
            return text.trim().length() > 0;
        } else {
            return true;
        }
    }

}
