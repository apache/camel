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
package org.apache.camel.component.vertx.mqtt;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * The broker of an endpoint, read from its broker URL.
 *
 * @param host the host name or address, an IPv6 address without brackets
 * @param port the port
 * @param ssl  whether the connection is TLS
 */
record BrokerAddress(String host, int port, boolean ssl) {

    static final int DEFAULT_PORT = 1883;
    static final int DEFAULT_TLS_PORT = 8883;

    /**
     * @param  url                      a broker URL such as {@code tcp://broker:1883} or {@code ssl://broker:8883}
     * @return                          the broker it names
     * @throws IllegalArgumentException if it is no broker URL of a supported scheme
     */
    static BrokerAddress parse(final String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("The broker URL is required");
        }
        final URI uri;
        try {
            uri = new URI(url.strip());
        } catch (final URISyntaxException e) {
            throw new IllegalArgumentException("Not a broker URL: %s".formatted(url), e);
        }
        final String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        final boolean ssl = switch (scheme) {
            case "tcp", "mqtt" -> false;
            case "ssl", "mqtts" -> true;
            case "ws", "wss" -> throw new IllegalArgumentException(
                    "The WebSocket transport (%s) is not supported yet: %s".formatted(scheme, url));
            default -> throw new IllegalArgumentException(
                    "The broker URL %s has no supported scheme, one of tcp, mqtt, ssl or mqtts".formatted(url));
        };
        // Credentials belong into the username and password options, never into a URL that ends up in logs
        if (uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException(
                    "The broker URL must not carry user information, use the username and password options: "
                                               + uri.getHost());
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("The broker URL has no host: %s".formatted(url));
        }
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        final String path = uri.getRawPath();
        if (path != null && !path.isEmpty() && !"/".equals(path) || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("The broker URL must not have a path, query or fragment: %s".formatted(url));
        }
        final int port = uri.getPort() >= 0 ? uri.getPort() : ssl ? DEFAULT_TLS_PORT : DEFAULT_PORT;
        return new BrokerAddress(host, port, ssl);
    }

    @Override
    public String toString() {
        return (ssl ? "ssl://" : "tcp://") + (host.indexOf(':') >= 0 ? "[" + host + "]" : host) + ":" + port;
    }
}
