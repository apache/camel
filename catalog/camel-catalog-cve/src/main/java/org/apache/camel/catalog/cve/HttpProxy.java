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
package org.apache.camel.catalog.cve;

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The HTTP proxy to reach OSV.dev through, such as the proxy of the Maven settings or of the {@code HTTPS_PROXY}
 * environment variable, with the optional username and password of the proxy and the hosts to reach without it.
 *
 * @param host          the host of the proxy
 * @param port          the port of the proxy
 * @param username      the username to log in to the proxy, or null
 * @param password      the password to log in to the proxy, or null
 * @param nonProxyHosts the hosts to connect to directly: {@code *} for all, {@code *.corp.com} or {@code .corp.com} for
 *                      the hosts of a domain, and {@code corp.com} for that host and the hosts of its domain
 */
public record HttpProxy(String host, int port, String username, String password, List<String> nonProxyHosts) {

    /** The property of the JDK that lists the schemes it does not use to log in to a proxy for HTTPS. */
    public static final String TUNNELING_DISABLED_SCHEMES = "jdk.http.auth.tunneling.disabledSchemes";

    public HttpProxy {
        nonProxyHosts = nonProxyHosts != null ? List.copyOf(nonProxyHosts) : List.of();
    }

    public HttpProxy(String host, int port) {
        this(host, port, null, null, List.of());
    }

    /**
     * The proxy of the {@code HTTPS_PROXY} environment variable (else {@code https_proxy}, {@code HTTP_PROXY} or
     * {@code http_proxy}), with the hosts of {@code NO_PROXY} (or {@code no_proxy}), as CI runners set them. Null when
     * none is set.
     */
    public static HttpProxy fromEnvironment(Map<String, String> env) {
        String url = firstOf(env, "HTTPS_PROXY", "https_proxy", "HTTP_PROXY", "http_proxy");
        return parse(url, firstOf(env, "NO_PROXY", "no_proxy"));
    }

    /**
     * A proxy given as a URL, such as {@code http://proxy.corp.com:3128} or {@code http://user:secret@proxy:8080} (port
     * 80 by default, and the username and password URL encoded), and the hosts to reach without it, separated by commas
     * or {@code |}. Null when the URL is blank or has no host.
     */
    public static HttpProxy parse(String url, String nonProxyHosts) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String text = url.trim();
        if (!text.contains("://")) {
            text = "http://" + text;
        }
        URI uri;
        try {
            uri = URI.create(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (uri.getHost() == null) {
            return null;
        }
        String username = null;
        String password = null;
        String userInfo = uri.getRawUserInfo();
        if (userInfo != null && !userInfo.isEmpty()) {
            int colon = userInfo.indexOf(':');
            username = decode(colon < 0 ? userInfo : userInfo.substring(0, colon));
            password = colon < 0 ? null : decode(userInfo.substring(colon + 1));
        }
        int port = uri.getPort() > 0 ? uri.getPort() : 80;
        return new HttpProxy(uri.getHost(), port, username, password, splitHosts(nonProxyHosts));
    }

    /** The hosts of a list separated by commas or {@code |}, as NO_PROXY and the Maven settings write them. */
    public static List<String> splitHosts(String hosts) {
        List<String> answer = new ArrayList<>();
        if (hosts != null) {
            for (String h : hosts.split("[,|]")) {
                String host = h.trim();
                if (!host.isEmpty()) {
                    answer.add(host.toLowerCase(Locale.ROOT));
                }
            }
        }
        return answer;
    }

    /** Whether the host is reached directly, without the proxy. */
    public boolean bypasses(String targetHost) {
        if (targetHost == null) {
            return false;
        }
        String target = targetHost.toLowerCase(Locale.ROOT);
        for (String h : nonProxyHosts) {
            if ("*".equals(h)) {
                return true;
            }
            if (h.startsWith("*.") || h.startsWith(".")) {
                String domain = h.substring(h.indexOf('.'));
                if (target.endsWith(domain) || target.equals(domain.substring(1))) {
                    return true;
                }
            } else if (target.equals(h) || target.endsWith("." + h)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the proxy needs a login. */
    public boolean hasCredentials() {
        return username != null && !username.isEmpty();
    }

    /** The proxy selector for an HTTP client: this proxy, except for the hosts to reach directly. */
    public ProxySelector selector() {
        Proxy proxy = new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(host, port));
        return new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                return List.of(bypasses(uri.getHost()) ? Proxy.NO_PROXY : proxy);
            }

            @Override
            public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                // nothing to remember: the proxy is the only route
            }
        };
    }

    /**
     * The authenticator for an HTTP client that logs in to this proxy, and answers no server; null when the proxy needs
     * no login. Calls {@link #allowBasicTunneling()}, so an HTTP client built with it can log in with Basic
     * authentication when it tunnels HTTPS: get the authenticator before the client is built.
     */
    public Authenticator authenticator() {
        if (!hasCredentials()) {
            return null;
        }
        allowBasicTunneling();
        char[] secret = password != null ? password.toCharArray() : new char[0];
        return new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                if (getRequestorType() == RequestorType.PROXY && host.equalsIgnoreCase(getRequestingHost())
                        && port == getRequestingPort()) {
                    return new PasswordAuthentication(username, secret.clone());
                }
                return null;
            }
        };
    }

    /**
     * Lets the HTTP client of the JDK log in to a proxy with Basic authentication when it tunnels HTTPS through it,
     * which the JDK disables by default ({@value #TUNNELING_DISABLED_SCHEMES}{@code =Basic}). The JDK reads the
     * property once, when the first HTTP client is used, so this must be called before that; a value set by the user is
     * kept.
     */
    public static synchronized void allowBasicTunneling() {
        if (System.getProperty(TUNNELING_DISABLED_SCHEMES) == null) {
            System.setProperty(TUNNELING_DISABLED_SCHEMES, "");
        }
    }

    /** The proxy as {@code host:port}, without the password. */
    @Override
    public String toString() {
        return (hasCredentials() ? username + "@" : "") + host + ":" + port;
    }

    private static String firstOf(Map<String, String> env, String... names) {
        for (String name : names) {
            String value = env.get(name);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String decode(String text) {
        // percent-decoding only: a + in the user info is a +
        return URLDecoder.decode(text.replace("+", "%2B"), StandardCharsets.UTF_8);
    }
}
