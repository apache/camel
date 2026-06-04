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

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;

/**
 * Factory for validating incoming OAuth 2.0 bearer tokens (JWT and opaque).
 * <p/>
 * JWT tokens are validated locally via JWKS (signature, expiry, audience, issuer). Opaque tokens are validated via RFC
 * 7662 introspection.
 * <p/>
 * The camel-oauth component provides the default implementation. Runtime-specific integrations can provide their own
 * implementation backed by their native security stack.
 *
 * @since 4.21
 */
public interface OAuthTokenValidationFactory {

    /**
     * Service factory key.
     */
    String FACTORY = "oauth-token-validation-factory";

    /**
     * Validates a bearer token using explicit configuration.
     * <p/>
     * The error contract distinguishes three failure modes:
     * <ul>
     * <li><b>Configuration errors</b> (missing JWKS endpoint, missing client credentials) throw
     * {@link IllegalArgumentException}. These indicate a setup problem that the operator must fix.</li>
     * <li><b>Validation failures</b> (expired token, invalid signature, wrong issuer) return
     * {@link OAuthTokenValidationResult#invalid(OAuthTokenValidationResult.ErrorCode, String)}. These indicate a
     * rejected bearer token.</li>
     * <li><b>Infrastructure failures</b> (network timeout, JWKS fetch failure) throw runtime exceptions. These indicate
     * a transient or infrastructure problem.</li>
     * </ul>
     * Callers integrating at an HTTP boundary should catch all exceptions and map them to an appropriate HTTP status
     * (401 for validation, 503 for infrastructure).
     *
     * @param  config                   the token validation configuration
     * @param  token                    the bearer token string to validate
     * @return                          the validation result (never null)
     * @throws IllegalArgumentException if required configuration is missing
     * @since                           4.21
     */
    OAuthTokenValidationResult validateToken(OAuthTokenValidationConfig config, String token);

    /**
     * Validates a bearer token using a named profile from Camel properties.
     * <p/>
     * Properties are resolved from {@code camel.oauth.<profileName>.*}:
     * <ul>
     * <li>{@code camel.oauth.<profileName>.base-uri} — base URL for OIDC auto-discovery from
     * {@code /.well-known/openid-configuration}</li>
     * <li>{@code camel.oauth.<profileName>.jwks-endpoint} — JWKS URL for JWT validation</li>
     * <li>{@code camel.oauth.<profileName>.introspection-endpoint} — RFC 7662 introspection URL for opaque tokens</li>
     * <li>{@code camel.oauth.<profileName>.introspection-client-id} — client ID for introspection; falls back to
     * {@code client-id}</li>
     * <li>{@code camel.oauth.<profileName>.introspection-client-secret} — client secret for introspection; falls back
     * to {@code client-secret}</li>
     * <li>{@code camel.oauth.<profileName>.expected-issuer} — expected issuer claim</li>
     * <li>{@code camel.oauth.<profileName>.expected-audience} — comma-separated accepted audience claim values</li>
     * <li>{@code camel.oauth.<profileName>.expected-token-type} — expected JWT {@code typ} header, for example
     * {@code at+jwt}</li>
     * <li>{@code camel.oauth.<profileName>.clock-skew-seconds} — clock skew leeway in seconds (default: 0)</li>
     * <li>{@code camel.oauth.<profileName>.jwks-cache-ttl-seconds} — JWKS cache TTL in seconds (default: 600)</li>
     * <li>{@code camel.oauth.<profileName>.oidc-discovery-cache-ttl-seconds} — OIDC discovery cache TTL in seconds
     * (default: 600)</li>
     * <li>{@code camel.oauth.<profileName>.connect-timeout-seconds} — outbound HTTP connect timeout in seconds
     * (default: 5)</li>
     * <li>{@code camel.oauth.<profileName>.read-timeout-seconds} — outbound HTTP read timeout in seconds (default:
     * 10)</li>
     * <li>{@code camel.oauth.<profileName>.require-expiration} — whether JWTs must contain {@code exp} (default:
     * true)</li>
     * <li>{@code camel.oauth.<profileName>.allowed-jws-algorithms} — comma-separated JWS algorithm allowlist</li>
     * <li>{@code camel.oauth.<profileName>.allow-missing-audience} — opt out of requiring an expected audience
     * (default: false)</li>
     * <li>{@code camel.oauth.<profileName>.allow-missing-issuer} — opt out of requiring an expected issuer (default:
     * false)</li>
     * <li>{@code camel.oauth.<profileName>.allow-insecure-http} — allow plain HTTP IdP endpoints for local development
     * (default: false)</li>
     * </ul>
     * Opaque-token introspection requires an introspection endpoint and client credentials, either through
     * {@code introspection-client-id}/{@code introspection-client-secret} or the
     * {@code client-id}/{@code client-secret} fallback properties.
     *
     * @param  context          the CamelContext to resolve properties from
     * @param  profileName      the named profile (e.g., "keycloak", "azure")
     * @param  token            the bearer token string to validate
     * @return                  the validation result (never null)
     * @throws RuntimeException if required properties are missing or an infrastructure error occurs
     * @since                   4.21
     */
    default OAuthTokenValidationResult validateToken(CamelContext context, String profileName, String token) {
        return validateToken(resolveProfileConfig(context, profileName), token);
    }

    /**
     * Validates a bearer token using the default (unnamed) profile.
     * <p/>
     * Properties are resolved from {@code camel.oauth.*} directly (backward compatible with existing single-IdP
     * configuration).
     *
     * @param  context          the CamelContext to resolve properties from
     * @param  token            the bearer token string to validate
     * @return                  the validation result (never null)
     * @throws RuntimeException if required properties are missing or an infrastructure error occurs
     * @since                   4.21
     */
    default OAuthTokenValidationResult validateToken(CamelContext context, String token) {
        return validateToken(resolveDefaultProfileConfig(context), token);
    }

    /**
     * Validates explicit token validation configuration without validating a token.
     * <p/>
     * Implementations should fail fast for static configuration errors such as missing endpoints, missing client
     * credentials, insecure endpoint URLs, or missing expected issuer/audience policy.
     *
     * @param  config                   the token validation configuration
     * @throws IllegalArgumentException if required configuration is missing or invalid
     * @since                           4.21
     */
    void validateConfiguration(OAuthTokenValidationConfig config);

    /**
     * Validates a named token validation profile without validating a token.
     *
     * @param  context                  the CamelContext to resolve properties from
     * @param  profileName              the named profile
     * @throws IllegalArgumentException if required profile configuration is missing or invalid
     * @since                           4.21
     */
    default void validateConfiguration(CamelContext context, String profileName) {
        validateConfiguration(resolveProfileConfig(context, profileName));
    }

    /**
     * Validates the default token validation profile without validating a token.
     *
     * @param  context                  the CamelContext to resolve properties from
     * @throws IllegalArgumentException if required profile configuration is missing or invalid
     * @since                           4.21
     */
    default void validateConfiguration(CamelContext context) {
        validateConfiguration(resolveDefaultProfileConfig(context));
    }

    /**
     * Resolves a named token validation profile from Camel properties.
     *
     * @param  context     the CamelContext to resolve properties from
     * @param  profileName the named profile
     * @return             the resolved profile configuration
     * @since              4.21
     */
    static OAuthTokenValidationConfig resolveProfileConfig(CamelContext context, String profileName) {
        return resolveProfileConfigWithPrefix(context, "camel.oauth." + profileName + ".");
    }

    /**
     * Resolves the default token validation profile from Camel properties.
     *
     * @param  context the CamelContext to resolve properties from
     * @return         the resolved profile configuration
     * @since          4.21
     */
    static OAuthTokenValidationConfig resolveDefaultProfileConfig(CamelContext context) {
        return resolveProfileConfigWithPrefix(context, "camel.oauth.");
    }

    private static OAuthTokenValidationConfig resolveProfileConfigWithPrefix(CamelContext context, String prefix) {
        OAuthTokenValidationConfig config = new OAuthTokenValidationConfig();

        resolveOptionalProperty(context, prefix + "jwks-endpoint")
                .ifPresent(config::setJwksEndpoint);
        resolveOptionalProperty(context, prefix + "introspection-endpoint")
                .ifPresent(config::setIntrospectionEndpoint);

        resolveOptionalProperty(context, prefix + "introspection-client-id")
                .or(() -> resolveOptionalProperty(context, prefix + "client-id"))
                .ifPresent(config::setIntrospectionClientId);
        resolveOptionalProperty(context, prefix + "introspection-client-secret")
                .or(() -> resolveOptionalProperty(context, prefix + "client-secret"))
                .ifPresent(config::setIntrospectionClientSecret);

        resolveOptionalProperty(context, prefix + "expected-issuer")
                .ifPresent(config::setExpectedIssuer);
        resolveOptionalProperty(context, prefix + "expected-audience")
                .map(OAuthTokenValidationFactory::parseStringSet)
                .ifPresent(config::setExpectedAudiences);
        resolveOptionalProperty(context, prefix + "expected-token-type")
                .ifPresent(config::setExpectedTokenType);
        resolveOptionalProperty(context, prefix + "clock-skew-seconds")
                .map(Integer::parseInt)
                .ifPresent(config::setClockSkewSeconds);
        resolveOptionalProperty(context, prefix + "jwks-cache-ttl-seconds")
                .map(Long::parseLong)
                .ifPresent(config::setJwksCacheTtlSeconds);
        resolveOptionalProperty(context, prefix + "oidc-discovery-cache-ttl-seconds")
                .map(Long::parseLong)
                .ifPresent(config::setOidcDiscoveryCacheTtlSeconds);
        resolveOptionalProperty(context, prefix + "connect-timeout-seconds")
                .map(Integer::parseInt)
                .ifPresent(config::setConnectTimeoutSeconds);
        resolveOptionalProperty(context, prefix + "read-timeout-seconds")
                .map(Integer::parseInt)
                .ifPresent(config::setReadTimeoutSeconds);
        resolveOptionalProperty(context, prefix + "require-expiration")
                .map(Boolean::parseBoolean)
                .ifPresent(config::setRequireExpiration);
        resolveOptionalProperty(context, prefix + "allowed-jws-algorithms")
                .map(OAuthTokenValidationFactory::parseStringSet)
                .filter(algorithms -> !algorithms.isEmpty())
                .ifPresent(config::setAllowedJwsAlgorithms);
        resolveOptionalProperty(context, prefix + "allow-missing-audience")
                .map(Boolean::parseBoolean)
                .ifPresent(config::setAllowMissingAudience);
        resolveOptionalProperty(context, prefix + "allow-missing-issuer")
                .map(Boolean::parseBoolean)
                .ifPresent(config::setAllowMissingIssuer);
        resolveOptionalProperty(context, prefix + "allow-insecure-http")
                .map(Boolean::parseBoolean)
                .ifPresent(config::setAllowInsecureHttp);

        resolveOptionalProperty(context, prefix + "base-uri")
                .map(String::trim)
                .filter(baseUri -> !baseUri.isEmpty())
                .ifPresent(baseUri -> {
                    String normalizedBaseUri = removeTrailingSlash(baseUri);
                    if (config.getExpectedIssuer() == null) {
                        config.setExpectedIssuer(normalizedBaseUri);
                    }
                    config.setOidcDiscoveryUrl(normalizedBaseUri + "/.well-known/openid-configuration");
                });

        return config;
    }

    private static Optional<String> resolveOptionalProperty(CamelContext context, String key) {
        return context.getPropertiesComponent().resolveProperty(key);
    }

    private static Set<String> parseStringSet(String value) {
        LinkedHashSet<String> values = Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return Collections.unmodifiableSet(values);
    }

    private static String removeTrailingSlash(String uri) {
        return uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
    }
}
