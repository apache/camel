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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * Result of validating an incoming OAuth 2.0 bearer token.
 * <p/>
 * Contains the validation outcome (valid/invalid), extracted claims from the token, and an error message when the token
 * is invalid.
 *
 * @since 4.21
 */
public final class OAuthTokenValidationResult {

    /**
     * Stable validation failure categories for rejected bearer tokens.
     *
     * @since 4.21
     */
    public enum ErrorCode {
        /**
         * Generic token validation failure.
         */
        INVALID_TOKEN,
        /**
         * The token is inactive according to introspection.
         */
        INACTIVE_TOKEN,
        /**
         * The token is expired.
         */
        EXPIRED_TOKEN,
        /**
         * The token is not valid yet.
         */
        NOT_YET_VALID,
        /**
         * The token has no required expiration claim.
         */
        MISSING_EXPIRATION,
        /**
         * The token signature is invalid.
         */
        INVALID_SIGNATURE,
        /**
         * No eligible verification key was found.
         */
        NO_MATCHING_KEY,
        /**
         * The token algorithm is not allowed.
         */
        UNSUPPORTED_ALGORITHM,
        /**
         * The token issuer does not match the expected issuer.
         */
        INVALID_ISSUER,
        /**
         * The token audience does not match the expected audience.
         */
        INVALID_AUDIENCE
    }

    private final boolean valid;
    private final @Nullable String subject;
    private final @Nullable String issuer;
    private final List<String> audience;
    private final List<String> scopes;
    private final Map<String, Object> claims;
    private final long expiresAt;
    private final @Nullable ErrorCode errorCode;
    private final @Nullable String error;

    private OAuthTokenValidationResult(
                                       boolean valid,
                                       @Nullable String subject,
                                       @Nullable String issuer,
                                       @Nullable List<String> audience,
                                       @Nullable List<String> scopes,
                                       @Nullable Map<String, Object> claims,
                                       long expiresAt,
                                       @Nullable ErrorCode errorCode,
                                       @Nullable String error) {
        this.valid = valid;
        this.subject = subject;
        this.issuer = issuer;
        this.audience = audience != null ? Collections.unmodifiableList(new ArrayList<>(audience)) : Collections.emptyList();
        this.scopes = scopes != null ? Collections.unmodifiableList(new ArrayList<>(scopes)) : Collections.emptyList();
        this.claims = claims != null ? Collections.unmodifiableMap(new LinkedHashMap<>(claims)) : Collections.emptyMap();
        this.expiresAt = expiresAt;
        this.errorCode = errorCode;
        this.error = error;
    }

    /**
     * Creates a result for a successfully validated token.
     *
     * @since 4.21
     */
    public static OAuthTokenValidationResult valid(
            @Nullable String subject,
            @Nullable String issuer,
            @Nullable List<String> audience,
            @Nullable List<String> scopes,
            @Nullable Map<String, Object> claims,
            long expiresAt) {
        return new OAuthTokenValidationResult(true, subject, issuer, audience, scopes, claims, expiresAt, null, null);
    }

    /**
     * Creates a result for a token that failed validation.
     *
     * @since 4.21
     */
    public static OAuthTokenValidationResult invalid(String error) {
        return invalid(ErrorCode.INVALID_TOKEN, error);
    }

    /**
     * Creates a result for a token that failed validation.
     *
     * @since 4.21
     */
    public static OAuthTokenValidationResult invalid(ErrorCode errorCode, String error) {
        return new OAuthTokenValidationResult(
                false, null, null, Collections.emptyList(), Collections.emptyList(),
                Collections.emptyMap(), 0, Objects.requireNonNull(errorCode, "errorCode"),
                Objects.requireNonNull(error, "error"));
    }

    /**
     * Whether the token passed validation.
     *
     * @since 4.21
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * The {@code sub} claim from the token, or null if not present.
     *
     * @since 4.21
     */
    public @Nullable String getSubject() {
        return subject;
    }

    /**
     * The principal name for route-level decisions.
     * <p/>
     * This implementation returns the {@code sub} claim.
     *
     * @since 4.21
     */
    public @Nullable String getName() {
        return subject;
    }

    /**
     * The {@code iss} claim from the token, or null if not present.
     *
     * @since 4.21
     */
    public @Nullable String getIssuer() {
        return issuer;
    }

    /**
     * The {@code aud} claim(s) from the token.
     *
     * @since 4.21
     */
    public List<String> getAudience() {
        return audience;
    }

    /**
     * The {@code scope} claim from the token, split into individual scopes.
     *
     * @since 4.21
     */
    public List<String> getScopes() {
        return scopes;
    }

    /**
     * Whether the token has the given OAuth scope.
     *
     * @since 4.21
     */
    public boolean hasScope(String scope) {
        return scopes.contains(scope);
    }

    /**
     * All claims from the token payload (JWT) or introspection response.
     *
     * @since 4.21
     */
    public Map<String, Object> getClaims() {
        return claims;
    }

    /**
     * All attributes extracted from the token payload (JWT) or introspection response.
     * <p/>
     * This is an alias for {@link #getClaims()} using principal-oriented terminology.
     *
     * @since 4.21
     */
    public Map<String, Object> getAttributes() {
        return claims;
    }

    /**
     * Gets an attribute extracted from the token payload (JWT) or introspection response.
     *
     * @since 4.21
     */
    public @Nullable Object getAttribute(String name) {
        return claims.get(name);
    }

    /**
     * Gets an attribute extracted from the token payload (JWT) or introspection response and casts it to the requested
     * type.
     *
     * @throws ClassCastException if the attribute exists but is not assignable to the requested type
     *
     * @since                     4.21
     */
    public <T> @Nullable T getAttribute(String name, Class<T> type) {
        Objects.requireNonNull(type, "type");
        Object value = getAttribute(name);
        if (value == null) {
            return null;
        }
        return type.cast(value);
    }

    /**
     * Gets a claim extracted from the token payload (JWT) or introspection response.
     *
     * @since 4.21
     */
    public @Nullable Object getClaim(String name) {
        return getAttribute(name);
    }

    /**
     * Gets a claim extracted from the token payload (JWT) or introspection response and casts it to the requested type.
     *
     * @throws ClassCastException if the claim exists but is not assignable to the requested type
     *
     * @since                     4.21
     */
    public <T> @Nullable T getClaim(String name, Class<T> type) {
        return getAttribute(name, type);
    }

    /**
     * The {@code exp} claim as epoch seconds, or 0 if not present.
     *
     * @since 4.21
     */
    public long getExpiresAt() {
        return expiresAt;
    }

    /**
     * Stable validation failure category when the token is invalid, or null when valid.
     *
     * @since 4.21
     */
    public @Nullable ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * Human-readable error message when the token is invalid, or null when valid.
     *
     * @since 4.21
     */
    public @Nullable String getError() {
        return error;
    }
}
