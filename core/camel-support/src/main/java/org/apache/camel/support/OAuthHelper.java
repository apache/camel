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
package org.apache.camel.support;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.spi.OAuthClientAuthenticationFactory;
import org.apache.camel.spi.OAuthTokenValidationFactory;
import org.apache.camel.spi.OAuthTokenValidationResult;

/**
 * Helper for resolving and validating OAuth tokens via the {@link OAuthClientAuthenticationFactory} and
 * {@link OAuthTokenValidationFactory} SPIs.
 * <p/>
 * Token acquisition requires camel-oauth on the classpath. Token validation requires an
 * {@link OAuthTokenValidationFactory}; camel-oauth provides the default implementation, and runtimes can provide their
 * own implementation backed by their native security stack.
 */
public final class OAuthHelper {

    private static final String OAUTH_PROFILE_PREFIX = "camel.oauth.";
    private static final String VALIDATION_FACTORY_PROPERTY = "validation-factory";

    private OAuthHelper() {
    }

    /**
     * Resolves an OAuth bearer token using a named profile.
     * <p/>
     * The profile properties are resolved from {@code camel.oauth.<profileName>.*}.
     *
     * @param  context     the CamelContext
     * @param  profileName the OAuth profile name
     * @return             the bearer token string
     * @throws Exception   if the factory is not found or token acquisition fails
     */
    public static String resolveOAuthToken(CamelContext context, String profileName) throws Exception {
        OAuthClientAuthenticationFactory factory = ResolverHelper.resolveMandatoryBootstrapService(
                context,
                OAuthClientAuthenticationFactory.FACTORY,
                OAuthClientAuthenticationFactory.class,
                "camel-oauth");
        return factory.resolveToken(context, profileName);
    }

    /**
     * Resolves the OAuth token validation factory.
     * <p/>
     * The default profile can reference a registry bean with {@code camel.oauth.validation-factory}. If configured, the
     * referenced bean must exist and implement {@link OAuthTokenValidationFactory}.
     *
     * @param  context the CamelContext
     * @return         the token validation factory
     * @since          4.21
     */
    public static OAuthTokenValidationFactory resolveOAuthTokenValidationFactory(CamelContext context) {
        OAuthTokenValidationFactory factory
                = resolveConfiguredValidationFactory(context, OAUTH_PROFILE_PREFIX + VALIDATION_FACTORY_PROPERTY);
        if (factory == null) {
            factory = context.getRegistry()
                    .lookupByNameAndType(OAuthTokenValidationFactory.FACTORY, OAuthTokenValidationFactory.class);
        }
        if (factory == null) {
            factory = context.getRegistry().findSingleByType(OAuthTokenValidationFactory.class);
        }
        if (factory == null) {
            factory = resolveBootstrapValidationFactory(context);
        }
        return CamelContextAware.trySetCamelContext(factory, context);
    }

    /**
     * Resolves the OAuth token validation factory for the given profile.
     * <p/>
     * The profile-specific {@code camel.oauth.<profileName>.validation-factory} property can reference a registry bean
     * by name. If configured, the referenced bean must exist and implement {@link OAuthTokenValidationFactory}.
     *
     * @param  context     the CamelContext
     * @param  profileName the OAuth profile name
     * @return             the token validation factory
     * @since              4.21
     */
    public static OAuthTokenValidationFactory resolveOAuthTokenValidationFactory(CamelContext context, String profileName) {
        if (profileName != null && !profileName.isBlank()) {
            OAuthTokenValidationFactory factory = resolveConfiguredValidationFactory(
                    context, OAUTH_PROFILE_PREFIX + profileName + "." + VALIDATION_FACTORY_PROPERTY);
            if (factory != null) {
                return CamelContextAware.trySetCamelContext(factory, context);
            }
        }
        return resolveOAuthTokenValidationFactory(context);
    }

    private static OAuthTokenValidationFactory resolveConfiguredValidationFactory(CamelContext context, String propertyKey) {
        String factoryReference = context.getPropertiesComponent().resolveProperty(propertyKey)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .orElse(null);
        if (factoryReference == null) {
            return null;
        }

        Object factory = EndpointHelper.resolveReferenceParameter(context, factoryReference, Object.class);
        if (factory instanceof OAuthTokenValidationFactory tokenValidationFactory) {
            return tokenValidationFactory;
        }
        throw new IllegalArgumentException(
                "OAuth token validation factory property " + propertyKey + " references " + factoryReference
                                           + " which is not an " + OAuthTokenValidationFactory.class.getName());
    }

    private static OAuthTokenValidationFactory resolveBootstrapValidationFactory(CamelContext context) {
        return ResolverHelper.resolveMandatoryBootstrapService(
                context,
                OAuthTokenValidationFactory.FACTORY,
                OAuthTokenValidationFactory.class,
                "camel-oauth or another OAuthTokenValidationFactory provider");
    }

    /**
     * Validates an incoming bearer token using a named profile.
     * <p/>
     * JWT tokens are validated locally via JWKS (signature, expiry, audience, issuer). Opaque tokens are validated via
     * RFC 7662 introspection.
     * <p/>
     * The profile properties are resolved from {@code camel.oauth.<profileName>.*}.
     *
     * @param  context     the CamelContext
     * @param  profileName the OAuth profile name
     * @param  token       the bearer token string to validate
     * @return             the validation result (never null)
     * @since              4.21
     */
    public static OAuthTokenValidationResult validateOAuthToken(
            CamelContext context, String profileName, String token) {
        return resolveOAuthTokenValidationFactory(context, profileName).validateToken(context, profileName, token);
    }

    /**
     * Validates an incoming bearer token using the default (unnamed) profile.
     * <p/>
     * JWT tokens are validated locally via JWKS (signature, expiry, audience, issuer). Opaque tokens are validated via
     * RFC 7662 introspection.
     * <p/>
     * The profile properties are resolved from {@code camel.oauth.*}.
     *
     * @param  context the CamelContext
     * @param  token   the bearer token string to validate
     * @return         the validation result (never null)
     * @since          4.21
     */
    public static OAuthTokenValidationResult validateOAuthToken(CamelContext context, String token) {
        return resolveOAuthTokenValidationFactory(context).validateToken(context, token);
    }
}
