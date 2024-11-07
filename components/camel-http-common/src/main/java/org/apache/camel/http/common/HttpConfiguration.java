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
package org.apache.camel.http.common;

import java.io.Serializable;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;

public class HttpConfiguration implements Serializable {
    private static final long serialVersionUID = 1L;

    @Metadata(label = "producer,security",
              description = "Authentication methods allowed to use as a comma separated list of values Basic, Digest or NTLM.")
    private String authMethod;
    @Metadata(label = "producer,security", enums = "Basic,Digest,NTLM",
              description = "Which authentication method to prioritize to use, either as Basic, Digest or NTLM.")
    private String authMethodPriority;
    @Metadata(label = "producer,security", secret = true, description = "Authentication username")
    private String authUsername;
    @Metadata(label = "producer,security", secret = true, description = "Authentication password")
    private String authPassword;
    @Metadata(label = "producer,security", secret = true, description = "OAuth2 client id")
    private String oauth2ClientId;
    @Metadata(label = "producer,security", secret = true, description = "OAuth2 client secret")
    private String oauth2ClientSecret;
    @Metadata(label = "producer,security", description = "OAuth2 token endpoint")
    private String oauth2TokenEndpoint;
    @Metadata(label = "producer,security", description = "OAuth2 scope")
    private String oauth2Scope;
    @UriParam(label = "producer,security", defaultValue = "false",
            description = "Whether to cache OAuth2 client tokens.")
    private boolean oauth2CacheTokens = false;
    @UriParam(label = "producer,security", defaultValue = "3600",
            description = "Default expiration time for cached OAuth2 tokens, in seconds. Used if token response does not contain 'expires_in' field.")
    private long oauth2CachedTokensDefaultExpirySeconds = 3600L;
    @UriParam(label = "producer,security", defaultValue = "5",
            description = "Amount of time which is deducted from OAuth2 tokens expiry time to compensate for the time it takes OAuth2 Token Endpoint to send the token over http, in seconds. " +
                    "Set this parameter to high value if you OAuth2 Token Endpoint answers slowly or you tokens expire quickly. " +
                    "If you set this parameter to too small value, you can get 4xx http errors because camel will think that the received token is still valid, while in reality the token is expired for the Authentication server.")
    private long oauth2CachedTokensExpirationMarginSeconds = 5L;
    @Metadata(label = "producer,security", description = "Authentication domain to use with NTML")
    private String authDomain;
    @Metadata(label = "producer,security", description = "Authentication host to use with NTML")
    private String authHost;
    @Metadata(label = "producer,proxy", description = "Proxy hostname to use")
    private String proxyHost;
    @Metadata(label = "producer,proxy", description = "Proxy port to use")
    private int proxyPort;
    @Metadata(label = "producer,proxy", enums = "http,https", description = "Authentication scheme to use")
    private String proxyAuthScheme;
    @Metadata(label = "producer,proxy", enums = "Basic,Digest,NTLM", description = "Proxy authentication method to use")
    private String proxyAuthMethod;
    @Metadata(label = "producer,proxy", secret = true, description = "Proxy authentication username")
    private String proxyAuthUsername;
    @Metadata(label = "producer,proxy", secret = true, description = "Proxy authentication password")
    private String proxyAuthPassword;
    @Metadata(label = "producer,proxy", description = "Proxy authentication host")
    private String proxyAuthHost;
    @Metadata(label = "producer,proxy", description = "Proxy authentication port")
    private int proxyAuthPort;
    @Metadata(label = "producer,proxy", description = "Proxy authentication domain to use with NTML")
    private String proxyAuthDomain;

    public String getAuthMethod() {
        return authMethod;
    }

    /**
     * Authentication methods allowed to use as a comma separated list of values Basic, Digest or NTLM.
     */
    public void setAuthMethod(String authMethod) {
        this.authMethod = authMethod;
    }

    public String getAuthMethodPriority() {
        return authMethodPriority;
    }

    /**
     * Which authentication method to prioritize to use, either as Basic, Digest or NTLM.
     */
    public void setAuthMethodPriority(String authMethodPriority) {
        this.authMethodPriority = authMethodPriority;
    }

    public String getAuthUsername() {
        return authUsername;
    }

    /**
     * Authentication username
     */
    public void setAuthUsername(String authUsername) {
        this.authUsername = authUsername;
    }

    public String getAuthPassword() {
        return authPassword;
    }

    /**
     * Authentication password
     */
    public void setAuthPassword(String authPassword) {
        this.authPassword = authPassword;
    }

    public String getAuthDomain() {
        return authDomain;
    }

    /**
     * Authentication domain to use with NTML
     */
    public void setAuthDomain(String authDomain) {
        this.authDomain = authDomain;
    }

    public String getAuthHost() {
        return authHost;
    }

    /**
     * Authentication host to use with NTML
     */
    public void setAuthHost(String authHost) {
        this.authHost = authHost;
    }

    public String getProxyAuthScheme() {
        return proxyAuthScheme;
    }

    /**
     * Proxy authentication scheme to use
     */
    public void setProxyAuthScheme(String proxyAuthScheme) {
        this.proxyAuthScheme = proxyAuthScheme;
    }

    public String getProxyAuthMethod() {
        return proxyAuthMethod;
    }

    /**
     * Proxy authentication method to use
     */
    public void setProxyAuthMethod(String proxyAuthMethod) {
        this.proxyAuthMethod = proxyAuthMethod;
    }

    public String getProxyAuthUsername() {
        return proxyAuthUsername;
    }

    /**
     * Proxy authentication username
     */
    public void setProxyAuthUsername(String proxyAuthUsername) {
        this.proxyAuthUsername = proxyAuthUsername;
    }

    public String getProxyAuthPassword() {
        return proxyAuthPassword;
    }

    /**
     * Proxy authentication password
     */
    public void setProxyAuthPassword(String proxyAuthPassword) {
        this.proxyAuthPassword = proxyAuthPassword;
    }

    public String getProxyAuthDomain() {
        return proxyAuthDomain;
    }

    /**
     * Proxy authentication domain to use with NTML
     */
    public void setProxyAuthDomain(String proxyAuthDomain) {
        this.proxyAuthDomain = proxyAuthDomain;
    }

    public String getProxyAuthHost() {
        return proxyAuthHost;
    }

    /**
     * Proxy authentication host
     */
    public void setProxyAuthHost(String proxyAuthHost) {
        this.proxyAuthHost = proxyAuthHost;
    }

    public int getProxyAuthPort() {
        return proxyAuthPort;
    }

    /**
     * Proxy authentication port
     */
    public void setProxyAuthPort(int proxyAuthPort) {
        this.proxyAuthPort = proxyAuthPort;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    /**
     * Proxy hostname to use
     */
    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    /**
     * Proxy port to use
     */
    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getOauth2ClientId() {
        return this.oauth2ClientId;
    }

    /**
     * OAuth2 Client id
     */
    public void setOauth2ClientId(String oauth2ClientId) {
        this.oauth2ClientId = oauth2ClientId;
    }

    public String getOauth2ClientSecret() {
        return this.oauth2ClientSecret;
    }

    /**
     * OAuth2 Client secret
     */
    public void setOauth2ClientSecret(String oauth2ClientSecret) {
        this.oauth2ClientSecret = oauth2ClientSecret;
    }

    public String getOauth2TokenEndpoint() {
        return this.oauth2TokenEndpoint;
    }

    /**
     * OAuth2 token endpoint
     */
    public void setOauth2TokenEndpoint(String oauth2TokenEndpoint) {
        this.oauth2TokenEndpoint = oauth2TokenEndpoint;
    }

    public String getOauth2Scope() {
        return oauth2Scope;
    }

    /**
     * OAuth2 scope
     */
    public void setOauth2Scope(String oauth2Scope) {
        this.oauth2Scope = oauth2Scope;
    }

    public boolean isOauth2CacheTokens() {
        return oauth2CacheTokens;
    }

    /**
     * Whether to cache OAuth2 client tokens.
     */
    public void setOauth2CacheTokens(boolean oauth2CacheTokens) {
        this.oauth2CacheTokens = oauth2CacheTokens;
    }

    public long getOauth2CachedTokensDefaultExpirySeconds() {
        return oauth2CachedTokensDefaultExpirySeconds;
    }

    /**
     * Default expiration time for cached OAuth2 tokens, in seconds. Used if token response does not contain 'expires_in' field.
     */
    public void setOauth2CachedTokensDefaultExpirySeconds(long oauth2CachedTokensDefaultExpirySeconds) {
        this.oauth2CachedTokensDefaultExpirySeconds = oauth2CachedTokensDefaultExpirySeconds;
    }

    public long getOauth2CachedTokensExpirationMarginSeconds() {
        return oauth2CachedTokensExpirationMarginSeconds;
    }

    /**
     * Amount of time which is deducted from OAuth2 tokens expiry time to compensate for the time it takes OAuth2 Token Endpoint to send the token over http, in seconds.
     * Set this parameter to high value if you OAuth2 Token Endpoint answers slowly or you tokens expire quickly.
     * If you set this parameter to too small value, you can get 4xx http errors because camel will think that the received token is still valid, while in reality the token is expired for the Authentication server.
     */
    public void setOauth2CachedTokensExpirationMarginSeconds(long oauth2CachedTokensExpirationMarginSeconds) {
        this.oauth2CachedTokensExpirationMarginSeconds = oauth2CachedTokensExpirationMarginSeconds;
    }
}
