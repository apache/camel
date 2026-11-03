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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

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

    public static final long DEFAULT_CACHE_REFRESH_INTERVAL_MS = 300_000; // 5 minutes
    public static final long DEFAULT_MIN_REFRESH_INTERVAL_MS = 10_000; // 10 seconds

    private final String serverUrl;
    private final String realm;
    private final ReentrantLock lock = new ReentrantLock();
    private final long cacheRefreshIntervalNanos;
    private final long minRefreshIntervalNanos;

    private volatile Map<String, PublicKey> keyCache = Map.of();
    private volatile long lastRefreshAttemptNanos;

    public KeycloakPublicKeyResolver(String serverUrl, String realm) {
        this(serverUrl, realm, DEFAULT_CACHE_REFRESH_INTERVAL_MS, DEFAULT_MIN_REFRESH_INTERVAL_MS);
    }

    public KeycloakPublicKeyResolver(String serverUrl, String realm, long cacheRefreshIntervalMs,
                                     long minRefreshIntervalMs) {
        this.serverUrl = serverUrl;
        this.realm = realm;
        this.cacheRefreshIntervalNanos = TimeUnit.MILLISECONDS.toNanos(cacheRefreshIntervalMs);
        this.minRefreshIntervalNanos = TimeUnit.MILLISECONDS.toNanos(minRefreshIntervalMs);
    }

    public long getCacheRefreshIntervalMs() {
        return TimeUnit.NANOSECONDS.toMillis(cacheRefreshIntervalNanos);
    }

    public long getMinRefreshIntervalMs() {
        return TimeUnit.NANOSECONDS.toMillis(minRefreshIntervalNanos);
    }

    /**
     * Gets the public key for verifying JWT signatures. Keys are cached and refreshed periodically.
     *
     * @param  kid         the key ID from the JWT header; the matching key is selected, and resolution fails closed
     *                     when a kid is given but no key matches (a {@code null} kid falls back to the first key)
     * @return             the public key
     * @throws IOException if fetching keys fails
     */
    public PublicKey getPublicKey(String kid) throws IOException {
        long elapsed = System.nanoTime() - lastRefreshAttemptNanos;
        long periodicInterval = keyCache.isEmpty() ? minRefreshIntervalNanos : cacheRefreshIntervalNanos;
        if (lastRefreshAttemptNanos == 0 || elapsed > periodicInterval) {
            refreshKeysIfStale(null, periodicInterval);
        }

        // If the kid is not known yet, refresh only if minimum cooldown has elapsed to prevent cache stampede
        if (kid != null && !keyCache.containsKey(kid)) {
            elapsed = System.nanoTime() - lastRefreshAttemptNanos;
            if (lastRefreshAttemptNanos == 0 || elapsed > minRefreshIntervalNanos) {
                refreshKeysIfStale(kid, minRefreshIntervalNanos);
            }
        }

        return selectKey(kid);
    }

    private void refreshKeysIfStale(String kid, long minIntervalNanos) throws IOException {
        lock.lock();
        try {
            long elapsed = System.nanoTime() - lastRefreshAttemptNanos;
            if ((kid != null && keyCache.containsKey(kid))
                    || (lastRefreshAttemptNanos != 0 && elapsed <= minIntervalNanos)) {
                return; // another thread refreshed (or attempted refresh) meanwhile
            }
            refreshKeys();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Selects the cached key matching the given kid. Fails closed (throws) when a kid is provided but no key matches,
     * so a token is never verified against the wrong key during key rotation. Falls back to the first available key
     * only when the token carries no kid.
     *
     * @param  kid         the key ID from the JWT header, or {@code null}
     * @return             the matching public key
     * @throws IOException if no matching key (or, for a {@code null} kid, no key at all) is available
     */
    PublicKey selectKey(String kid) throws IOException {
        Map<String, PublicKey> currentCache = keyCache;
        if (kid != null) {
            PublicKey key = currentCache.get(kid);
            if (key == null) {
                throw new IOException("No public key found in Keycloak JWKS for kid: " + kid);
            }
            return key;
        }

        // No kid in the token header - fall back to the first available key
        if (!currentCache.isEmpty()) {
            return currentCache.values().iterator().next();
        }

        throw new IOException("No public keys available from Keycloak JWKS endpoint");
    }

    /**
     * Refreshes the public keys from the JWKS endpoint.
     */
    public void refreshKeys() throws IOException {
        lock.lock();
        try {
            lastRefreshAttemptNanos = System.nanoTime();
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
                LOG.debug("Successfully loaded {} public keys from JWKS endpoint", keyCache.size());
            }
        } finally {
            lock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    void parseJwks(String jwksJson) throws IOException {
        Map<String, Object> jwks = OBJECT_MAPPER.readValue(jwksJson, Map.class);
        List<Map<String, Object>> keys = (List<Map<String, Object>>) jwks.get("keys");

        if (keys == null || keys.isEmpty()) {
            throw new IOException("No keys found in JWKS response");
        }

        Map<String, PublicKey> parsedKeys = new HashMap<>();
        for (Map<String, Object> keyData : keys) {
            String kty = (String) keyData.get("kty");
            String kid = (String) keyData.get("kid");
            String use = (String) keyData.get("use");

            // Only process RSA keys used for signatures
            if ("RSA".equals(kty) && (use == null || "sig".equals(use))) {
                try {
                    PublicKey publicKey = parseRsaPublicKey(keyData);
                    if (kid != null) {
                        parsedKeys.put(kid, publicKey);
                    }
                } catch (Exception e) {
                    LOG.warn("Failed to parse RSA key with kid '{}': {}", kid, e.getMessage());
                }
            }
        }

        if (parsedKeys.isEmpty()) {
            throw new IOException("No valid RSA signature keys found in JWKS response");
        }

        keyCache = Map.copyOf(parsedKeys);
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
        keyCache = Map.of();
        lastRefreshAttemptNanos = 0;
    }
}
