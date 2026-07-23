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
package org.apache.camel.oauth;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jose.util.Resource;
import com.nimbusds.jwt.SignedJWT;
import org.apache.camel.spi.OAuthTokenValidationConfig;
import org.apache.camel.spi.OAuthTokenValidationFactory;
import org.apache.camel.spi.OAuthTokenValidationResult;
import org.apache.camel.spi.OAuthTokenValidationResult.ErrorCode;
import org.apache.camel.spi.annotations.JdkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of {@link OAuthTokenValidationFactory} that validates incoming bearer tokens.
 * <p/>
 * JWT tokens are validated locally via JWKS. Opaque tokens are validated via RFC 7662 introspection.
 */
@JdkService(OAuthTokenValidationFactory.FACTORY)
public class DefaultOAuthTokenValidationFactory implements OAuthTokenValidationFactory {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultOAuthTokenValidationFactory.class);
    private static final int MAX_DISCOVERY_SIZE_BYTES = 64 * 1024;
    private static final long MIN_DISCOVERY_RETRY_INTERVAL_MILLIS = 30_000L;
    private static final ConcurrentMap<String, DiscoveryCacheEntry> DISCOVERY_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, FailureRecord> DISCOVERY_FAILURES = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Object> DISCOVERY_LOCKS = new ConcurrentHashMap<>();
    private static volatile LongSupplier currentTimeMillis = System::currentTimeMillis;

    @Override
    public OAuthTokenValidationResult validateToken(OAuthTokenValidationConfig config, String token) {
        OAuthTokenValidationConfig resolved = resolveAndValidate(config);

        boolean hasJwksEndpoint = resolved.getJwksEndpoint() != null;
        boolean hasIntrospectionEndpoint = resolved.getIntrospectionEndpoint() != null;
        if (hasJwksEndpoint && (!hasIntrospectionEndpoint || isSignedJwt(token))) {
            return JwtTokenValidator.validate(
                    token,
                    resolved.getJwksEndpoint(),
                    resolved.getJwksCacheTtlSeconds(),
                    resolved.getExpectedIssuer(),
                    resolved.getExpectedAudiences(),
                    resolved.getClockSkewSeconds(),
                    resolved.isRequireExpiration(),
                    resolved.getConnectTimeoutSeconds() * 1000,
                    resolved.getReadTimeoutSeconds() * 1000,
                    resolved.getAllowedJwsAlgorithms(),
                    resolved.getExpectedTokenType());
        }

        return introspectToken(resolved, token);
    }

    @Override
    public void validateConfiguration(OAuthTokenValidationConfig config) {
        resolveAndValidate(config);
    }

    private OAuthTokenValidationConfig resolveAndValidate(OAuthTokenValidationConfig config) {
        OAuthTokenValidationConfig resolved = maybeDiscoverEndpoints(config);
        validateResolvedConfiguration(resolved);
        return resolved;
    }

    private OAuthTokenValidationConfig maybeDiscoverEndpoints(OAuthTokenValidationConfig config) {
        String discoveryUrl = config.getOidcDiscoveryUrl();
        if (discoveryUrl == null) {
            return config;
        }

        boolean needsJwks = config.getJwksEndpoint() == null;
        boolean canUseDiscoveredIntrospection = config.getIntrospectionClientId() != null
                && config.getIntrospectionClientSecret() != null;
        boolean needsIntrospection = canUseDiscoveredIntrospection && config.getIntrospectionEndpoint() == null;
        boolean needsIssuer = config.getExpectedIssuer() == null;

        if (!needsJwks && !needsIntrospection && !needsIssuer) {
            return config;
        }

        validateHttps(discoveryUrl, "OIDC discovery URL", config.isAllowInsecureHttp());
        JsonObject json = discoverEndpoints(config, discoveryUrl);
        validateDiscoveryIssuer(config, discoveryUrl, json);

        OAuthTokenValidationConfig resolved = config.copy();

        if (config.getJwksEndpoint() != null) {
            resolved.setJwksEndpoint(config.getJwksEndpoint());
        } else if (json.has("jwks_uri")) {
            resolved.setJwksEndpoint(json.get("jwks_uri").getAsString());
        }

        if (config.getIntrospectionEndpoint() != null) {
            resolved.setIntrospectionEndpoint(config.getIntrospectionEndpoint());
        } else if (json.has("introspection_endpoint") && canUseDiscoveredIntrospection) {
            resolved.setIntrospectionEndpoint(json.get("introspection_endpoint").getAsString());
        }

        if (config.getExpectedIssuer() != null) {
            resolved.setExpectedIssuer(config.getExpectedIssuer());
        } else if (json.has("issuer")) {
            resolved.setExpectedIssuer(json.get("issuer").getAsString());
        }

        if (!config.getExpectedAudiences().isEmpty()) {
            resolved.setExpectedAudiences(config.getExpectedAudiences());
        }
        if (config.getIntrospectionClientId() != null) {
            resolved.setIntrospectionClientId(config.getIntrospectionClientId());
        }
        if (config.getIntrospectionClientSecret() != null) {
            resolved.setIntrospectionClientSecret(config.getIntrospectionClientSecret());
        }

        return resolved;
    }

    private static void validateResolvedConfiguration(OAuthTokenValidationConfig config) {
        boolean hasJwksEndpoint = config.getJwksEndpoint() != null;
        boolean hasIntrospectionEndpoint = config.getIntrospectionEndpoint() != null;

        if (!hasJwksEndpoint && !hasIntrospectionEndpoint) {
            throw new IllegalArgumentException(
                    "No jwks-endpoint, introspection-endpoint, or base-uri configured for token validation");
        }
        if (hasIntrospectionEndpoint
                && (config.getIntrospectionClientId() == null || config.getIntrospectionClientSecret() == null)) {
            throw new IllegalArgumentException("Introspection requires client-id and client-secret");
        }
        if (!config.isAllowMissingAudience() && config.getExpectedAudiences().isEmpty()) {
            throw new IllegalArgumentException(
                    "Token validation requires expected-audience; set allow-missing-audience=true to opt out");
        }
        if (!config.isAllowMissingIssuer() && config.getExpectedIssuer() == null) {
            throw new IllegalArgumentException(
                    "Token validation requires expected-issuer or base-uri; set allow-missing-issuer=true to opt out");
        }
        validateHttps(config.getJwksEndpoint(), "JWKS endpoint", config.isAllowInsecureHttp());
        validateHttps(config.getIntrospectionEndpoint(), "Introspection endpoint", config.isAllowInsecureHttp());
        validateHttps(config.getOidcDiscoveryUrl(), "OIDC discovery URL", config.isAllowInsecureHttp());
    }

    private OAuthTokenValidationResult introspectToken(OAuthTokenValidationConfig config, String token) {
        JsonObject claimsJson = OAuthTokenRequest.introspect(
                config.getIntrospectionEndpoint(),
                config.getIntrospectionClientId(),
                config.getIntrospectionClientSecret(),
                token,
                config.getConnectTimeoutSeconds(),
                config.getReadTimeoutSeconds());

        if (!readRequiredBoolean(claimsJson, "active")) {
            return OAuthTokenValidationResult.invalid(ErrorCode.INACTIVE_TOKEN, "Token is not active");
        }

        OAuthTokenValidationResult temporalResult
                = checkIntrospectionTemporalClaims(claimsJson, config.getClockSkewSeconds());
        if (temporalResult != null) {
            return temporalResult;
        }

        String subject = readOptionalString(claimsJson, "sub");
        String issuer = readOptionalString(claimsJson, "iss");

        List<String> audience = Collections.emptyList();
        if (claimsJson.has("aud")) {
            audience = readStringOrStringArray(claimsJson, "aud");
        }

        List<String> scopes = readScope(claimsJson);

        long expiresAt = readOptionalNumericDate(claimsJson, "exp", 0);

        Map<String, Object> claims = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : claimsJson.entrySet()) {
            claims.put(entry.getKey(), toClaimValue(entry.getValue()));
        }

        if (config.getExpectedIssuer() != null && !config.getExpectedIssuer().equals(issuer)) {
            return OAuthTokenValidationResult.invalid(ErrorCode.INVALID_ISSUER,
                    "Invalid issuer: expected " + config.getExpectedIssuer() + ", got " + issuer);
        }

        if (!config.getExpectedAudiences().isEmpty()
                && !containsAny(audience, config.getExpectedAudiences())) {
            return OAuthTokenValidationResult.invalid(ErrorCode.INVALID_AUDIENCE,
                    "Invalid audience: expected to contain one of " + config.getExpectedAudiences());
        }

        LOG.debug("Introspection validated successfully: sub={}, iss={}, exp={}", subject, issuer, expiresAt);
        return OAuthTokenValidationResult.valid(subject, issuer, audience, scopes, claims, expiresAt);
    }

    static boolean isSignedJwt(String token) {
        try {
            SignedJWT.parse(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static boolean isJwt(String token) {
        int firstDot = token.indexOf('.');
        if (firstDot < 0) {
            return false;
        }
        int secondDot = token.indexOf('.', firstDot + 1);
        if (secondDot < 0) {
            return false;
        }
        return token.indexOf('.', secondDot + 1) < 0;
    }

    private static void validateHttps(String url, String label, boolean allowInsecureHttp) {
        if (url == null) {
            return;
        }

        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(label + " must be a valid URI", e);
        }

        String scheme = uri.getScheme();
        if ("https".equalsIgnoreCase(scheme)) {
            return;
        }
        if ("http".equalsIgnoreCase(scheme) && allowInsecureHttp) {
            return;
        }
        if ("http".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException(
                    label + " must use HTTPS; set allow-insecure-http=true only for local development");
        }
        throw new IllegalArgumentException(label + " must use HTTP or HTTPS");
    }

    private static JsonObject discoverEndpoints(OAuthTokenValidationConfig config, String discoveryUrl) {
        long now = now();
        DiscoveryCacheEntry cached = DISCOVERY_CACHE.get(discoveryUrl);
        if (cached != null && !cached.isExpired(config.getOidcDiscoveryCacheTtlSeconds(), now)) {
            return cached.json;
        }

        Object lock = DISCOVERY_LOCKS.computeIfAbsent(discoveryUrl, key -> new Object());
        synchronized (lock) {
            now = now();
            cached = DISCOVERY_CACHE.get(discoveryUrl);
            if (cached != null && !cached.isExpired(config.getOidcDiscoveryCacheTtlSeconds(), now)) {
                return cached.json;
            }

            FailureRecord failure = DISCOVERY_FAILURES.get(discoveryUrl);
            if (failure != null && now - failure.failedAtMillis < MIN_DISCOVERY_RETRY_INTERVAL_MILLIS) {
                throw new OAuthException(
                        "OIDC discovery from " + discoveryUrl + " was attempted recently", failure.cause);
            }

            try {
                LOG.debug("Discovering OIDC endpoints from {}", discoveryUrl);
                DefaultResourceRetriever retriever = new DefaultResourceRetriever(
                        config.getConnectTimeoutSeconds() * 1000,
                        config.getReadTimeoutSeconds() * 1000,
                        MAX_DISCOVERY_SIZE_BYTES);
                Resource resource = retriever.retrieveResource(URI.create(discoveryUrl).toURL());
                JsonObject json = JsonParser.parseString(resource.getContent()).getAsJsonObject();
                DISCOVERY_FAILURES.remove(discoveryUrl);
                DISCOVERY_CACHE.put(discoveryUrl, new DiscoveryCacheEntry(json, now));
                return json;
            } catch (Exception e) {
                DISCOVERY_FAILURES.put(discoveryUrl, new FailureRecord(now, e));
                throw new OAuthException("Failed to discover OIDC endpoints from " + discoveryUrl, e);
            }
        }
    }

    static void clearDiscoveryCache() {
        DISCOVERY_CACHE.clear();
        DISCOVERY_FAILURES.clear();
        DISCOVERY_LOCKS.clear();
        currentTimeMillis = System::currentTimeMillis;
    }

    static void setCurrentTimeMillisSupplier(LongSupplier currentTimeMillis) {
        DefaultOAuthTokenValidationFactory.currentTimeMillis
                = Objects.requireNonNull(currentTimeMillis, "currentTimeMillis");
    }

    private static long now() {
        return currentTimeMillis.getAsLong();
    }

    static OAuthTokenValidationResult checkIntrospectionTemporalClaims(
            JsonObject json, int clockSkewSeconds) {
        long now = now() / 1000L;

        if (json.has("exp")) {
            long exp = readOptionalNumericDate(json, "exp", 0);
            if (now > exp + clockSkewSeconds) {
                return OAuthTokenValidationResult.invalid(
                        ErrorCode.EXPIRED_TOKEN, "Token expired per introspection exp claim");
            }
        }

        if (json.has("nbf")) {
            long nbf = readOptionalNumericDate(json, "nbf", 0);
            if (now < nbf - clockSkewSeconds) {
                return OAuthTokenValidationResult.invalid(
                        ErrorCode.NOT_YET_VALID, "Token not yet valid per introspection nbf claim");
            }
        }

        return null;
    }

    private static long readOptionalNumericDate(JsonObject json, String claimName, long defaultValue) {
        if (!json.has(claimName)) {
            return defaultValue;
        }
        JsonElement value = json.get(claimName);
        if (!isNumber(value)) {
            throw new OAuthException("Introspection response " + claimName + " field must be a JSON number");
        }
        return value.getAsLong();
    }

    private static boolean readRequiredBoolean(JsonObject json, String claimName) {
        if (!json.has(claimName)) {
            throw new OAuthException("Introspection response " + claimName + " field is required");
        }
        JsonElement value = json.get(claimName);
        if (!isBoolean(value)) {
            throw new OAuthException("Introspection response " + claimName + " field must be a JSON boolean");
        }
        return value.getAsBoolean();
    }

    private static String readOptionalString(JsonObject json, String claimName) {
        if (!json.has(claimName) || json.get(claimName).isJsonNull()) {
            return null;
        }
        JsonElement value = json.get(claimName);
        if (!isString(value)) {
            throw new OAuthException("Introspection response " + claimName + " field must be a JSON string");
        }
        return value.getAsString();
    }

    private static List<String> readScope(JsonObject json) {
        String scope = readOptionalString(json, "scope");
        if (scope == null || scope.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(scope.trim().split("\\s+")).toList();
    }

    private static List<String> readStringOrStringArray(JsonObject json, String claimName) {
        JsonElement value = json.get(claimName);
        if (isString(value)) {
            return Collections.singletonList(value.getAsString());
        }
        if (!value.isJsonArray()) {
            throw new OAuthException("Introspection response " + claimName + " field must be a string or string array");
        }

        List<String> strings = new ArrayList<>();
        for (JsonElement item : value.getAsJsonArray()) {
            if (!isString(item)) {
                throw new OAuthException(
                        "Introspection response " + claimName + " field must be a string or string array");
            }
            strings.add(item.getAsString());
        }
        return strings;
    }

    private static boolean containsAny(List<String> actual, Set<String> expected) {
        for (String value : expected) {
            if (actual.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private static void validateDiscoveryIssuer(
            OAuthTokenValidationConfig config, String discoveryUrl, JsonObject json) {
        String discoveredIssuer = readDiscoveryString(json, "issuer");
        if (discoveredIssuer == null) {
            throw new OAuthException("OIDC discovery response issuer field is required");
        }

        String expectedIssuer = config.getExpectedIssuer();
        if (expectedIssuer != null) {
            if (!expectedIssuer.equals(discoveredIssuer)) {
                throw new OAuthException(
                        "OIDC discovery issuer mismatch: expected " + expectedIssuer + ", got " + discoveredIssuer);
            }
            return;
        }

        String issuerFromUrl = issuerFromDiscoveryUrl(discoveryUrl);
        if (issuerFromUrl == null) {
            throw new OAuthException(
                    "OIDC discovery issuer cannot be validated from non-standard discovery URL "
                                     + discoveryUrl
                                     + "; configure expectedIssuer or use the standard /.well-known/openid-configuration URL");
        }
        if (!issuerFromUrl.equals(discoveredIssuer)) {
            throw new OAuthException(
                    "OIDC discovery issuer mismatch: expected " + issuerFromUrl + ", got " + discoveredIssuer);
        }
    }

    private static String readDiscoveryString(JsonObject json, String fieldName) {
        if (!json.has(fieldName) || json.get(fieldName).isJsonNull()) {
            return null;
        }
        JsonElement value = json.get(fieldName);
        if (!isString(value)) {
            throw new OAuthException("OIDC discovery response " + fieldName + " field must be a JSON string");
        }
        return value.getAsString();
    }

    private static String issuerFromDiscoveryUrl(String discoveryUrl) {
        URI uri = URI.create(discoveryUrl);
        String path = uri.getPath();
        String suffix = "/.well-known/openid-configuration";
        if (path == null || !path.endsWith(suffix)) {
            return null;
        }
        String issuerPath = path.substring(0, path.length() - suffix.length());
        return removeTrailingSlash(uri.getScheme() + "://" + uri.getAuthority() + issuerPath);
    }

    private static String removeTrailingSlash(String uri) {
        return uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
    }

    private static boolean isBoolean(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean();
    }

    private static boolean isNumber(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber();
    }

    private static boolean isString(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
    }

    private static Object toClaimValue(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (value.isJsonPrimitive()) {
            JsonPrimitive primitive = value.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            }
            if (primitive.isNumber()) {
                return primitive.getAsNumber();
            }
            return primitive.getAsString();
        }
        if (value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray();
            List<Object> list = new ArrayList<>(array.size());
            for (JsonElement item : array) {
                list.add(toClaimValue(item));
            }
            return Collections.unmodifiableList(list);
        }

        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
            map.put(entry.getKey(), toClaimValue(entry.getValue()));
        }
        return Collections.unmodifiableMap(map);
    }

    private static final class FailureRecord {
        final long failedAtMillis;
        final Throwable cause;

        FailureRecord(long failedAtMillis, Throwable cause) {
            this.failedAtMillis = failedAtMillis;
            this.cause = cause;
        }
    }

    private static final class DiscoveryCacheEntry {
        private final JsonObject json;
        private final long fetchedAtMillis;

        private DiscoveryCacheEntry(JsonObject json, long fetchedAtMillis) {
            this.json = json;
            this.fetchedAtMillis = fetchedAtMillis;
        }

        private boolean isExpired(long ttlSeconds, long now) {
            return now - fetchedAtMillis > ttlSeconds * 1000L;
        }
    }

}
