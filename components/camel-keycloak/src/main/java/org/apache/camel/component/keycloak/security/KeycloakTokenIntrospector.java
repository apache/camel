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
package org.apache.camel.component.keycloak.security;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.util.ObjectHelper;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Token introspection client for Keycloak OAuth 2.0 Token Introspection (RFC 7662). This class handles communication
 * with Keycloak's introspection endpoint to validate tokens in real-time, including detecting revoked tokens before
 * their expiration.
 */
public class KeycloakTokenIntrospector {
    private static final Logger LOG = LoggerFactory.getLogger(KeycloakTokenIntrospector.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String serverUrl;
    private final String realm;
    private final String clientId;
    private final String clientSecret;
    private final CloseableHttpClient httpClient;
    private final Map<String, CachedIntrospectionResult> cache;
    private final boolean cacheEnabled;
    private final long cacheTtlMillis;

    public KeycloakTokenIntrospector(
                                     String serverUrl, String realm, String clientId, String clientSecret,
                                     boolean cacheEnabled, long cacheTtlSeconds) {
        this.serverUrl = serverUrl;
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.httpClient = HttpClients.createDefault();
        this.cacheEnabled = cacheEnabled;
        this.cacheTtlMillis = cacheTtlSeconds * 1000;
        this.cache = cacheEnabled ? new ConcurrentHashMap<>() : null;
    }

    /**
     * Introspects a token by calling Keycloak's introspection endpoint.
     *
     * @param  token       the access token to introspect
     * @return             the introspection result
     * @throws IOException if the introspection request fails
     */
    public IntrospectionResult introspect(String token) throws IOException {
        if (ObjectHelper.isEmpty(token)) {
            throw new IllegalArgumentException("Token cannot be null or empty");
        }

        // Check cache first
        if (cacheEnabled) {
            CachedIntrospectionResult cached = cache.get(token);
            if (cached != null && !cached.isExpired()) {
                LOG.debug("Returning cached introspection result for token");
                return cached.result;
            } else if (cached != null) {
                cache.remove(token);
            }
        }

        // Build introspection endpoint URL
        String introspectionUrl = String.format("%s/realms/%s/protocol/openid-connect/token/introspect",
                serverUrl, realm);

        LOG.debug("Introspecting token at: {}", introspectionUrl);

        HttpPost request = new HttpPost(introspectionUrl);

        // Add Basic authentication header
        String auth = clientId + ":" + clientSecret;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
        request.setHeader("Authorization", "Basic " + encodedAuth);
        request.setHeader("Content-Type", "application/x-www-form-urlencoded");

        // Set request body with token
        NameValuePair tokenParam = new BasicNameValuePair("token", token);
        request.setEntity(new UrlEncodedFormEntity(java.util.List.of(tokenParam)));

        // Execute request
        try {
            IntrospectionResult result = httpClient.execute(request, response -> {
                return parseIntrospectionResponse(response);
            });

            // Cache the result
            if (cacheEnabled && result != null) {
                cache.put(token, new CachedIntrospectionResult(result, cacheTtlMillis));
                cleanupExpiredCacheEntries();
            }

            return result;
        } catch (Exception e) {
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException("Failed to introspect token", e);
        }
    }

    private IntrospectionResult parseIntrospectionResponse(ClassicHttpResponse response)
            throws IOException {
        try {
            int statusCode = response.getCode();

            if (statusCode != 200) {
                String errorBody = EntityUtils.toString(response.getEntity());
                LOG.error("Introspection request failed with status {}: {}", statusCode, errorBody);
                throw new IOException("Token introspection failed with status " + statusCode + ": " + errorBody);
            }

            String responseBody = EntityUtils.toString(response.getEntity());
            LOG.debug("Introspection response: {}", responseBody);

            Map<String, Object> responseMap = OBJECT_MAPPER.readValue(responseBody, Map.class);
            return new IntrospectionResult(responseMap);
        } catch (org.apache.hc.core5.http.ParseException e) {
            throw new IOException("Failed to parse HTTP response", e);
        }
    }

    private void cleanupExpiredCacheEntries() {
        if (!cacheEnabled || cache.isEmpty()) {
            return;
        }

        // Cleanup expired entries (max 100 at a time to avoid performance issues)
        cache.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired()) {
                LOG.trace("Removing expired cache entry");
                return true;
            }
            return false;
        });
    }

    /**
     * Clears the introspection cache.
     */
    public void clearCache() {
        if (cacheEnabled) {
            cache.clear();
            LOG.debug("Introspection cache cleared");
        }
    }

    /**
     * Closes the HTTP client.
     */
    public void close() {
        try {
            if (httpClient != null) {
                httpClient.close();
            }
        } catch (IOException e) {
            LOG.warn("Error closing HTTP client", e);
        }
    }

    /**
     * Represents the result of a token introspection request.
     */
    public static class IntrospectionResult {
        private final Map<String, Object> claims;

        public IntrospectionResult(Map<String, Object> claims) {
            this.claims = claims;
        }

        /**
         * Returns whether the token is active.
         *
         * @return true if the token is active, false otherwise
         */
        public boolean isActive() {
            Object active = claims.get("active");
            return active instanceof Boolean && (Boolean) active;
        }

        /**
         * Returns the subject (user ID) of the token.
         *
         * @return the subject
         */
        public String getSubject() {
            return (String) claims.get("sub");
        }

        /**
         * Returns the username from the token.
         *
         * @return the username
         */
        public String getUsername() {
            return (String) claims.get("username");
        }

        /**
         * Returns the scope of the token.
         *
         * @return the scope
         */
        public String getScope() {
            return (String) claims.get("scope");
        }

        /**
         * Returns the client ID that the token was issued to.
         *
         * @return the client ID
         */
        public String getClientId() {
            return (String) claims.get("client_id");
        }

        /**
         * Returns the token type.
         *
         * @return the token type
         */
        public String getTokenType() {
            return (String) claims.get("token_type");
        }

        /**
         * Returns the expiration time in seconds since epoch.
         *
         * @return the expiration time
         */
        public Long getExpiration() {
            Object exp = claims.get("exp");
            if (exp instanceof Number) {
                return ((Number) exp).longValue();
            }
            return null;
        }

        /**
         * Returns the issued at time in seconds since epoch.
         *
         * @return the issued at time
         */
        public Long getIssuedAt() {
            Object iat = claims.get("iat");
            if (iat instanceof Number) {
                return ((Number) iat).longValue();
            }
            return null;
        }

        /**
         * Returns all claims from the introspection response.
         *
         * @return map of all claims
         */
        public Map<String, Object> getAllClaims() {
            return claims;
        }

        /**
         * Returns a specific claim value.
         *
         * @param  claimName the claim name
         * @return           the claim value
         */
        public Object getClaim(String claimName) {
            return claims.get(claimName);
        }
    }

    /**
     * Internal class to hold cached introspection results with expiration.
     */
    private static class CachedIntrospectionResult {
        private final IntrospectionResult result;
        private final long expirationTime;

        public CachedIntrospectionResult(IntrospectionResult result, long ttlMillis) {
            this.result = result;
            this.expirationTime = System.currentTimeMillis() + ttlMillis;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() >= expirationTime;
        }
    }
}
