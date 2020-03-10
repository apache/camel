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

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * URI utilities.
 */
public final class URISupport {

    public static final String RAW_TOKEN_PREFIX = "RAW";
    public static final char[] RAW_TOKEN_START = {'(', '{'};
    public static final char[] RAW_TOKEN_END = {')', '}'};

    // Match any key-value pair in the URI query string whose key contains
    // "passphrase" or "password" or secret key (case-insensitive).
    // First capture group is the key, second is the value.
    private static final Pattern SECRETS = Pattern.compile("([?&][^=]*(?:passphrase|password|secretKey|accessToken|clientSecret|authorizationToken|saslJaasConfig)[^=]*)=(RAW[({].*[)}]|[^&]*)", Pattern.CASE_INSENSITIVE);

    // Match the user password in the URI as second capture group
    // (applies to URI with authority component and userinfo token in the form
    // "user:password").
    private static final Pattern USERINFO_PASSWORD = Pattern.compile("(.*://.*?:)(.*)(@)");

    // Match the user password in the URI path as second capture group
    // (applies to URI path with authority component and userinfo token in the
    // form "user:password").
    private static final Pattern PATH_USERINFO_PASSWORD = Pattern.compile("(.*?:)(.*)(@)");

    private static final String CHARSET = "UTF-8";

    private URISupport() {
        // Helper class
    }

    /**
     * Removes detected sensitive information (such as passwords) from the URI
     * and returns the result.
     *
     * @param uri The uri to sanitize.
     * @see #SECRETS and #USERINFO_PASSWORD for the matched pattern
     * @return Returns null if the uri is null, otherwise the URI with the
     *         passphrase, password or secretKey sanitized.
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
     * Extracts the scheme specific path from the URI that is used as the
     * remainder option when creating endpoints.
     *
     * @param u the URI
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
     * The URI parameters will by default be URI encoded. However you can define
     * a parameter values with the syntax: <tt>key=RAW(value)</tt> which tells
     * Camel to not encode the value, and use the value as is (eg key=value) and
     * the value has <b>not</b> been encoded.
     *
     * @param uri the uri
     * @return the parameters, or an empty map if no parameters (eg never null)
     * @throws URISyntaxException is thrown if uri has invalid syntax.
     * @see #RAW_TOKEN_PREFIX
     * @see #RAW_TOKEN_START
     * @see #RAW_TOKEN_END
     */
    public static Map<String, Object> parseQuery(String uri) throws URISyntaxException {
        return parseQuery(uri, false);
    }

    /**
     * Parses the query part of the uri (eg the parameters).
     * <p/>
     * The URI parameters will by default be URI encoded. However you can define
     * a parameter values with the syntax: <tt>key=RAW(value)</tt> which tells
     * Camel to not encode the value, and use the value as is (eg key=value) and
     * the value has <b>not</b> been encoded.
     *
     * @param uri the uri
     * @param useRaw whether to force using raw values
     * @return the parameters, or an empty map if no parameters (eg never null)
     * @throws URISyntaxException is thrown if uri has invalid syntax.
     * @see #RAW_TOKEN_PREFIX
     * @see #RAW_TOKEN_START
     * @see #RAW_TOKEN_END
     */
    public static Map<String, Object> parseQuery(String uri, boolean useRaw) throws URISyntaxException {
        return parseQuery(uri, useRaw, false);
    }

    /**
     * Parses the query part of the uri (eg the parameters).
     * <p/>
     * The URI parameters will by default be URI encoded. However you can define
     * a parameter values with the syntax: <tt>key=RAW(value)</tt> which tells
     * Camel to not encode the value, and use the value as is (eg key=value) and
     * the value has <b>not</b> been encoded.
     *
     * @param uri the uri
     * @param useRaw whether to force using raw values
     * @param lenient whether to parse lenient and ignore trailing & markers
     *            which has no key or value which can happen when using HTTP
     *            components
     * @return the parameters, or an empty map if no parameters (eg never null)
     * @throws URISyntaxException is thrown if uri has invalid syntax.
     * @see #RAW_TOKEN_PREFIX
     * @see #RAW_TOKEN_START
     * @see #RAW_TOKEN_END
     */
    public static Map<String, Object> parseQuery(String uri, boolean useRaw, boolean lenient) throws URISyntaxException {
        if (uri == null || uri.isEmpty()) {
            // return an empty map
            return new LinkedHashMap<>(0);
        }

        // must check for trailing & as the uri.split("&") will ignore those
        if (!lenient && uri.endsWith("&")) {
            throw new URISyntaxException(uri, "Invalid uri syntax: Trailing & marker found. " + "Check the uri and remove the trailing & marker.");
        }

        URIScanner scanner = new URIScanner();
        return scanner.parseQuery(uri, useRaw);
    }

    /**
     * Scans RAW tokens in the string and returns the list of pair indexes which
     * tell where a RAW token starts and ends in the string.
     * <p/>
     * This is a companion method with {@link #isRaw(int, List)} and the
     * returned value is supposed to be used as the parameter of that method.
     *
     * @param str the string to scan RAW tokens
     * @return the list of pair indexes which represent the start and end
     *         positions of a RAW token
     * @see #isRaw(int, List)
     * @see #RAW_TOKEN_PREFIX
     * @see #RAW_TOKEN_START
     * @see #RAW_TOKEN_END
     */
    public static List<Pair<Integer>> scanRaw(String str) {
        return URIScanner.scanRaw(str);
    }

    /**
     * Tests if the index is within any pair of the start and end indexes which
     * represent the start and end positions of a RAW token.
     * <p/>
     * This is a companion method with {@link #scanRaw(String)} and is supposed
     * to consume the returned value of that method as the second parameter
     * <tt>pairs</tt>.
     *
     * @param index the index to be tested
     * @param pairs the list of pair indexes which represent the start and end
     *            positions of a RAW token
     * @return <tt>true</tt> if the index is within any pair of the indexes,
     *         <tt>false</tt> otherwise
     * @see #scanRaw(String)
     * @see #RAW_TOKEN_PREFIX
     * @see #RAW_TOKEN_START
     * @see #RAW_TOKEN_END
     */
    public static boolean isRaw(int index, List<Pair<Integer>> pairs) {
        if (pairs == null || pairs.isEmpty()) {
            return false;
        }

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

    /**
     * Parses the query parameters of the uri (eg the query part).
     *
     * @param uri the uri
     * @return the parameters, or an empty map if no parameters (eg never null)
     * @throws URISyntaxException is thrown if uri has invalid syntax.
     */
    public static Map<String, Object> parseParameters(URI uri) throws URISyntaxException {
        String query = prepareQuery(uri);
        if (query == null) {
            // empty an empty map
            return new LinkedHashMap<>(0);
        }
        return parseQuery(query);
    }

    public static String prepareQuery(URI uri) {
        String query = uri.getQuery();
        if (query == null) {
            String schemeSpecificPart = uri.getSchemeSpecificPart();
            int idx = schemeSpecificPart.indexOf('?');
            if (idx < 0) {
                return null;
            } else {
                query = schemeSpecificPart.substring(idx + 1);
            }
        } else if (query.indexOf('?') == 0) {
            // skip leading query
            query = query.substring(1);
        }
        return query;
    }



    /**
     * Traverses the given parameters, and resolve any parameter values which
     * uses the RAW token syntax: <tt>key=RAW(value)</tt>. This method will then
     * remove the RAW tokens, and replace the content of the value, with just
     * the value.
     *
     * @param parameters the uri parameters
     * @see #parseQuery(String)
     * @see #RAW_TOKEN_PREFIX
     * @see #RAW_TOKEN_START
     * @see #RAW_TOKEN_END
     */
    @SuppressWarnings("unchecked")
    public static void resolveRawParameterValues(Map<String, Object> parameters) {
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            // if the value is a list then we need to iterate
            Object value = entry.getValue();
            if (value instanceof List) {
                List list = (List)value;
                for (int i = 0; i < list.size(); i++) {
                    Object obj = list.get(i);
                    if (obj == null) {
                        continue;
                    }
                    String str = obj.toString();
                    String raw = URIScanner.resolveRaw(str);
                    if (raw != null) {
                        // update the string in the list
                        list.set(i, raw);
                    }
                }
            } else {
                String str = entry.getValue().toString();
                String raw = URIScanner.resolveRaw(str);
                if (raw != null) {
                    entry.setValue(raw);
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

        // assemble string as new uri and replace parameters with the query
        // instead
        String s = uri.toString();
        String before = StringHelper.before(s, "?");
        if (before == null) {
            before = StringHelper.before(s, "#");
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
     * @param value the value
     * @param prefix the prefix to remove from value
     * @return the value without the prefix
     */
    public static String stripPrefix(String value, String prefix) {
        if (value == null || prefix == null) {
            return value;
        }

        if (value.startsWith(prefix)) {
            return value.substring(prefix.length());
        }

        return value;
    }

    /**
     * Strips the suffix from the value.
     * <p/>
     * Returns the value as-is if not ending with the prefix.
     *
     * @param value the value
     * @param suffix the suffix to remove from value
     * @return the value without the suffix
     */
    public static String stripSuffix(final String value, final String suffix) {
        if (value == null || suffix == null) {
            return value;
        }

        if (value.endsWith(suffix)) {
            return value.substring(0, value.length() - suffix.length());
        }

        return value;
    }

    /**
     * Assembles a query from the given map.
     *
     * @param options the map with the options (eg key/value pairs)
     * @return a query string with <tt>key1=value&key2=value2&...</tt>, or an
     *         empty string if there is no options.
     * @throws URISyntaxException is thrown if uri has invalid syntax.
     */
    @SuppressWarnings("unchecked")
    public static String createQueryString(Map<String, Object> options) throws URISyntaxException {
        return createQueryString(options.keySet(), options);
    }

    public static String createQueryString(Collection<String> sortedKeys, Map<String, Object> options) throws URISyntaxException {
        try {
            if (options.size() > 0) {
                StringBuilder rc = new StringBuilder();
                boolean first = true;
                for (Object o : sortedKeys) {
                    if (first) {
                        first = false;
                    } else {
                        rc.append("&");
                    }

                    String key = (String)o;
                    Object value = options.get(key);

                    // the value may be a list since the same key has multiple
                    // values
                    if (value instanceof List) {
                        List<String> list = (List<String>)value;
                        for (Iterator<String> it = list.iterator(); it.hasNext();) {
                            String s = it.next();
                            appendQueryStringParameter(key, s, rc);
                            // append & separator if there is more in the list
                            // to append
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
        if (value == null) {
            return;
        }
        // only append if value is not null
        rc.append("=");
        String raw = URIScanner.resolveRaw(value);
        if (raw != null) {
            // do not encode RAW parameters unless it has %
            // need to replace % with %25 to avoid losing "%" when decoding
            String s = StringHelper.replaceAll(value, "%", "%25");
            rc.append(s);
        } else {
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
     * Appends the given parameters to the given URI.
     * <p/>
     * It keeps the original parameters and if a new parameter is already
     * defined in {@code originalURI}, it will be replaced by its value in
     * {@code newParameters}.
     *
     * @param originalURI the original URI
     * @param newParameters the parameters to add
     * @return the URI with all the parameters
     * @throws URISyntaxException is thrown if the uri syntax is invalid
     * @throws UnsupportedEncodingException is thrown if encoding error
     */
    public static String appendParametersToURI(String originalURI, Map<String, Object> newParameters) throws URISyntaxException, UnsupportedEncodingException {
        URI uri = new URI(normalizeUri(originalURI));
        Map<String, Object> parameters = parseParameters(uri);
        parameters.putAll(newParameters);
        return createRemainingURI(uri, parameters).toString();
    }

    /**
     * Normalizes the uri by reordering the parameters so they are sorted and
     * thus we can use the uris for endpoint matching.
     * <p/>
     * The URI parameters will by default be URI encoded. However you can define
     * a parameter values with the syntax: <tt>key=RAW(value)</tt> which tells
     * Camel to not encode the value, and use the value as is (eg key=value) and
     * the value has <b>not</b> been encoded.
     *
     * @param uri the uri
     * @return the normalized uri
     * @throws URISyntaxException in thrown if the uri syntax is invalid
     * @throws UnsupportedEncodingException is thrown if encoding error
     * @see #RAW_TOKEN_PREFIX
     * @see #RAW_TOKEN_START
     * @see #RAW_TOKEN_END
     */
    public static String normalizeUri(String uri) throws URISyntaxException, UnsupportedEncodingException {
        // try to parse using the simpler and faster Camel URI parser
        String[] parts = CamelURIParser.parseUri(uri);
        if (parts != null) {
            // use the faster and more simple normalizer
            return doFastNormalizeUri(parts);
        } else {
            // use the legacy normalizer as the uri is complex and may have unsafe URL characters
            return doComplexNormalizeUri(uri);
        }
    }

    /**
     * The complex (and Camel 2.x) compatible URI normalizer when the URI is more complex
     * such as having percent encoded values, or other unsafe URL characters, or have authority user/password, etc.
     */
    private static String doComplexNormalizeUri(String uri) throws URISyntaxException {
        URI u = new URI(UnsafeUriCharactersEncoder.encode(uri, true));
        String scheme = u.getScheme();
        String path = u.getSchemeSpecificPart();

        // not possible to normalize
        if (scheme == null || path == null) {
            return uri;
        }

        // find start and end position in path as we only check the context-path and not the query parameters
        int start = path.startsWith("//") ? 2 : 0;
        int end = path.indexOf('?');
        if (start == 0 && end == 0 || start == 2 && end == 2) {
            // special when there is no context path
            path = "";
        } else {
            if (start != 0 && end == -1) {
                path = path.substring(start);
            } else if (end != -1) {
                path = path.substring(start, end);
            }
            if (scheme.startsWith("http")) {
                path = UnsafeUriCharactersEncoder.encodeHttpURI(path);
            } else {
                path = UnsafeUriCharactersEncoder.encode(path);
            }
        }

        // okay if we have user info in the path and they use @ in username or password,
        // then we need to encode them (but leave the last @ sign before the hostname)
        // this is needed as Camel end users may not encode their user info properly,
        // but expect this to work out of the box with Camel, and hence we need to
        // fix it for them
        int idxPath = path.indexOf('/');
        if (StringHelper.countChar(path, '@', idxPath) > 1) {
            String userInfoPath = idxPath > 0 ? path.substring(0, idxPath) : path;
            int max = userInfoPath.lastIndexOf('@');
            String before = userInfoPath.substring(0, max);
            // after must be from original path
            String after = path.substring(max);

            // replace the @ with %40
            before = StringHelper.replaceAll(before, "@", "%40");
            path = before + after;
        }

        // in case there are parameters we should reorder them
        String query = prepareQuery(u);
        if (query == null) {
            // no parameters then just return
            return buildUri(scheme, path, null);
        } else {
            Map<String, Object> parameters = URISupport.parseQuery(query, false, false);
            if (parameters.size() == 1) {
                // only 1 parameter need to create new query string
                query = URISupport.createQueryString(parameters);
                return buildUri(scheme, path, query);
            } else {
                // reorder parameters a..z
                List<String> keys = new ArrayList<>(parameters.keySet());
                keys.sort(null);

                // build uri object with sorted parameters
                query = URISupport.createQueryString(keys, parameters);
                return buildUri(scheme, path, query);
            }
        }
    }

    /**
     * The fast parser for normalizing Camel endpoint URIs when the URI is not complex and
     * can be parsed in a much more efficient way.
     */
    private static String doFastNormalizeUri(String[] parts) throws URISyntaxException {
        String scheme = parts[0];
        String path = parts[1];
        String query = parts[2];

        // in case there are parameters we should reorder them
        if (query == null) {
            // no parameters then just return
            return buildUri(scheme, path, null);
        } else {
            Map<String, Object> parameters = null;
            if (query.indexOf('&') != -1) {
                // only parse if there is parameters
                parameters = URISupport.parseQuery(query, false, false);
            }
            if (parameters == null || parameters.size() == 1) {
                return buildUri(scheme, path, query);
            } else {
                // reorder parameters a..z
                // optimize and only build new query if the keys was resorted
                boolean sort = false;
                String prev = null;
                for (String key : parameters.keySet()) {
                    if (prev == null) {
                        prev = key;
                    } else {
                        int comp = key.compareTo(prev);
                        if (comp < 0) {
                            sort = true;
                            break;
                        }
                    }
                }
                if (sort) {
                    List<String> keys = new ArrayList<>(parameters.keySet());
                    keys.sort(null);
                    // rebuild query with sorted parameters
                    query = URISupport.createQueryString(keys, parameters);
                }

                return buildUri(scheme, path, query);
            }
        }
    }

    private static String buildUri(String scheme, String path, String query) {
        // must include :// to do a correct URI all components can work with
        int len = scheme.length() + 3 + path.length();
        if (query != null) {
            len += 1 + query.length();
            StringBuilder sb = new StringBuilder(len);
            sb.append(scheme).append("://").append(path).append('?').append(query);
            return sb.toString();
        } else {
            StringBuilder sb = new StringBuilder(len);
            sb.append(scheme).append("://").append(path);
            return sb.toString();
        }
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

    public static String joinPaths(final String... paths) {
        if (paths == null || paths.length == 0) {
            return "";
        }

        final StringBuilder joined = new StringBuilder();

        boolean addedLast = false;
        for (int i = paths.length - 1; i >= 0; i--) {
            String path = paths[i];
            if (ObjectHelper.isNotEmpty(path)) {
                if (addedLast) {
                    path = stripSuffix(path, "/");
                }

                addedLast = true;

                if (path.charAt(0) == '/') {
                    joined.insert(0, path);
                } else {
                    if (i > 0) {
                        joined.insert(0, '/').insert(1, path);
                    } else {
                        joined.insert(0, path);
                    }
                }
            }
        }

        return joined.toString();
    }
}
