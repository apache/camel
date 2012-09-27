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
    
    // Match the user password in the URI as second capture group
    // (applies to URI with authority component and userinfo token in the form "user:password").
    private static final Pattern USERINFO_PASSWORD = Pattern.compile("(.*://.*:)(.*)(@)");
    
    // Match the user password in the URI path as second capture group
    // (applies to URI path with authority component and userinfo token in the form "user:password").
    private static final Pattern PATH_USERINFO_PASSWORD = Pattern.compile("(.*:)(.*)(@)");
    
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
        String sanitized = uri;
        if (uri != null) {
            sanitized = SECRETS.matcher(sanitized).replaceAll("$1=******");
            sanitized = USERINFO_PASSWORD.matcher(sanitized).replaceFirst("$1******$3");
        }
        return sanitized;
    }
    
    /**
     * Removes detected sensitive information (such as passwords) from the
     * <em>path part</em> of an URI (that is, the part without the query
     * parameters or component prefix) and returns the result.
     * 
     * @param path the URI path to sanitize
     * @return null if the path is null, otherwise the sanitized path
     */
    public static String sanitizePath(String path) {
        String sanitized = path;
        if (path != null) {
            sanitized = PATH_USERINFO_PASSWORD.matcher(sanitized).replaceFirst("$1******$3");
        }
        return sanitized;
    }

    /**
     * Parses the query part of the uri (eg the parameters).
     *
     * @param uri the uri
     * @return the parameters, or an empty map if no parameters (eg never null)
     * @throws URISyntaxException is thrown if uri has invalid syntax.
     */
    public static Map<String, Object> parseQuery(String uri) throws URISyntaxException {
        // must check for trailing & as the uri.split("&") will ignore those
        if (uri != null && uri.endsWith("&")) {
            throw new URISyntaxException(uri, "Invalid uri syntax: Trailing & marker found. "
                + "Check the uri and remove the trailing & marker.");
        }

        if (ObjectHelper.isEmpty(uri)) {
            // return an empty map
            return new LinkedHashMap<String, Object>(0);
        }

        try {
            // use a linked map so the parameters is in the same order
            Map<String, Object> rc = new LinkedHashMap<String, Object>();
            if (uri != null) {
                String[] parameters = uri.split("&");
                for (String parameter : parameters) {
                    int p = parameter.indexOf("=");
                    if (p >= 0) {
                        // The replaceAll is an ugly workaround for CAMEL-4954, awaiting a cleaner fix once CAMEL-4425
                        // is fully resolved in all components
                        String name = URLDecoder.decode(parameter.substring(0, p), CHARSET);
                        String value = URLDecoder.decode(parameter.substring(p + 1).replaceAll("%", "%25"), CHARSET);

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
     * Creates a URI with the given query
     *
     * @param uri the uri
     * @param query the query to append to the uri
     * @return uri with the query appended
     * @throws URISyntaxException is thrown if uri has invalid syntax.
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
        if ((!s.contains("#")) && (uri.getFragment() != null)) {
            s = s + "#" + uri.getFragment();
        }

        return new URI(s);
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
        if (value.startsWith(prefix)) {
            return value.substring(prefix.length());
        }
        return value;
    }

    /**
     * Assembles a query from the given map.
     *
     * @param options  the map with the options (eg key/value pairs)
     * @return a query string with <tt>key1=value&key2=value2&...</tt>, or an empty string if there is no options.
     * @throws URISyntaxException is thrown if uri has invalid syntax.
     */
    @SuppressWarnings("unchecked")
    public static String createQueryString(Map<String, Object> options) throws URISyntaxException {
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
    public static URI createRemainingURI(URI originalURI, Map<String, Object> params) throws URISyntaxException {
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
     * @throws UnsupportedEncodingException is thrown if encoding error
     */
    public static String normalizeUri(String uri) throws URISyntaxException, UnsupportedEncodingException {

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
        // when the path has ?
        if (idx != -1) {
            path = path.substring(0, idx);
        }

        path = UnsafeUriCharactersEncoder.encode(path);

        // okay if we have user info in the path and they use @ in username or password,
        // then we need to encode them (but leave the last @ sign before the hostname)
        // this is needed as Camel end users may not encode their user info properly, but expect
        // this to work out of the box with Camel, and hence we need to fix it for them
        String userInfoPath = path;
        if (userInfoPath.contains("/")) {
            userInfoPath = userInfoPath.substring(0, userInfoPath.indexOf("/"));
        }
        if (StringHelper.countChar(userInfoPath, '@') > 1) {
            int max = userInfoPath.lastIndexOf('@');
            String before = userInfoPath.substring(0, max);
            // after must be from original path
            String after = path.substring(max);

            // replace the @ with %40
            before = StringHelper.replaceAll(before, "@", "%40");
            path = before + after;
        }

        // in case there are parameters we should reorder them
        Map<String, Object> parameters = URISupport.parseParameters(u);
        if (parameters.isEmpty()) {
            // no parameters then just return
            return buildUri(scheme, path, null);
        } else {
            // reorder parameters a..z
            List<String> keys = new ArrayList<String>(parameters.keySet());
            Collections.sort(keys);

            Map<String, Object> sorted = new LinkedHashMap<String, Object>(parameters.size());
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
