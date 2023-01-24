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

/**
 * A Camel specific URI parser that parses endpoint URIs in a quasi syntax that Camel uses.
 *
 * The {@link java.net.URI} is much slower and parses endpoint URIs into additional parts which Camel does not use or
 * need.
 */
public final class CamelURIParser {

    public static final String[] URI_ALREADY_NORMALIZED = new String[] {};

    private CamelURIParser() {
    }

    /**
     * Parses the URI (in fast mode).
     *
     * If this parser cannot parse the uri then <tt>null</tt> is returned. And instead the follow code can be used:
     *
     * <pre>
     * URI u = new URI(UnsafeUriCharactersEncoder.encode(uri, true));
     * </pre>
     *
     * @param  uri the uri
     *
     * @return     <tt>null</tt> if not possible to parse, if the uri is already normalized, then
     *             {@link #URI_ALREADY_NORMALIZED} is returned, or an array[3] with scheme,path,query
     */
    public static String[] fastParseUri(String uri) {
        return doParseUri(uri, true);
    }

    /**
     * Parses the URI.
     *
     * If this parser cannot parse the uri then <tt>null</tt> is returned. And instead the follow code can be used:
     *
     * <pre>
     * URI u = new URI(UnsafeUriCharactersEncoder.encode(uri, true));
     * </pre>
     *
     * @param  uri the uri
     *
     * @return     <tt>null</tt> if not possible to parse, or an array[3] with scheme,path,query
     */
    public static String[] parseUri(String uri) {
        return doParseUri(uri, false);
    }

    private static String[] doParseUri(String uri, boolean fastParse) {
        int schemeStart = 0;
        int schemeEnd = 0;
        int pathStart = 0;
        int pathEnd = 0;
        int queryStart = 0;

        int len = uri.length();
        for (int i = 0; i < len; i++) {
            char ch = uri.charAt(i);
            if (ch > 128) {
                // must be an ascii char
                return null;
            }
            // must be a safe char
            if (!UnsafeUriCharactersEncoder.isSafeFastParser(ch)) {
                return null;
            }
            if (schemeEnd == 0) {
                if (ch == ':') {
                    schemeEnd = i;
                    // skip colon
                    pathStart = i + 1;
                }
            } else if (pathEnd == 0) {
                if (ch == '?') {
                    pathEnd = i;
                    // skip ? marker
                    queryStart = i + 1;
                }
            }
        }

        if (pathStart == 0 && schemeEnd != 0) {
            // skip colon
            pathStart = schemeEnd + 1;
        }
        // invalid if there is no path anyway
        if (pathStart >= len) {
            return null;
        }

        String scheme = null;
        if (schemeEnd != 0) {

            // optimized if there are no query and the uri is already in camel style
            if (fastParse && queryStart == 0 && pathStart + 1 < len) {
                char ch = uri.charAt(schemeEnd);
                char ch2 = uri.charAt(pathStart);
                char ch3 = uri.charAt(pathStart + 1);
                if (ch == ':' && ch2 == '/' && ch3 == '/') {
                    return URI_ALREADY_NORMALIZED;
                }
            }

            scheme = uri.substring(schemeStart, schemeEnd);
        }
        if (scheme == null) {
            return null;
        }

        String path;
        // skip two leading slashes
        int next = pathStart + 1;
        if (uri.charAt(pathStart) == '/' && next < len && uri.charAt(next) == '/') {
            pathStart = pathStart + 2;
        }
        if (pathEnd != 0) {
            path = uri.substring(pathStart, pathEnd);
        } else {
            path = uri.substring(pathStart);
        }

        String query = null;
        if (queryStart != 0 && queryStart < len) {
            query = uri.substring(queryStart);
        }

        return new String[] { scheme, path, query };
    }
}
