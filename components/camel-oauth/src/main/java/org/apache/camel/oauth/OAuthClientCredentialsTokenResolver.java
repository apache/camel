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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.google.gson.JsonObject;
import org.apache.camel.spi.OAuthClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves OAuth 2.0 bearer tokens using the client_credentials grant with thread-safe caching.
 * <p/>
 * Tokens are cached per (tokenEndpoint, clientId) pair using {@link UserProfile} for expiry tracking. This class
 * contains no Processor or Exchange dependencies — it is a pure token resolver.
 */
public class OAuthClientCredentialsTokenResolver {

    private static final Logger LOG = LoggerFactory.getLogger(OAuthClientCredentialsTokenResolver.class);

    private static final ConcurrentMap<TokenCacheKey, UserProfile> TOKEN_CACHE = new ConcurrentHashMap<>();

    /**
     * Resolves a bearer token for the given config. Uses caching if enabled.
     *
     * @param  config the OAuth client configuration
     * @return        the access token string
     */
    public String resolveToken(OAuthClientConfig config) {
        UserProfile profile;

        if (config.isCacheTokens()) {
            TokenCacheKey cacheKey = new TokenCacheKey(config.getTokenEndpoint(), config.getClientId());
            long margin = config.getCachedTokensExpirationMarginSeconds();
            profile = TOKEN_CACHE.compute(cacheKey, (key, existing) -> {
                if (existing != null && existing.ttl() > margin) {
                    return existing;
                }
                return acquireToken(config);
            });
        } else {
            profile = acquireToken(config);
        }

        return profile.accessToken()
                .orElseThrow(() -> new OAuthException("No access_token in token response"));
    }

    private UserProfile acquireToken(OAuthClientConfig config) {
        JsonObject json = OAuthTokenRequest.clientCredentialsGrant(
                config.getTokenEndpoint(),
                config.getClientId(),
                config.getClientSecret(),
                config.getScope());

        UserProfile profile = UserProfile.fromTokenResponse(json);
        LOG.debug("Acquired OAuth token from {}, ttl {}s", config.getTokenEndpoint(), profile.ttl());
        return profile;
    }

    record TokenCacheKey(String tokenEndpoint, String clientId) {
    }
}
