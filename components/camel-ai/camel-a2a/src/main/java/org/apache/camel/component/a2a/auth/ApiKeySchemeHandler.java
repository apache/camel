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
package org.apache.camel.component.a2a.auth;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.a2a.A2AConfiguration;
import org.apache.camel.component.a2a.model.SecurityScheme;

/**
 * Handler for API key authentication ({@code type=apiKey}). Supports placement in header, query, or cookie as specified
 * by the security scheme's {@code location} and {@code name} fields. Falls back to the configuration's
 * {@code apiKeyHeader} (default: "Authorization") when the scheme doesn't specify placement.
 */
public class ApiKeySchemeHandler implements A2ASecuritySchemeHandler {

    @Override
    public String schemeType() {
        return "apiKey";
    }

    @Override
    public void applyCredentials(Exchange exchange, SecurityScheme scheme, A2AConfiguration config, CamelContext context) {
        String apiKey = config.getApiKey();
        if (apiKey == null) {
            return;
        }

        String location = resolveLocation(scheme);
        String name = resolveName(scheme, config);
        if ("cookie".equals(location)) {
            exchange.getMessage().setHeader("Cookie", name + "=" + apiKey);
        } else if ("header".equals(location)) {
            if ("Authorization".equals(name)) {
                exchange.getMessage().setHeader(name, "Bearer " + apiKey);
            } else {
                exchange.getMessage().setHeader(name, apiKey);
            }
        }
    }

    @Override
    public A2AUserProfile validateCredentials(Exchange exchange, SecurityScheme scheme, A2AConfiguration config) {
        String location = resolveLocation(scheme);
        String name = resolveName(scheme, config);
        String providedKey = switch (location) {
            case "query" -> resolveQueryValue(exchange, name);
            case "cookie" -> resolveCookieValue(exchange, name);
            default -> exchange.getMessage().getHeader(name, String.class);
        };

        if (providedKey == null || providedKey.isEmpty()) {
            throw new SecurityException("Missing API key in " + location + ": " + name);
        }

        // Strip "Bearer " prefix if present (Authorization header convention)
        if (providedKey.startsWith("Bearer ")) {
            providedKey = providedKey.substring("Bearer ".length()).trim();
        }

        String expectedKey = config.getApiKey();
        if (expectedKey == null) {
            throw new SecurityException("No API key configured for validation");
        }

        if (!MessageDigest.isEqual(
                expectedKey.getBytes(StandardCharsets.UTF_8),
                providedKey.getBytes(StandardCharsets.UTF_8))) {
            throw new SecurityException("Invalid API key");
        }

        return A2AUserProfile.forScheme("apiKey");
    }

    private static String resolveLocation(SecurityScheme scheme) {
        if (scheme != null && scheme.getLocation() != null) {
            return scheme.getLocation();
        }
        return "header";
    }

    private static String resolveName(SecurityScheme scheme, A2AConfiguration config) {
        if (scheme != null && scheme.getName() != null) {
            return scheme.getName();
        }
        return config.getApiKeyHeader();
    }

    private static String resolveQueryValue(Exchange exchange, String name) {
        String query = exchange.getMessage().getHeader(Exchange.HTTP_QUERY, String.class);
        if (query == null || query.isEmpty()) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && name.equals(urlDecode(pair.substring(0, eq)))) {
                return urlDecode(pair.substring(eq + 1));
            }
        }
        return null;
    }

    private static String resolveCookieValue(Exchange exchange, String name) {
        String cookieHeader = exchange.getMessage().getHeader("Cookie", String.class);
        if (cookieHeader == null || cookieHeader.isEmpty()) {
            return null;
        }
        for (String cookie : cookieHeader.split(";")) {
            String trimmed = cookie.trim();
            int eq = trimmed.indexOf('=');
            if (eq > 0 && name.equals(trimmed.substring(0, eq))) {
                return trimmed.substring(eq + 1);
            }
        }
        return null;
    }

    private static String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
