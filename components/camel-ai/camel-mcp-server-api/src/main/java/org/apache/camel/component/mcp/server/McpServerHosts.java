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
 * Parses and normalizes the hosts allowed in the {@code Host} header of a request to the MCP endpoint, and the
 * {@code Host} header itself, so that both are compared in the same form: the host in lower case and the port without
 * leading zeros.
 * <p>
 * An allowed host is {@code host} or {@code host:port}, where the port may be {@code *} to match any port, and also a
 * {@code Host} header without a port. An IPv6 address is written in brackets, such as {@code [::1]:*}. Anything else,
 * such as {@code *}, an entry with a scheme or a path, is refused with an {@link IllegalArgumentException}, so a
 * mistake shows at startup instead of as an error for every request.
 *
 * @since 4.24
 */
public final class McpServerHosts {

    private static final String ANY_PORT = "*";
    private static final Pattern HOST = Pattern.compile("[a-zA-Z0-9._-]+|\\[[0-9a-fA-F:.]+]");
    private static final Pattern PORT = Pattern.compile("\\d{1,5}");

    private McpServerHosts() {
    }

    /**
     * Splits a comma-separated list of allowed hosts, skipping empty entries, and normalizes each entry with
     * {@link #normalizeAllowedHost(String)}.
     *
     * @param  allowedHosts             the comma-separated list
     * @return                          the normalized hosts, or {@code null} when not set or without any entry, so the
     *                                  {@code Host} header is not checked
     * @throws IllegalArgumentException when an entry is not a valid host
     */
    public static List<String> parseAllowedHosts(String allowedHosts) {
        if (allowedHosts == null || allowedHosts.isBlank()) {
            return null;
        }
        List<String> answer = new ArrayList<>();
        for (String entry : allowedHosts.split(",")) {
            if (!entry.isBlank()) {
                answer.add(normalizeAllowedHost(entry));
            }
        }
        return answer.isEmpty() ? null : answer;
    }

    /**
     * Normalizes an allowed host: trims it, lower-cases the host and drops the leading zeros of the port.
     *
     * @param  allowedHost              a host such as {@code MCP.Example.com}, {@code localhost:8080} or
     *                                  {@code localhost:*}
     * @return                          the normalized host, such as {@code mcp.example.com}
     * @throws IllegalArgumentException when the entry is {@code *} or not of the form {@code host[:port]}
     */
    public static String normalizeAllowedHost(String allowedHost) {
        String value = allowedHost == null ? "" : allowedHost.trim();
        if (ANY_PORT.equals(value)) {
            throw new IllegalArgumentException(
                    "MCP allowed host * is not supported: leave the allowed hosts option unset to not check the Host"
                                               + " header, or list each host, such as mcp.example.com or localhost:* (any port).");
        }
        String answer = normalize(value, true);
        if (answer == null) {
            throw new IllegalArgumentException(
                    "Invalid MCP allowed host '" + allowedHost + "': expected host or host:port, without a scheme,"
                                               + " where the port may be * for any port, such as mcp.example.com or"
                                               + " localhost:*");
        }
        return answer;
    }

    /**
     * Normalizes the value of a {@code Host} header the same way as the allowed hosts, so that both compare as strings.
     *
     * @param  host the header value
     * @return      the normalized host, or the value as is when it is not of the form {@code host[:port]} (such as
     *              {@code null}), so it matches no allowed host
     */
    public static String normalizeHost(String host) {
        if (host == null) {
            return null;
        }
        String answer = normalize(host.trim(), false);
        return answer != null ? answer : host;
    }

    private static String normalize(String value, boolean allowAnyPort) {
        String host = value;
        String port = null;
        int colon = value.lastIndexOf(':');
        if (colon >= 0 && colon > value.lastIndexOf(']')) {
            host = value.substring(0, colon);
            port = value.substring(colon + 1);
        }
        if (!HOST.matcher(host).matches()) {
            return null;
        }
        host = host.toLowerCase(Locale.ROOT);
        if (port != null) {
            if (ANY_PORT.equals(port)) {
                if (!allowAnyPort) {
                    return null;
                }
            } else if (PORT.matcher(port).matches() && Integer.parseInt(port) <= 65535) {
                port = String.valueOf(Integer.parseInt(port));
            } else {
                return null;
            }
        }
        return port != null ? host + ":" + port : host;
    }
}
