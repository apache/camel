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
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves and caches public keys from Keycloak's JWKS endpoint for JWT signature verification.
 */
public class KeycloakPublicKeyResolver {
    private static final Logger LOG = LoggerFactory.getLogger(KeycloakPublicKeyResolver.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String serverUrl;
    private final String realm;
    private final Map<String, PublicKey> keyCache = new ConcurrentHashMap<>();
    private volatile long lastRefreshTime = 0;
    private static final long CACHE_REFRESH_INTERVAL_MS = 300_000; // 5 minutes

    public KeycloakPublicKeyResolver(String serverUrl, String realm) {
        this.serverUrl = serverUrl;
        this.realm = realm;
    }

    /**
     * Gets the public key for verifying JWT signatures. Keys are cached and refreshed periodically.
     *
     * @param  kid         the key ID from the JWT header (optional, uses first key if null)
     * @return             the public key
     * @throws IOException if fetching keys fails
     */
    public PublicKey getPublicKey(String kid) throws IOException {
        // Check if we need to refresh the cache
        long now = System.currentTimeMillis();
        if (keyCache.isEmpty() || (now - lastRefreshTime) > CACHE_REFRESH_INTERVAL_MS) {
            refreshKeys();
        }

        if (kid != null && keyCache.containsKey(kid)) {
            return keyCache.get(kid);
        }

        // If no kid specified or not found, return the first available key
        if (!keyCache.isEmpty()) {
            return keyCache.values().iterator().next();
        }

        throw new IOException("No public keys available from Keycloak JWKS endpoint");
    }

    /**
     * Refreshes the public keys from the JWKS endpoint.
     */
    public synchronized void refreshKeys() throws IOException {
        String jwksUrl = String.format("%s/realms/%s/protocol/openid-connect/certs", serverUrl, realm);
        LOG.debug("Fetching public keys from: {}", jwksUrl);

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(jwksUrl);

            String responseBody = httpClient.execute(request, response -> {
                int statusCode = response.getCode();
                if (statusCode != 200) {
                    throw new IOException("Failed to fetch JWKS: HTTP " + statusCode);
                }
                return EntityUtils.toString(response.getEntity());
            });

            parseJwks(responseBody);
            lastRefreshTime = System.currentTimeMillis();
            LOG.debug("Successfully loaded {} public keys from JWKS endpoint", keyCache.size());
        }
    }

    @SuppressWarnings("unchecked")
    private void parseJwks(String jwksJson) throws IOException {
        Map<String, Object> jwks = OBJECT_MAPPER.readValue(jwksJson, Map.class);
        List<Map<String, Object>> keys = (List<Map<String, Object>>) jwks.get("keys");

        if (keys == null || keys.isEmpty()) {
            throw new IOException("No keys found in JWKS response");
        }

        keyCache.clear();
        for (Map<String, Object> keyData : keys) {
            String kty = (String) keyData.get("kty");
            String kid = (String) keyData.get("kid");
            String use = (String) keyData.get("use");

            // Only process RSA keys used for signatures
            if ("RSA".equals(kty) && (use == null || "sig".equals(use))) {
                try {
                    PublicKey publicKey = parseRsaPublicKey(keyData);
                    if (kid != null) {
                        keyCache.put(kid, publicKey);
                    }
                } catch (Exception e) {
                    LOG.warn("Failed to parse RSA key with kid '{}': {}", kid, e.getMessage());
                }
            }
        }

        if (keyCache.isEmpty()) {
            throw new IOException("No valid RSA signature keys found in JWKS response");
        }
    }

    private PublicKey parseRsaPublicKey(Map<String, Object> keyData)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        String n = (String) keyData.get("n");
        String e = (String) keyData.get("e");

        if (n == null || e == null) {
            throw new IllegalArgumentException("RSA key missing n or e component");
        }

        BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(n));
        BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(e));

        RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(spec);
    }

    /**
     * Returns the expected issuer URL for this realm.
     *
     * @return the issuer URL
     */
    public String getExpectedIssuer() {
        return serverUrl + "/realms/" + realm;
    }

    /**
     * Clears the key cache.
     */
    public void clearCache() {
        keyCache.clear();
        lastRefreshTime = 0;
    }
}
