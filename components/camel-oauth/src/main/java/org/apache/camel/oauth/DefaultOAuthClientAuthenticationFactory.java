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

import java.util.Optional;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.OAuthClientAuthenticationFactory;
import org.apache.camel.spi.OAuthClientConfig;
import org.apache.camel.spi.annotations.JdkService;

/**
 * Default implementation of {@link OAuthClientAuthenticationFactory} that resolves OAuth 2.0 bearer tokens using the
 * Client Credentials grant.
 * <p/>
 * Delegates token acquisition and caching to {@link OAuthClientCredentialsTokenResolver}.
 */
@JdkService(OAuthClientAuthenticationFactory.FACTORY)
public class DefaultOAuthClientAuthenticationFactory implements OAuthClientAuthenticationFactory {

    private final OAuthClientCredentialsTokenResolver resolver = new OAuthClientCredentialsTokenResolver();

    @Override
    public String resolveToken(OAuthClientConfig config) throws Exception {
        validateConfig(config);
        return resolver.resolveToken(config);
    }

    @Override
    public String resolveToken(CamelContext context, String profileName) throws Exception {
        OAuthClientConfig config = resolveProfileConfig(context, "camel.oauth." + profileName + ".");
        return resolveToken(config);
    }

    @Override
    public String resolveToken(CamelContext context) throws Exception {
        OAuthClientConfig config = resolveProfileConfig(context, "camel.oauth.");
        return resolveToken(config);
    }

    private OAuthClientConfig resolveProfileConfig(CamelContext context, String prefix) {
        OAuthClientConfig config = new OAuthClientConfig();

        // Required
        config.setClientId(resolveRequiredProperty(context, prefix + "client-id"));
        config.setClientSecret(resolveRequiredProperty(context, prefix + "client-secret"));
        config.setTokenEndpoint(resolveRequiredProperty(context, prefix + "token-endpoint"));

        // Optional
        resolveOptionalProperty(context, prefix + "scope")
                .ifPresent(config::setScope);
        resolveOptionalProperty(context, prefix + "cache-tokens")
                .map(Boolean::parseBoolean)
                .ifPresent(config::setCacheTokens);
        resolveOptionalProperty(context, prefix + "cached-tokens-default-expiry-seconds")
                .map(Long::parseLong)
                .ifPresent(config::setCachedTokensDefaultExpirySeconds);
        resolveOptionalProperty(context, prefix + "cached-tokens-expiration-margin-seconds")
                .map(Long::parseLong)
                .ifPresent(config::setCachedTokensExpirationMarginSeconds);

        return config;
    }

    private String resolveRequiredProperty(CamelContext ctx, String key) {
        return resolveOptionalProperty(ctx, key)
                .orElseThrow(() -> new IllegalArgumentException("Required OAuth property not found: " + key));
    }

    private Optional<String> resolveOptionalProperty(CamelContext ctx, String key) {
        return ctx.getPropertiesComponent().resolveProperty(key);
    }

    private void validateConfig(OAuthClientConfig config) {
        if (config.getClientId() == null || config.getClientId().isBlank()) {
            throw new IllegalArgumentException("OAuth clientId is required");
        }
        if (config.getClientSecret() == null || config.getClientSecret().isBlank()) {
            throw new IllegalArgumentException("OAuth clientSecret is required");
        }
        if (config.getTokenEndpoint() == null || config.getTokenEndpoint().isBlank()) {
            throw new IllegalArgumentException("OAuth tokenEndpoint is required");
        }
    }
}
