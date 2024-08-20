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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;

public final class CatalogHelper {
    private static final Charset CHARSET = StandardCharsets.UTF_8;

    private CatalogHelper() {
    }

    /**
     * Loads the entire stream into memory as a String and returns it.
     * <p/>
     * <b>Notice:</b> This implementation appends a <tt>\n</tt> as line terminator at the of the text.
     * <p/>
     * Warning, don't use for crazy big streams :)
     */
    public static void loadLines(InputStream in, List<String> lines) throws IOException {
        try (final InputStreamReader isr = new InputStreamReader(in);
             final BufferedReader reader = new LineNumberReader(isr)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
    }

    /**
     * Loads the entire stream into memory as a String and returns it.
     * <p/>
     * <b>Notice:</b> This implementation appends a <tt>\n</tt> as line terminator at the of the text.
     * <p/>
     * Warning, don't use for crazy big streams :)
     */
    public static List<String> loadLines(InputStream in) throws IOException {
        List<String> lines = new ArrayList<>();
        loadLines(in, lines);
        return lines;
    }

    /**
     * Loads the entire stream into memory as a String and returns it.
     * <p/>
     * <b>Notice:</b> This implementation appends a <tt>\n</tt> as line terminator at the of the text.
     * <p/>
     * Warning, don't use for crazy big streams :)
     */
    public static String loadText(InputStream in) throws IOException {
        return IOHelper.loadText(in);
    }

    /**
     * Matches the name with the pattern.
     *
     * @param  name    the name
     * @param  pattern the pattern
     * @return         <tt>true</tt> if matched, or <tt>false</tt> if not
     */
    public static boolean matchWildcard(String name, String pattern) {
        // we have wildcard support in that hence you can match with: file* to match any file endpoints
        if (pattern.endsWith("*") && name.startsWith(pattern.substring(0, pattern.length() - 1))) {
            return true;
        }
        return false;
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
            return !text.isBlank();
        } else {
            return true;
        }
    }

    /**
     * Parses the query parameters of the uri (eg the query part) manually.
     * <p>
     * Note: we cannot use {@link URISupport#parseParameters(URI)} because it uses the URIScanner and that scanner does
     * not support "{{" and "}}".
     *
     * @param  uri                the uri
     * @return                    the parameters, or an empty map if no parameters (eg never null)
     * @throws URISyntaxException is thrown if uri has invalid syntax.
     */
    static Map<String, Object> parseParameters(URI uri) throws URISyntaxException {
        String query = uri.getQuery();
        if (query == null) {
            String schemeSpecificPart = uri.getSchemeSpecificPart();
            query = StringHelper.after(schemeSpecificPart, "?");
            if (query == null) {
                return new LinkedHashMap<>(0);
            }
        } else {
            query = URISupport.stripPrefix(query, "?");
        }
        return parseQueryManually(query);
    }

    /**
     * Parses the query part of the uri (eg the parameters) manually. This method is mostly used by the CamelCatalog in
     * order to be able to handle certain special characters markers (i.e.: "{{" and "}}"). It should not be used
     * anywhere else.
     * <p/>
     * The URI parameters will by default be URI encoded. However, you can define a parameter values with the syntax:
     * <tt>key=RAW(value)</tt> which tells Camel to not encode the value, and use the value as is (eg key=value) and the
     * value has <b>not</b> been encoded.
     *
     * @param  uri                the uri
     * @return                    the parameters, or an empty map if no parameters (eg never null)
     * @throws URISyntaxException is thrown if uri has invalid syntax.
     */
    static Map<String, Object> parseQueryManually(String uri) throws URISyntaxException {
        if (uri == null || uri.isEmpty()) {
            // return an empty map
            return Collections.emptyMap();
        }

        // must check for trailing & as the uri.split("&") will ignore those
        if (uri.endsWith("&")) {
            throw new URISyntaxException(
                    uri, "Invalid uri syntax: Trailing & marker found. "
                         + "Check the uri and remove the trailing & marker.");
        }

        // need to parse the uri query parameters manually as we cannot rely on splitting by &,
        // as & can be used in a parameter value as well.

        // use a linked map so the parameters is in the same order
        Map<String, Object> rc = new LinkedHashMap<>();

        boolean isKey = true;
        boolean isRaw = false;
        StringBuilder key = new StringBuilder();
        StringBuilder value = new StringBuilder();

        // parse the uri parameters char by char
        for (int i = 0; i < uri.length(); i++) {
            // current char
            char ch = uri.charAt(i);

            isRaw = isRaw(isRaw, value);

            // if its a key and there is a = sign then the key ends and we are in value mode
            if (isKey && ch == '=') {
                isKey = false;
                isRaw = false;
                continue;
            }

            // the & denote parameter is ended
            if (ch == '&') {
                // parameter is ended, as we hit & separator
                String aKey = key.toString();
                // the key may be a placeholder of options which we then do not know what is
                addKeyIfPresent(aKey, value, rc, isRaw);
                key.setLength(0);
                value.setLength(0);
                isKey = true;
                isRaw = false;
                continue;
            }

            // regular char so add it to the key or value
            if (isKey) {
                key.append(ch);
            } else {
                value.append(ch);
            }
        }

        // any left over parameters, then add that
        if (!key.isEmpty()) {
            String aKey = key.toString();
            // the key may be a placeholder of options which we then do not know what is
            addKeyIfPresent(aKey, value, rc, isRaw);
        }

        return rc;

    }

    private static boolean isRaw(boolean isRaw, StringBuilder value) {
        // are we a raw value
        for (int j = 0; j < URISupport.RAW_TOKEN_START.length; j++) {
            String rawTokenStart = URISupport.RAW_TOKEN_PREFIX + URISupport.RAW_TOKEN_START[j];
            isRaw = value.toString().startsWith(rawTokenStart);
            if (isRaw) {
                break;
            }
        }
        return isRaw;
    }

    private static void addKeyIfPresent(String aKey, StringBuilder value, Map<String, Object> rc, boolean isRaw) {
        boolean validKey = !aKey.startsWith("{{") && !aKey.endsWith("}}");
        if (validKey) {
            String valueStr = optionallyDecode(value.toString(), isRaw);

            addParameter(aKey, valueStr, rc, isRaw);
        }
    }

    private static void addParameter(String name, final String value, Map<String, Object> map, boolean isRaw) {
        name = URLDecoder.decode(name, CHARSET);

        // does the key already exist?
        if (map.containsKey(name)) {
            // yes it does, so make sure we can support multiple values, but using a list
            // to hold the multiple values
            map.computeIfPresent(name, (k, v) -> replaceWithList(v, value));
        } else {
            map.put(name, value);
        }
    }

    private static String optionallyDecode(String value, boolean isRaw) {
        if (!isRaw) {
            // need to replace % with %25
            return URLDecoder.decode(value.replace("%", "%25"), CHARSET);
        }

        return value;
    }

    @SuppressWarnings("unchecked")
    private static Object replaceWithList(Object oldValue, String newValue) {
        List<String> list;
        if (oldValue instanceof List oldValueList) {
            list = oldValueList;
            list.add(newValue);

        } else {
            // create a new list to hold the multiple values
            list = new ArrayList<>();
            String s = oldValue != null ? oldValue.toString() : null;
            if (s != null) {
                list.add(s);
            }

        }
        return list;
    }

}
