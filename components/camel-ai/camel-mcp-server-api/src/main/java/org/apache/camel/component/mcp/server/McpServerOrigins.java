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
package org.apache.camel.component.mcp.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Parses and normalizes the origins allowed to call the MCP endpoint, and the {@code Origin} header of a request, so
 * that both are compared in the form a browser sends: scheme and host in lower case, and no default port ({@code :80}
 * for {@code http}, {@code :443} for {@code https}).
 * <p>
 * An allowed origin is {@code scheme://host} or {@code scheme://host:port}, where the port may be {@code *} to match
 * any port. Anything else, such as {@code *}, an entry with a path or an entry without a scheme, can never match an
 * {@code Origin} header and is refused with an {@link IllegalArgumentException}, so a mistake shows at startup instead
 * of as HTTP 403 for every browser request.
 *
 * @since 4.24
 */
public final class McpServerOrigins {

    private static final String SCHEME_SEPARATOR = "://";
    private static final String ANY_PORT = "*";
    private static final Pattern SCHEME = Pattern.compile("[a-zA-Z][a-zA-Z0-9+.-]*");
    private static final Pattern HOST = Pattern.compile("[a-zA-Z0-9._-]+|\\[[0-9a-fA-F:.]+]");
    private static final Pattern PORT = Pattern.compile("\\d{1,5}");

    private McpServerOrigins() {
    }

    /**
     * Splits a comma-separated list of allowed origins, skipping empty entries, and normalizes each entry with
     * {@link #normalizeAllowedOrigin(String)}.
     *
     * @param  allowedOrigins           the comma-separated list
     * @return                          the normalized origins, or {@code null} when not set, so the engine applies its
     *                                  default (loopback origins only)
     * @throws IllegalArgumentException when an entry is not a valid origin
     */
    public static List<String> parseAllowedOrigins(String allowedOrigins) {
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            return null;
        }
        List<String> answer = new ArrayList<>();
        for (String entry : allowedOrigins.split(",")) {
            if (!entry.isBlank()) {
                answer.add(normalizeAllowedOrigin(entry));
            }
        }
        return answer;
    }

    /**
     * Normalizes an allowed origin: trims it, drops a trailing {@code /}, lower-cases the scheme and host, and drops a
     * default port.
     *
     * @param  allowedOrigin            an origin such as {@code https://App.Example.com:443} or
     *                                  {@code http://localhost:*}
     * @return                          the normalized origin, such as {@code https://app.example.com}
     * @throws IllegalArgumentException when the entry is {@code *} or not of the form {@code scheme://host[:port]}
     */
    public static String normalizeAllowedOrigin(String allowedOrigin) {
        String value = allowedOrigin == null ? "" : allowedOrigin.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (ANY_PORT.equals(value)) {
            throw new IllegalArgumentException(
                    "MCP allowed origin * is not supported: the Origin check cannot be turned off. List each origin"
                                               + " instead, such as https://app.example.com or http://localhost:* (any port)."
                                               + " A request without an Origin header, as sent by MCP clients that are not"
                                               + " browsers, is always served.");
        }
        String answer = normalize(value, true);
        if (answer == null) {
            throw new IllegalArgumentException(
                    "Invalid MCP allowed origin '" + allowedOrigin + "': expected scheme://host or scheme://host:port,"
                                               + " where the port may be * for any port, such as https://app.example.com or"
                                               + " http://localhost:*");
        }
        return answer;
    }

    /**
     * Normalizes the value of an {@code Origin} header the same way as the allowed origins, so that both compare as
     * strings.
     *
     * @param  origin the header value
     * @return        the normalized origin, or the value as is when it is not of the form {@code scheme://host[:port]}
     *                (such as {@code null}), so it matches no allowed origin
     */
    public static String normalizeOrigin(String origin) {
        if (origin == null) {
            return null;
        }
        String answer = normalize(origin.trim(), false);
        return answer != null ? answer : origin;
    }

    private static String normalize(String value, boolean allowAnyPort) {
        int separator = value.indexOf(SCHEME_SEPARATOR);
        if (separator <= 0) {
            return null;
        }
        String scheme = value.substring(0, separator);
        String authority = value.substring(separator + SCHEME_SEPARATOR.length());
        String host = authority;
        String port = null;
        int colon = authority.lastIndexOf(':');
        if (colon >= 0 && colon > authority.lastIndexOf(']')) {
            host = authority.substring(0, colon);
            port = authority.substring(colon + 1);
        }
        if (!SCHEME.matcher(scheme).matches() || !HOST.matcher(host).matches()) {
            return null;
        }
        scheme = scheme.toLowerCase(Locale.ROOT);
        host = host.toLowerCase(Locale.ROOT);
        if (port != null) {
            if (ANY_PORT.equals(port)) {
                if (!allowAnyPort) {
                    return null;
                }
            } else if (PORT.matcher(port).matches() && Integer.parseInt(port) <= 65535) {
                port = String.valueOf(Integer.parseInt(port));
                if ("http".equals(scheme) && "80".equals(port) || "https".equals(scheme) && "443".equals(port)) {
                    port = null;
                }
            } else {
                return null;
            }
        }
        return port != null ? scheme + SCHEME_SEPARATOR + host + ":" + port : scheme + SCHEME_SEPARATOR + host;
    }
}
