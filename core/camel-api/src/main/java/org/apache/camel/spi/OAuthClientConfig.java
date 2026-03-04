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

/**
 * Configuration for OAuth 2.0 Client Credentials grant.
 * <p/>
 * Used by {@link OAuthClientAuthenticationFactory} to create a {@link org.apache.camel.Processor} that acquires a
 * bearer token and sets the {@code Authorization: Bearer <token>} header on the Exchange message.
 */
public class OAuthClientConfig {

    private String clientId;
    private String clientSecret;
    private String tokenEndpoint;
    private String scope;
    private boolean cacheTokens = true;
    private long cachedTokensDefaultExpirySeconds = 3600;
    private long cachedTokensExpirationMarginSeconds = 5;

    public String getClientId() {
        return clientId;
    }

    public OAuthClientConfig setClientId(String clientId) {
        this.clientId = clientId;
        return this;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public OAuthClientConfig setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
        return this;
    }

    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    public OAuthClientConfig setTokenEndpoint(String tokenEndpoint) {
        this.tokenEndpoint = tokenEndpoint;
        return this;
    }

    public String getScope() {
        return scope;
    }

    public OAuthClientConfig setScope(String scope) {
        this.scope = scope;
        return this;
    }

    public boolean isCacheTokens() {
        return cacheTokens;
    }

    public OAuthClientConfig setCacheTokens(boolean cacheTokens) {
        this.cacheTokens = cacheTokens;
        return this;
    }

    public long getCachedTokensDefaultExpirySeconds() {
        return cachedTokensDefaultExpirySeconds;
    }

    public OAuthClientConfig setCachedTokensDefaultExpirySeconds(long cachedTokensDefaultExpirySeconds) {
        this.cachedTokensDefaultExpirySeconds = cachedTokensDefaultExpirySeconds;
        return this;
    }

    public long getCachedTokensExpirationMarginSeconds() {
        return cachedTokensExpirationMarginSeconds;
    }

    public OAuthClientConfig setCachedTokensExpirationMarginSeconds(long cachedTokensExpirationMarginSeconds) {
        this.cachedTokensExpirationMarginSeconds = cachedTokensExpirationMarginSeconds;
        return this;
    }
}
