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
package org.apache.camel.component.mcp.server.vertx;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Package-private immutable RFC 9728 metadata for the Camel Main HTTP MCP protected resource.
 * <p>
 * The public resource identifier is explicit configuration. It is never derived from request {@code Host} or
 * {@code X-Forwarded-*} headers, which are supplied by the caller unless a separately trusted proxy contract exists.
 */
final class McpOAuthProtectedResourceMetadata {

    private static final String WELL_KNOWN_PREFIX = "/.well-known/oauth-protected-resource";

    private final String resource;
    private final String authorizationServer;
    private final String metadataPath;
    private final String metadataUri;
    private final List<String> scopesSupported;

    private McpOAuthProtectedResourceMetadata(
                                              String resource, String authorizationServer, String metadataPath,
                                              String metadataUri,
                                              List<String> scopesSupported) {
        this.resource = resource;
        this.authorizationServer = authorizationServer;
        this.metadataPath = metadataPath;
        this.metadataUri = metadataUri;
        this.scopesSupported = List.copyOf(scopesSupported);
    }

    /**
     * Creates and validates the metadata for a resource. The resource URI must be an absolute HTTPS URI with no
     * user-info, query, or fragment, and its path must equal the configured MCP endpoint path.
     */
    static McpOAuthProtectedResourceMetadata create(
            String resourceUri, String mcpPath, String authorizationServerIssuer, List<String> scopesSupported) {
        return create(resourceUri, mcpPath, authorizationServerIssuer, scopesSupported, false);
    }

    /**
     * Creates metadata allowing an HTTP authorization-server issuer only when the OAuth profile explicitly enables
     * allow-insecure-http (for local development/test IdPs). The public resource URI remains HTTPS.
     */
    static McpOAuthProtectedResourceMetadata create(
            String resourceUri, String mcpPath, String authorizationServerIssuer, List<String> scopesSupported,
            boolean allowInsecureAuthorizationServer) {
        URI resource = parseHttpsUri(resourceUri, "camel.server.mcp-resource-uri");
        URI issuer = parseHttpsUri(authorizationServerIssuer, "OAuth profile issuer", allowInsecureAuthorizationServer);

        if (mcpPath == null || mcpPath.isBlank() || !mcpPath.startsWith("/")
                || mcpPath.indexOf('?') >= 0 || mcpPath.indexOf('#') >= 0 || mcpPath.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("MCP HTTP path must be an absolute path without query or fragment");
        }

        String resourcePath = resource.getRawPath();
        if (resourcePath == null || resourcePath.isEmpty()) {
            resourcePath = "/";
        }
        if (!resourcePath.equals(mcpPath)) {
            throw new IllegalArgumentException(
                    "camel.server.mcp-resource-uri path must match camel.server.mcp-path");
        }

        // RFC 9728 inserts the well-known path between the authority and the resource path.
        String metadataPath = WELL_KNOWN_PREFIX + ("/".equals(resourcePath) ? "" : resourcePath);
        String metadataUri = resource.getScheme() + "://" + resource.getRawAuthority() + metadataPath;

        return new McpOAuthProtectedResourceMetadata(
                resource.toASCIIString(), issuer.toASCIIString(), metadataPath, metadataUri,
                scopesSupported != null ? scopesSupported : List.of());
    }

    private static URI parseHttpsUri(String value, String label) {
        return parseHttpsUri(value, label, false);
    }

    private static URI parseHttpsUri(String value, String label, boolean allowInsecureHttp) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be configured as an absolute HTTPS URI");
        }
        try {
            URI uri = new URI(value.trim());
            boolean https = "https".equalsIgnoreCase(uri.getScheme());
            boolean permittedHttp = allowInsecureHttp && "http".equalsIgnoreCase(uri.getScheme());
            if (!(https || permittedHttp) || uri.getHost() == null
                    || uri.getRawAuthority() == null || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || uri.getPort() > 65535) {
                String requiredScheme = allowInsecureHttp ? "HTTP or HTTPS" : "HTTPS";
                throw new IllegalArgumentException(
                        label + " must be an absolute " + requiredScheme
                                                   + " URI without user-info, query, or fragment");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(label + " must be a valid absolute HTTPS URI");
        }
    }

    /** Returns the local GET route path for this metadata document. */
    String metadataPath() {
        return metadataPath;
    }

    /** Returns the absolute HTTPS metadata URL used in the Bearer challenge. */
    String metadataUri() {
        return metadataUri;
    }

    /** Returns the required scopes that are advertised and enforced by the transport. */
    List<String> scopesSupported() {
        return scopesSupported;
    }

    /**
     * Returns a JSON-ready RFC 9728 metadata object. Parameters with no values are omitted as required by RFC 9728.
     */
    Map<String, Object> asMap() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("resource", resource);
        metadata.put("authorization_servers", List.of(authorizationServer));
        metadata.put("bearer_methods_supported", List.of("header"));
        if (!scopesSupported.isEmpty()) {
            metadata.put("scopes_supported", scopesSupported);
        }
        return Collections.unmodifiableMap(metadata);
    }

    /**
     * Adds RFC 9728 resource discovery and the configured scope request to an existing Bearer challenge. Existing OAuth
     * challenge parameters (notably {@code error="invalid_token"}) are preserved.
     */
    String addToWwwAuthenticate(String challenge) {
        String value = challenge == null || challenge.isBlank() ? "Bearer" : challenge;
        StringBuilder result = new StringBuilder(value);
        if ("Bearer".equalsIgnoreCase(value.trim())) {
            result.append(' ');
        } else {
            result.append(", ");
        }
        result.append("resource_metadata=\"").append(metadataUri).append('"');
        if (!scopesSupported.isEmpty()) {
            result.append(", scope=\"").append(String.join(" ", scopesSupported)).append('"');
        }
        return result.toString();
    }
}
