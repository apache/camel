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

import org.apache.camel.CamelContext;

/**
 * Factory for resolving OAuth 2.0 bearer tokens using the client_credentials grant.
 * <p/>
 * This requires camel-oauth on the classpath.
 */
public interface OAuthClientAuthenticationFactory {

    /**
     * Service factory key.
     */
    String FACTORY = "oauth-client-authentication-factory";

    /**
     * Resolves a bearer token using explicit configuration.
     * <p/>
     * Acquires a token from the configured token endpoint using the client_credentials grant. Tokens are cached if
     * caching is enabled in the config.
     *
     * @param  config    the OAuth client configuration
     * @return           the bearer token string
     * @throws Exception if token acquisition fails
     */
    String resolveToken(OAuthClientConfig config) throws Exception;

    /**
     * Resolves a bearer token using a named profile from Camel properties.
     * <p/>
     * Properties are resolved from {@code camel.oauth.<profileName>.*}:
     * <ul>
     * <li>{@code camel.oauth.<profileName>.client-id} (required)</li>
     * <li>{@code camel.oauth.<profileName>.client-secret} (required)</li>
     * <li>{@code camel.oauth.<profileName>.token-endpoint} (required)</li>
     * <li>{@code camel.oauth.<profileName>.scope} (optional)</li>
     * <li>{@code camel.oauth.<profileName>.cache-tokens} (optional, default true)</li>
     * <li>{@code camel.oauth.<profileName>.cached-tokens-default-expiry-seconds} (optional, default 3600)</li>
     * <li>{@code camel.oauth.<profileName>.cached-tokens-expiration-margin-seconds} (optional, default 5)</li>
     * </ul>
     *
     * @param  context     the CamelContext to resolve properties from
     * @param  profileName the named profile (e.g., "keycloak", "azure")
     * @return             the bearer token string
     * @throws Exception   if required properties are missing or token acquisition fails
     */
    String resolveToken(CamelContext context, String profileName) throws Exception;

    /**
     * Resolves a bearer token using the default (unnamed) profile.
     * <p/>
     * Properties are resolved from {@code camel.oauth.*} directly (backward compatible with existing single-IdP
     * configuration).
     *
     * @param  context   the CamelContext to resolve properties from
     * @return           the bearer token string
     * @throws Exception if required properties are missing or token acquisition fails
     */
    String resolveToken(CamelContext context) throws Exception;
}
