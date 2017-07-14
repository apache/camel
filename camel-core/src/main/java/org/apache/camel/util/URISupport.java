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

    public static final String RAW_TOKEN_START = "RAW(";
    public static final String RAW_TOKEN_END = ")";

    // Match any key-value pair in the URI query string whose key contains
    // "passphrase" or "password" or secret key (case-insensitive).
    // First capture group is the key, second is the value.
    private static final Pattern SECRETS = Pattern.compile("([?&][^=]*(?:passphrase|password|secretKey)[^=]*)=(RAW\\(.*\\)|[^&]*)",
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
     *
     * @param uri The uri to sanitize.
     * @see #SECRETS and #USERINFO_PASSWORD for the matched pattern
     *
     * @return Returns null if the uri is null, otherwise the URI with the passphrase, password or secretKey sanitized.
     */
    public static String sanitizeUri(String uri) {
        // use xxxxx as replacement as that works well with JMX also
        String sanitized = uri;
        if (uri != null) {
            sanitized = SECRETS.matcher(sanitized).replaceAll("$1=xxxxxx");
            sanitized = USERINFO_PASSWORD.matcher(sanitized).replaceFirst("$1xxxxxx$3");
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
            sanitized = PATH_USERINFO_PASSWORD.matcher(sanitized).replaceFirst("$1xxxxxx$3");
        }
        return sanitized;
    }

    /**
     * Extracts the scheme specific path from the URI that is used as the remainder option when creating endpoints.
     *
     * @param u      the URI
     * @param useRaw whether to force using raw values
     * @return the remainder path
     */
    public static String extractRemainderPath(URI u, boolean useRaw) {
        String path = useRaw ? u.getRawSchemeSpecificPart() : u.getSchemeSpecificPart();

        // lets trim off any query arguments
        if (path.startsWith("//")) {
            path = path.substring(2);
        }
        int idx = path.indexOf('?');
        if (idx > -1) {
            path = path.substring(0, idx);
        }

        return path;
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
        return parseQuery(uri, useRaw, false);
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
     * @param lenient whether to parse lenient and ignore trailing & markers which has no key or value which can happen when using HTTP components
     * @return the parameters, or an empty map if no parameters (eg never null)
     * @throws URISyntaxException is thrown if uri has invalid syntax.
     * @see #RAW_TOKEN_START
     * @see #RAW_TOKEN_END
     */
    public static Map<String, Object> parseQuery(String uri, boolean useRaw, boolean lenient) throws URISyntaxException {
        // must check for trailing & as the uri.split("&") will ignore those
        if (!lenient) {
            if (uri != null && uri.endsWith("&")) {
                throw new URISyntaxException(uri, "Invalid uri syntax: Trailing & marker found. "
                        + "Check the uri and remove the trailing & marker.");
            }
        }

        if (uri == null || ObjectHelper.isEmpty(uri)) {
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
                    addParameter(key.toString(), value.toString(), rc, useRaw || isRaw);
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
                addParameter(key.toString(), value.toString(), rc, useRaw || isRaw);
            }

            return rc;

        } catch (UnsupportedEncodingException e) {
            URISyntaxException se = new URISyntaxException(e.toString(), "Invalid encoding");
            se.initCause(e);
            throw se;
        }
    }

    private static void addParameter(String name, String value, Map<String, Object> map, boolean isRaw) throws UnsupportedEncodingException {
        name = URLDecoder.decode(name, CHARSET);
        if (!isRaw) {
            // need to replace % with %25
            String s = StringHelper.replaceAll(value, "%", "%25");
            value = URLDecoder.decode(s, CHARSET);
        }

        // does the key already exist?
        if (map.containsKey(name)) {
            // yes it does, so make sure we can support multiple values, but using a list
            // to hold the multiple values
            Object existing = map.get(name);
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
            map.put(name, list);
        } else {
            map.put(name, value);
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
     * Traverses the given parameters, and resolve any parameter values which uses the RAW token
     * syntax: <tt>key=RAW(value)</tt>. This method will then remove the RAW tokens, and replace
     * the content of the value, with just the value.
     *
     * @param parameters the uri parameters
     * @see #parseQuery(String)
     * @see #RAW_TOKEN_START
     * @see #RAW_TOKEN_END
     */
    @SuppressWarnings("unchecked")
    public static void resolveRawParameterValues(Map<String, Object> parameters) {
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            if (entry.getValue() != null) {
                // if the value is a list then we need to iterate
                Object value = entry.getValue();
                if (value instanceof List) {
                    List list = (List) value;
                    for (int i = 0; i < list.size(); i++) {
                        Object obj = list.get(i);
                        if (obj != null) {
                            String str = obj.toString();
                            if (str.startsWith(RAW_TOKEN_START) && str.endsWith(RAW_TOKEN_END)) {
                                str = str.substring(4, str.length() - 1);
                                // update the string in the list
                                list.set(i, str);
                            }
                        }
                    }
                } else {
                    String str = entry.getValue().toString();
                    if (str.startsWith(RAW_TOKEN_START) && str.endsWith(RAW_TOKEN_END)) {
                        str = str.substring(4, str.length() - 1);
                        entry.setValue(str);
                    }
                }
            }
        }
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
        if (before == null) {
            before = ObjectHelper.before(s, "#");
        }
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
        if (value != null && value.startsWith(prefix)) {
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
            if (value.startsWith(RAW_TOKEN_START) && value.endsWith(RAW_TOKEN_END)) {
                // do not encode RAW parameters unless it has %
                // need to replace % with %25 to avoid losing "%" when decoding
                String s = StringHelper.replaceAll(value, "%", "%25");
                rc.append(s);
            } else {
                rc.append(URLEncoder.encode(value, CHARSET));
            }
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
     * Appends the given parameters to the given URI.
     * <p/>
     * It keeps the original parameters and if a new parameter is already defined in
     * {@code originalURI}, it will be replaced by its value in {@code newParameters}.
     *
     * @param originalURI   the original URI
     * @param newParameters the parameters to add
     * @return the URI with all the parameters
     * @throws URISyntaxException           is thrown if the uri syntax is invalid
     * @throws UnsupportedEncodingException is thrown if encoding error
     */
    public static String appendParametersToURI(String originalURI, Map<String, Object> newParameters) throws URISyntaxException, UnsupportedEncodingException {
        URI uri = new URI(normalizeUri(originalURI));
        Map<String, Object> parameters = parseParameters(uri);
        parameters.putAll(newParameters);
        return createRemainingURI(uri, parameters).toString();
    }

    /**
     * Normalizes the uri by reordering the parameters so they are sorted and thus
     * we can use the uris for endpoint matching.
     * <p/>
     * The URI parameters will by default be URI encoded. However you can define a parameter
     * values with the syntax: <tt>key=RAW(value)</tt> which tells Camel to not encode the value,
     * and use the value as is (eg key=value) and the value has <b>not</b> been encoded.
     *
     * @param uri the uri
     * @return the normalized uri
     * @throws URISyntaxException in thrown if the uri syntax is invalid
     * @throws UnsupportedEncodingException is thrown if encoding error
     * @see #RAW_TOKEN_START
     * @see #RAW_TOKEN_END
     */
    public static String normalizeUri(String uri) throws URISyntaxException, UnsupportedEncodingException {

        URI u = new URI(UnsafeUriCharactersEncoder.encode(uri, true));
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

        if (u.getScheme().startsWith("http")) {
            path = UnsafeUriCharactersEncoder.encodeHttpURI(path);
        } else {
            path = UnsafeUriCharactersEncoder.encode(path);
        }

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
            keys.sort(null);

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

    public static String pathAndQueryOf(final URI uri) {
        final String path = uri.getPath();

        String pathAndQuery = path;
        if (ObjectHelper.isEmpty(path)) {
            pathAndQuery = "/";
        }

        final String query = uri.getQuery();
        if (ObjectHelper.isNotEmpty(query)) {
            pathAndQuery += "?" + query;
        }

        return pathAndQuery;
    }
}
