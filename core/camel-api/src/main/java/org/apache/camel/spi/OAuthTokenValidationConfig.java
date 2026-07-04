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
package org.apache.camel.spi;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

/**
 * Configuration for validating incoming OAuth 2.0 bearer tokens.
 * <p/>
 * Used by {@link OAuthTokenValidationFactory} to validate JWT tokens locally via JWKS or opaque tokens via RFC 7662
 * introspection.
 *
 * @since 4.21
 */
public class OAuthTokenValidationConfig {

    private @Nullable String jwksEndpoint;
    private @Nullable String introspectionEndpoint;
    private @Nullable String introspectionClientId;
    private @Nullable String introspectionClientSecret;
    private @Nullable String expectedIssuer;
    private Set<String> expectedAudiences = Collections.emptySet();
    private @Nullable String expectedTokenType;
    private int clockSkewSeconds;
    private long jwksCacheTtlSeconds = 600;
    private @Nullable String oidcDiscoveryUrl;
    private long oidcDiscoveryCacheTtlSeconds = 600;
    private int connectTimeoutSeconds = 5;
    private int readTimeoutSeconds = 10;
    private boolean requireExpiration = true;
    private @Nullable Set<String> allowedJwsAlgorithms;
    private boolean allowMissingAudience;
    private boolean allowMissingIssuer;
    private boolean allowInsecureHttp;

    public OAuthTokenValidationConfig() {
    }

    /**
     * Creates a copy of the source configuration.
     *
     * @since 4.21
     */
    public OAuthTokenValidationConfig(OAuthTokenValidationConfig source) {
        this.jwksEndpoint = source.getJwksEndpoint();
        this.introspectionEndpoint = source.getIntrospectionEndpoint();
        this.introspectionClientId = source.getIntrospectionClientId();
        this.introspectionClientSecret = source.getIntrospectionClientSecret();
        this.expectedIssuer = source.getExpectedIssuer();
        this.expectedAudiences = copyStringSet(source.getExpectedAudiences());
        this.expectedTokenType = source.getExpectedTokenType();
        this.clockSkewSeconds = source.getClockSkewSeconds();
        this.jwksCacheTtlSeconds = source.getJwksCacheTtlSeconds();
        this.oidcDiscoveryUrl = source.getOidcDiscoveryUrl();
        this.oidcDiscoveryCacheTtlSeconds = source.getOidcDiscoveryCacheTtlSeconds();
        this.connectTimeoutSeconds = source.getConnectTimeoutSeconds();
        this.readTimeoutSeconds = source.getReadTimeoutSeconds();
        this.requireExpiration = source.isRequireExpiration();
        this.allowedJwsAlgorithms = source.getAllowedJwsAlgorithms() != null
                ? copyStringSet(source.getAllowedJwsAlgorithms()) : null;
        this.allowMissingAudience = source.isAllowMissingAudience();
        this.allowMissingIssuer = source.isAllowMissingIssuer();
        this.allowInsecureHttp = source.isAllowInsecureHttp();
    }

    /**
     * Returns a copy of this configuration.
     *
     * @since 4.21
     */
    public OAuthTokenValidationConfig copy() {
        return new OAuthTokenValidationConfig(this);
    }

    /**
     * JWKS endpoint used to validate JWT signatures.
     *
     * @since 4.21
     */
    public @Nullable String getJwksEndpoint() {
        return jwksEndpoint;
    }

    /**
     * Sets the JWKS endpoint used to validate JWT signatures.
     *
     * @since 4.21
     */
    public OAuthTokenValidationConfig setJwksEndpoint(@Nullable String jwksEndpoint) {
        this.jwksEndpoint = trimToNull(jwksEndpoint);
        return this;
    }

    /**
     * RFC 7662 introspection endpoint used to validate opaque tokens.
     *
     * @since 4.21
     */
    public @Nullable String getIntrospectionEndpoint() {
        return introspectionEndpoint;
    }

    /**
     * Sets the RFC 7662 introspection endpoint used to validate opaque tokens.
     *
     * @since 4.21
     */
    public OAuthTokenValidationConfig setIntrospectionEndpoint(@Nullable String introspectionEndpoint) {
        this.introspectionEndpoint = trimToNull(introspectionEndpoint);
        return this;
    }

    /**
     * Client identifier used for token introspection.
     *
     * @since 4.21
     */
    public @Nullable String getIntrospectionClientId() {
        return introspectionClientId;
    }

    /**
     * Sets the client identifier used for token introspection.
     *
     * @since 4.21
     */
    public OAuthTokenValidationConfig setIntrospectionClientId(@Nullable String introspectionClientId) {
        this.introspectionClientId = rejectBlank(introspectionClientId, "introspectionClientId");
        return this;
    }

    /**
     * Client secret used for token introspection.
     *
     * @since 4.21
     */
    public @Nullable String getIntrospectionClientSecret() {
        return introspectionClientSecret;
    }

    /**
     * Sets the client secret used for token introspection.
     *
     * @since 4.21
     */
    public OAuthTokenValidationConfig setIntrospectionClientSecret(@Nullable String introspectionClientSecret) {
        this.introspectionClientSecret = rejectBlank(introspectionClientSecret, "introspectionClientSecret");
        return this;
    }

    /**
     * Expected issuer claim.
     *
     * @since 4.21
     */
    public @Nullable String getExpectedIssuer() {
        return expectedIssuer;
    }

    /**
     * Sets the expected issuer claim.
     *
     * @since 4.21
     */
    public OAuthTokenValidationConfig setExpectedIssuer(@Nullable String expectedIssuer) {
        this.expectedIssuer = trimToNull(expectedIssuer);
        return this;
    }

    /**
     * First configured expected audience claim, or null when no audience is configured.
     * <p/>
     * Prefer {@link #getExpectedAudiences()} for new code that accepts multiple audiences.
     *
     * @since 4.21
     */
    public @Nullable String getExpectedAudience() {
        return expectedAudiences.isEmpty() ? null : expectedAudiences.iterator().next();
    }

    /**
     * Sets a single accepted audience claim.
     *
     * @since 4.21
     */
    public OAuthTokenValidationConfig setExpectedAudience(@Nullable String expectedAudience) {
        this.expectedAudiences = expectedAudience == null || expectedAudience.isBlank()
                ? Collections.emptySet()
                : Collections.singleton(expectedAudience.trim());
        return this;
    }

    /**
     * Accepted audience claim values. When non-empty, the token {@code aud} claim must contain at least one of these
     * values.
     *
     * @since 4.21
     */
    public Set<String> getExpectedAudiences() {
        return expectedAudiences;
    }

    /**
     * Sets the accepted audience claim values.
     *
     * @since 4.21
     */
    public OAuthTokenValidationConfig setExpectedAudiences(@Nullable Set<String> expectedAudiences) {
        this.expectedAudiences = copyStringSet(expectedAudiences);
        return this;
    }

    /**
     * Expected JWT {@code typ} header, for example {@code at+jwt}. When set, JWT tokens without this type are rejected.
     *
     * @since 4.21
     */
    public @Nullable String getExpectedTokenType() {
        return expectedTokenType;
    }

    /**
     * Sets the expected JWT {@code typ} header.
     *
     * @since 4.21
     */
    public OAuthTokenValidationConfig setExpectedTokenType(@Nullable String expectedTokenType) {
        this.expectedTokenType = trimToNull(expectedTokenType);
        return this;
    }

    /**
     * Clock skew leeway in seconds for temporal claims.
     *
     * @since 4.21
     */
    public int getClockSkewSeconds() {
        return clockSkewSeconds;
    }

    /**
     * Sets clock skew leeway in seconds for temporal claims.
     *
     * @since 4.21
     */
    public OAuthTokenValidationConfig setClockSkewSeconds(int clockSkewSeconds) {
        if (clockSkewSeconds < 0) {
            throw new IllegalArgumentException("clockSkewSeconds must be >= 0");
        }
        this.clockSkewSeconds = clockSkewSeconds;
        return this;
    }

    /**
     * JWKS cache TTL in seconds.
     *
     * @since 4.21
     */
    public long getJwksCacheTtlSeconds() {
        return jwksCacheTtlSeconds;
    }

    /**
     * Sets the JWKS cache TTL in seconds.
     *
     * @since 4.21
     */
    public OAuthTokenValidationConfig setJwksCacheTtlSeconds(long jwksCacheTtlSeconds) {
        if (jwksCacheTtlSeconds <= 0) {
            throw new IllegalArgumentException("jwksCacheTtlSeconds must be > 0");
        }
        this.jwksCacheTtlSeconds = jwksCacheTtlSeconds;
        return this;
    }

    /**
     * OIDC discovery URL used to resolve JWKS, introspection, and issuer metadata.
     *
     * @since 4.21
     */
    public @Nullable String getOidcDiscoveryUrl() {
        return oidcDiscoveryUrl;
    }

    /**
     * Sets the OIDC discovery URL used to resolve JWKS, introspection, and issuer metadata.
     *
     * @since 4.21
     */
    public OAuthTokenValidationConfig setOidcDiscoveryUrl(@Nullable String oidcDiscoveryUrl) {
        this.oidcDiscoveryUrl = trimToNull(oidcDiscoveryUrl);
        return this;
    }

    /**
     * OIDC discovery metadata cache TTL in seconds.
     *
     * @since 4.21
     */
    public long getOidcDiscoveryCacheTtlSeconds() {
        return oidcDiscoveryCacheTtlSeconds;
    }

    /**
     * Sets the OIDC discovery metadata cache TTL in seconds.
     *
     * @since 4.21
     */
    public OAuthTokenValidationConfig setOidcDiscoveryCacheTtlSeconds(long oidcDiscoveryCacheTtlSeconds) {
        if (oidcDiscoveryCacheTtlSeconds <= 0) {
            throw new IllegalArgumentException("oidcDiscoveryCacheTtlSeconds must be > 0");
        }
        this.oidcDiscoveryCacheTtlSeconds = oidcDiscoveryCacheTtlSeconds;
        return this;
    }

    /**
     * Network connect timeout in seconds for outbound HTTP calls (JWKS fetch, OIDC discovery, introspection).
     *
     * @since 4.21
     */
    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    /**
     * Sets the network connect timeout in seconds for outbound HTTP calls.
     *
     * @since 4.21
     */
    public OAuthTokenValidationConfig setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        if (connectTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("connectTimeoutSeconds must be > 0");
        }
        this.connectTimeoutSeconds = connectTimeoutSeconds;
        return this;
    }

    /**
     * Network read timeout in seconds for outbound HTTP calls (JWKS fetch, OIDC discovery, introspection).
     *
     * @since 4.21
     */
    public int getReadTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    /**
     * Sets the network read timeout in seconds for outbound HTTP calls.
     *
     * @since 4.21
     */
    public OAuthTokenValidationConfig setReadTimeoutSeconds(int readTimeoutSeconds) {
        if (readTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("readTimeoutSeconds must be > 0");
        }
        this.readTimeoutSeconds = readTimeoutSeconds;
        return this;
    }

    /**
     * Whether to require the {@code exp} claim in JWT tokens. When true (default), JWTs without an expiration claim are
     * rejected.
     *
     * @since 4.21
     */
    public boolean isRequireExpiration() {
        return requireExpiration;
    }

    /**
     * Sets whether JWT tokens must contain an {@code exp} claim.
     *
     * @since 4.21
     */
    public OAuthTokenValidationConfig setRequireExpiration(boolean requireExpiration) {
        this.requireExpiration = requireExpiration;
        return this;
    }

    /**
     * Allowed JWS algorithms for JWT validation. When set, JWTs signed with algorithms not in this set are rejected.
     * When null (default), any RSA or EC algorithm supported by the verifier is accepted.
     *
     * @since 4.21
     */
    public @Nullable Set<String> getAllowedJwsAlgorithms() {
        return allowedJwsAlgorithms;
    }

    /**
     * Sets the allowed JWS algorithms for JWT validation. Blank values are ignored.
     *
     * @since 4.21
     */
    public OAuthTokenValidationConfig setAllowedJwsAlgorithms(@Nullable Set<String> allowedJwsAlgorithms) {
        Set<String> algorithms = copyStringSet(allowedJwsAlgorithms);
        this.allowedJwsAlgorithms = algorithms.isEmpty() ? null : algorithms;
        return this;
    }

    /**
     * Whether token validation may proceed without configured accepted audiences.
     *
     * @since 4.21
     */
    public boolean isAllowMissingAudience() {
        return allowMissingAudience;
    }

    /**
     * Sets whether token validation may proceed without configured accepted audiences.
     *
     * @since 4.21
     */
    public OAuthTokenValidationConfig setAllowMissingAudience(boolean allowMissingAudience) {
        this.allowMissingAudience = allowMissingAudience;
        return this;
    }

    /**
     * Whether token validation may proceed without an expected issuer.
     *
     * @since 4.21
     */
    public boolean isAllowMissingIssuer() {
        return allowMissingIssuer;
    }

    /**
     * Sets whether token validation may proceed without an expected issuer.
     *
     * @since 4.21
     */
    public OAuthTokenValidationConfig setAllowMissingIssuer(boolean allowMissingIssuer) {
        this.allowMissingIssuer = allowMissingIssuer;
        return this;
    }

    /**
     * Whether JWKS, OIDC discovery, or introspection endpoints may use plain HTTP.
     *
     * @since 4.21
     */
    public boolean isAllowInsecureHttp() {
        return allowInsecureHttp;
    }

    /**
     * Sets whether JWKS, OIDC discovery, or introspection endpoints may use plain HTTP.
     *
     * @since 4.21
     */
    public OAuthTokenValidationConfig setAllowInsecureHttp(boolean allowInsecureHttp) {
        this.allowInsecureHttp = allowInsecureHttp;
        return this;
    }

    private static Set<String> copyStringSet(@Nullable Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        LinkedHashSet<String> copy = values.stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (copy.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(copy);
    }

    private static @Nullable String trimToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static @Nullable String rejectBlank(@Nullable String value, String label) {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value;
    }

}
