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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * URI utilities.
 *
 * @version 
 */
public final class URISupport {

    // Match any key-value pair in the URI query string whose key contains
    // "passphrase" or "password" or secret key (case-insensitive).
    // First capture group is the key, second is the value.
    private static final Pattern SECRETS = Pattern.compile("([?&][^=]*(?:passphrase|password|secretKey)[^=]*)=([^&]*)",
            Pattern.CASE_INSENSITIVE);
    private static final String CHARSET = "UTF-8";

    private URISupport() {
        // Helper class
    }

    /**
     * Removes detected sensitive information (such as passwords) from the URI and returns the result.
     * @param uri The uri to sanitize.
     * @see #SECRETS for the matched pattern
     *
     * @return Returns null if the uri is null, otherwise the URI with the passphrase, password or secretKey sanitized.
     */
    public static String sanitizeUri(String uri) {
        return uri == null ? null : SECRETS.matcher(uri).replaceAll("$1=******");
    }

    public static Map<String, Object> parseQuery(String uri) throws URISyntaxException {
        // must check for trailing & as the uri.split("&") will ignore those
        if (uri != null && uri.endsWith("&")) {
            throw new URISyntaxException(uri, "Invalid uri syntax: Trailing & marker found. "
                + "Check the uri and remove the trailing & marker.");
        }

        try {
            // use a linked map so the parameters is in the same order
            Map<String, Object> rc = new LinkedHashMap<String, Object>();
            if (uri != null) {
                String[] parameters = uri.split("&");
                for (String parameter : parameters) {
                    int p = parameter.indexOf("=");
                    if (p >= 0) {
                        String name = URLDecoder.decode(parameter.substring(0, p), CHARSET);
                        String value = URLDecoder.decode(parameter.substring(p + 1), CHARSET);

                        // does the key already exist?
                        if (rc.containsKey(name)) {
                            // yes it does, so make sure we can support multiple values, but using a list
                            // to hold the multiple values
                            Object existing = rc.get(name);
                            List<String> list;
                            if (existing instanceof List) {
                                list = CastUtils.cast((List<?>) existing);
                            } else {
                                // create a new list to hold the multiple values
                                list = new ArrayList<String>();
                                String s = existing != null ? existing.toString() : null;
                                if (s != null) {
                                    list.add(s);
                                }
                            }
                            list.add(value);
                            rc.put(name, list);
                        } else {
                            rc.put(name, value);
                        }
                    } else {
                        rc.put(parameter, null);
                    }
                }
            }
            return rc;
        } catch (UnsupportedEncodingException e) {
            URISyntaxException se = new URISyntaxException(e.toString(), "Invalid encoding");
            se.initCause(e);
            throw se;
        }
    }

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
     * Creates a URI with the given query
     */
    public static URI createURIWithQuery(URI uri, String query) throws URISyntaxException {
        ObjectHelper.notNull(uri, "uri");

        // assemble string as new uri and replace parameters with the query instead
        String s = uri.toString();
        String before = ObjectHelper.before(s, "?");
        if (before != null) {
            s = before;
        }
        if (query != null) {
            s = s + "?" + query;
        }
        if (uri.getFragment() != null) {
            s = s + "#" + uri.getFragment();
        }

        return new URI(s);
    }

    public static String stripPrefix(String value, String prefix) {
        if (value.startsWith(prefix)) {
            return value.substring(prefix.length());
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static String createQueryString(Map<Object, Object> options) throws URISyntaxException {
        try {
            if (options.size() > 0) {
                StringBuilder rc = new StringBuilder();
                boolean first = true;
                for (Object o : options.keySet()) {
                    if (first) {
                        first = false;
                    } else {
                        rc.append("&");
                    }

                    String key = (String) o;
                    Object value = options.get(key);

                    // the value may be a list since the same key has multiple values
                    if (value instanceof List) {
                        List<String> list = (List<String>) value;
                        for (Iterator<String> it = list.iterator(); it.hasNext();) {
                            String s = it.next();
                            appendQueryStringParameter(key, s, rc);
                            // append & separator if there is more in the list to append
                            if (it.hasNext()) {
                                rc.append("&");
                            }
                        }
                    } else {
                        // use the value as a String
                        String s = value != null ? value.toString() : null;
                        appendQueryStringParameter(key, s, rc);
                    }
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

    private static void appendQueryStringParameter(String key, String value, StringBuilder rc) throws UnsupportedEncodingException {
        rc.append(URLEncoder.encode(key, CHARSET));
        // only append if value is not null
        if (value != null) {
            rc.append("=");
            rc.append(URLEncoder.encode(value, CHARSET));
        }
    }


    /**
     * Creates a URI from the original URI and the remaining parameters
     * <p/>
     * Used by various Camel components
     */
    public static URI createRemainingURI(URI originalURI, Map<Object, Object> params) throws URISyntaxException {
        String s = createQueryString(params);
        if (s.length() == 0) {
            s = null;
        }
        return createURIWithQuery(originalURI, s);
    }

    /**
     * Normalizes the uri by reordering the parameters so they are sorted and thus
     * we can use the uris for endpoint matching.
     *
     * @param uri the uri
     * @return the normalized uri
     * @throws URISyntaxException in thrown if the uri syntax is invalid
     */
    @SuppressWarnings("unchecked")
    public static String normalizeUri(String uri) throws URISyntaxException {

        URI u = new URI(UnsafeUriCharactersEncoder.encode(uri));
        String path = u.getSchemeSpecificPart();
        String scheme = u.getScheme();

        // not possible to normalize
        if (scheme == null || path == null) {
            return uri;
        }

        // lets trim off any query arguments
        if (path.startsWith("//")) {
            path = path.substring(2);
        }
        int idx = path.indexOf('?');
        if (idx > 0) {
            path = path.substring(0, idx);
        }

        // in case there are parameters we should reorder them
        Map parameters = URISupport.parseParameters(u);
        if (parameters.isEmpty()) {
            // no parameters then just return
            return buildUri(scheme, path, null);
        } else {
            // reorder parameters a..z
            List<String> keys = new ArrayList<String>(parameters.keySet());
            Collections.sort(keys);

            Map<Object, Object> sorted = new LinkedHashMap<Object, Object>(parameters.size());
            for (String key : keys) {
                sorted.put(key, parameters.get(key));
            }

            // build uri object with sorted parameters
            String query = URISupport.createQueryString(sorted);
            return buildUri(scheme, path, query);
        }
    }

    private static String buildUri(String scheme, String path, String query) {
        // must include :// to do a correct URI all components can work with
        return scheme + "://" + path + (query != null ? "?" + query : "");
    }
}
