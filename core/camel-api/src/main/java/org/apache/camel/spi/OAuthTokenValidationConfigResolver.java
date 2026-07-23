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
 * Resolves {@link OAuthTokenValidationConfig} instances from Camel OAuth profile properties.
 *
 * @since 4.21
 */
public final class OAuthTokenValidationConfigResolver {

    private OAuthTokenValidationConfigResolver() {
    }

    /**
     * Resolves a named token validation profile from Camel properties.
     *
     * @param  context     the CamelContext to resolve properties from
     * @param  profileName the named profile
     * @return             the resolved profile configuration
     * @since              4.21
     */
    public static OAuthTokenValidationConfig resolveProfileConfig(CamelContext context, String profileName) {
        return resolveProfileConfigWithPrefix(context, "camel.oauth." + profileName + ".");
    }

    /**
     * Resolves the default token validation profile from Camel properties.
     *
     * @param  context the CamelContext to resolve properties from
     * @return         the resolved profile configuration
     * @since          4.21
     */
    public static OAuthTokenValidationConfig resolveDefaultProfileConfig(CamelContext context) {
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
                .map(OAuthTokenValidationConfigResolver::parseStringSet)
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
                .map(OAuthTokenValidationConfigResolver::parseStringSet)
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
