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
package org.apache.camel.component.keycloak.security;

import java.security.PublicKey;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.spi.AuthorizationPolicy;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.ObjectHelper;
import org.keycloak.admin.client.Keycloak;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Metadata(label = "bean",
          description = "An authorization policy that validates the caller's Keycloak access token and checks its roles and"
                        + " permissions before the part of the route it guards runs. A caller that fails is denied with"
                        + " CamelAuthorizationException.",
          annotations = { "interfaceName=org.apache.camel.spi.AuthorizationPolicy" })
@Configurer(metadataOnly = true)
public class KeycloakSecurityPolicy implements AuthorizationPolicy {
    private static final Logger LOG = LoggerFactory.getLogger(KeycloakSecurityPolicy.class);

    @Metadata(description = "The base URL of the Keycloak server, for example http://localhost:8080.")
    private String serverUrl;
    @Metadata(description = "The Keycloak realm the tokens are issued in.")
    private String realm;
    @Metadata(description = "The client id of this application in the realm.")
    private String clientId;
    @Metadata(label = "security", security = "secret",
              description = "The client secret, for a confidential client.")
    private String clientSecret;
    @Metadata(label = "security", security = "secret",
              description = "The user to sign in as when useResourceOwnerPasswordCredentials is enabled.")
    private String username;
    @Metadata(label = "security", security = "secret",
              description = "The password of that user.")
    private String password;
    /**
     * Comma-separated list of required roles for authorization. Example: "admin,user,manager"
     */
    @Metadata(description = "Comma-separated list of the roles the caller must have, for example admin,user. Whether it needs"
                            + " all of them or any one is set by allRolesRequired.")
    private String requiredRoles;
    /**
     * Comma-separated list of required permissions for authorization. Example: "read:documents,write:documents"
     */
    @Metadata(description = "Comma-separated list of the permissions the caller must have, for example"
                            + " read:documents,write:documents. Whether it needs all of them or any one is set by"
                            + " allPermissionsRequired.")
    private String requiredPermissions;
    @Metadata(defaultValue = "true",
              description = "Whether the caller must have all the required roles, rather than any one of them.")
    private boolean allRolesRequired = true;
    @Metadata(defaultValue = "true",
              description = "Whether the caller must have all the required permissions, rather than any one of them.")
    private boolean allPermissionsRequired = true;
    @Metadata(label = "security", defaultValue = "false",
              description = "Whether the policy's Keycloak client signs in with username and password, the resource owner"
                            + " password credentials grant, rather than with clientId and clientSecret.")
    private boolean useResourceOwnerPasswordCredentials = false;
    @Metadata(label = "security",
              description = "The public key to verify token signatures with. Used only when autoFetchPublicKey is false,"
                            + " which it is not by default.")
    private PublicKey publicKey;
    /**
     * Enable OAuth 2.0 token introspection for real-time token validation. When enabled, tokens are validated by
     * calling Keycloak's introspection endpoint instead of local JWT parsing. This allows detecting revoked tokens
     * before expiration.
     */
    @Metadata(label = "security", defaultValue = "false",
              description = "Whether to validate tokens with Keycloak's token introspection endpoint rather than by parsing"
                            + " the JWT locally, which detects a token revoked before it expires.")
    private boolean useTokenIntrospection = false;
    /**
     * Enable caching of token introspection results to reduce API calls to Keycloak.
     */
    @Metadata(label = "advanced", defaultValue = "true",
              description = "Whether to cache token introspection results, to reduce the calls to Keycloak.")
    private boolean introspectionCacheEnabled = true;
    /**
     * Time-to-live for cached introspection results in seconds. Default is 60 seconds.
     */
    @Metadata(label = "advanced", defaultValue = "60",
              description = "How long a cached introspection result is kept, in seconds, given as a plain number rather"
                            + " than a time pattern such as 5m.")
    private long introspectionCacheTtl = 60;
    /**
     * Enable validation that tokens are bound to specific sessions. When enabled, tokens from headers must match the
     * session-bound token to prevent session fixation attacks. Default is true for security.
     */
    @Metadata(label = "security", defaultValue = "true",
              description = "Whether a token from a header must match the token bound to the session, which prevents session"
                            + " fixation.")
    private boolean validateTokenBinding = true;
    /**
     * Allow token retrieval from HTTP headers. When disabled, tokens can only come from exchange properties. Disabling
     * this prevents attackers from injecting tokens via headers. Default is true for backward compatibility, but should
     * be set to false in production environments where tokens are set programmatically.
     */
    @Metadata(label = "security", defaultValue = "true",
              description = "Whether the token may come from a message header. Disable it where the route sets the token"
                            + " itself, so a caller cannot inject one through a header.")
    private boolean allowTokenFromHeader = true;
    /**
     * Prefer tokens from exchange properties over headers. When true, if a token exists in both property and header,
     * the property value is used. This prevents header-based token override attacks. Default is true for security.
     */
    @Metadata(label = "security", defaultValue = "true",
              description = "Whether a token in an exchange property wins over one in a header, which prevents a header"
                            + " from overriding the token the route established.")
    private boolean preferPropertyOverHeader = true;

    private Keycloak keycloakClient;
    private KeycloakTokenIntrospector tokenIntrospector;
    private KeycloakPublicKeyResolver publicKeyResolver;
    /**
     * Enable issuer validation to ensure tokens are issued by the expected realm. When enabled (default), tokens with
     * an issuer that does not match the configured serverUrl and realm will be rejected. This prevents cross-realm
     * token injection attacks in multi-tenant environments.
     */
    @Metadata(label = "security", defaultValue = "true",
              description = "Whether a token must have been issued by the configured serverUrl and realm, which prevents"
                            + " cross-realm token injection.")
    private boolean validateIssuer = true;
    /**
     * Enable automatic fetching of public keys from the Keycloak JWKS endpoint for signature verification. When enabled
     * (default), public keys are automatically fetched and cached from
     * {serverUrl}/realms/{realm}/protocol/openid-connect/certs. This ensures token signatures are properly verified.
     */
    @Metadata(label = "security", defaultValue = "true",
              description = "Whether to fetch the keys that verify token signatures from the realm's JWKS endpoint, and"
                            + " cache them.")
    private boolean autoFetchPublicKey = true;
    /**
     * Cache refresh interval for Keycloak JWKS public keys in seconds. Default is 300 seconds (5 minutes).
     */
    @Metadata(label = "advanced", defaultValue = "300",
              description = "How often the cached JWKS keys are refreshed, in seconds, given as a plain number rather than"
                            + " a time pattern such as 5m.")
    private long jwksRefreshInterval = 300;
    /**
     * Minimum cooldown interval in seconds between remote JWKS refreshes when an unknown key ID (kid) is encountered.
     * This rate-limits outbound calls and prevents JWKS cache stampede. Default is 10 seconds.
     */
    @Metadata(label = "advanced", defaultValue = "10",
              description = "The minimum time between two JWKS refreshes triggered by an unknown key id, which"
                            + " rate-limits the calls to Keycloak, in seconds, given as a plain number rather than a time"
                            + " pattern such as 5m.")
    private long minJwksRefreshInterval = 10;
    /**
     * Comma-separated list of expected audiences for token validation. When set, tokens whose "aud" claim does not
     * contain every one of the configured audiences are rejected (matching Keycloak's own audience verification
     * behavior). This prevents a token issued for one client from being accepted by routes intended for another client
     * in multi-client Keycloak realms. Disabled by default for backward compatibility. Example: "my-client"
     */
    @Metadata(label = "security",
              description = "Comma-separated list of audiences a token must all carry in its aud claim, so that a token"
                            + " issued for another client of the realm is rejected.")
    private String expectedAudience;

    /**
     * Comma-separated list of accepted token types ({@code typ} claim). When set, a token whose {@code typ} is not one
     * of the configured values is rejected. This guards against token-type confusion, e.g. an ID token or refresh token
     * being presented where an access token is expected. Disabled by default for backward compatibility. Example:
     * "Bearer"
     */
    @Metadata(label = "security",
              description = "Comma-separated list of the accepted token types (typ claim), for example Bearer, so that an"
                            + " ID or refresh token is not accepted where an access token is expected.")
    private String expectedTokenTypes;

    /**
     * Expected authorized party ({@code azp} claim). When set, a token whose {@code azp} does not equal the configured
     * value is rejected, ensuring the token was issued for the expected client. Disabled by default for backward
     * compatibility. Example: "my-client"
     */
    @Metadata(label = "security",
              description = "The client a token must have been issued for (azp claim).")
    private String expectedAuthorizedParty;

    public KeycloakSecurityPolicy() {
        this.requiredRoles = "";
        this.requiredPermissions = "";
    }

    public KeycloakSecurityPolicy(String serverUrl, String realm, String clientId, String clientSecret) {
        this();
        this.serverUrl = serverUrl;
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public KeycloakSecurityPolicy(String serverUrl, String realm, String clientId, String username, String password) {
        this();
        this.serverUrl = serverUrl;
        this.realm = realm;
        this.clientId = clientId;
        this.username = username;
        this.password = password;
        this.useResourceOwnerPasswordCredentials = true;
    }

    @Override
    public void beforeWrap(Route route, NamedNode definition) {
        // Initialize Keycloak clients if not already done
        if (keycloakClient == null) {
            initializeKeycloakClient();
        }
        // Initialize token introspector if introspection is enabled
        if (useTokenIntrospection && tokenIntrospector == null) {
            initializeTokenIntrospector();
        }
        // Initialize public key resolver for signature and issuer validation
        if (autoFetchPublicKey && publicKeyResolver == null) {
            initializePublicKeyResolver();
        }
    }

    @Override
    public Processor wrap(Route route, final Processor processor) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Securing route {} using Keycloak policy {}", route.getRouteId(), this);
        }
        return new KeycloakSecurityProcessor(processor, this);
    }

    private void initializeKeycloakClient() {
        if (useResourceOwnerPasswordCredentials && username != null && password != null) {
            keycloakClient = Keycloak.getInstance(serverUrl, realm, username, password, clientId);
        } else if (clientSecret != null) {
            keycloakClient = Keycloak.getInstance(serverUrl, realm, clientId, clientSecret);
        } else {
            throw new IllegalArgumentException(
                    "Either clientSecret or username/password must be provided for Keycloak authentication");
        }

        // Note: Permission-based authorization requires additional setup with Keycloak Authorization Services
        // For now, this implementation focuses on role-based authorization
    }

    private void initializeTokenIntrospector() {
        if (clientSecret == null) {
            throw new IllegalArgumentException(
                    "Client secret is required for token introspection");
        }
        tokenIntrospector = new KeycloakTokenIntrospector(
                serverUrl, realm, clientId, clientSecret,
                introspectionCacheEnabled, introspectionCacheTtl);
    }

    private void initializePublicKeyResolver() {
        if (serverUrl == null || realm == null) {
            throw new IllegalArgumentException(
                    "Server URL and realm are required for public key resolution");
        }
        publicKeyResolver = new KeycloakPublicKeyResolver(
                serverUrl, realm, jwksRefreshInterval * 1000L, minJwksRefreshInterval * 1000L);
        LOG.info("Initialized public key resolver for realm '{}' - issuer validation is {}",
                realm, validateIssuer ? "enabled" : "disabled");
    }

    // Getters and setters
    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String getRealm() {
        return realm;
    }

    public void setRealm(String realm) {
        this.realm = realm;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Gets the required roles as a comma-separated string.
     *
     * @return comma-separated roles (e.g., "admin,user,manager")
     */
    public String getRequiredRoles() {
        return requiredRoles;
    }

    /**
     * Sets the required roles as a comma-separated string.
     *
     * @param requiredRoles comma-separated roles (e.g., "admin,user,manager")
     */
    public void setRequiredRoles(String requiredRoles) {
        this.requiredRoles = requiredRoles != null ? requiredRoles : "";
    }

    /**
     * Sets the required roles from a list.
     *
     * @param requiredRoles list of roles
     */
    public void setRequiredRoles(List<String> requiredRoles) {
        this.requiredRoles = requiredRoles != null ? String.join(",", requiredRoles) : "";
    }

    /**
     * Gets the required roles as a list.
     *
     * @return list of required roles
     */
    public List<String> getRequiredRolesAsList() {
        if (ObjectHelper.isEmpty(requiredRoles)) {
            return Collections.emptyList();
        }
        return Arrays.stream(requiredRoles.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Gets the required permissions as a comma-separated string.
     *
     * @return comma-separated permissions (e.g., "read:documents,write:documents")
     */
    public String getRequiredPermissions() {
        return requiredPermissions;
    }

    /**
     * Sets the required permissions as a comma-separated string.
     *
     * @param requiredPermissions comma-separated permissions (e.g., "read:documents,write:documents")
     */
    public void setRequiredPermissions(String requiredPermissions) {
        this.requiredPermissions = requiredPermissions != null ? requiredPermissions : "";
    }

    /**
     * Sets the required permissions from a list.
     *
     * @param requiredPermissions list of permissions
     */
    public void setRequiredPermissions(List<String> requiredPermissions) {
        this.requiredPermissions = requiredPermissions != null ? String.join(",", requiredPermissions) : "";
    }

    /**
     * Gets the required permissions as a list.
     *
     * @return list of required permissions
     */
    public List<String> getRequiredPermissionsAsList() {
        if (ObjectHelper.isEmpty(requiredPermissions)) {
            return Collections.emptyList();
        }
        return Arrays.stream(requiredPermissions.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    public boolean isAllRolesRequired() {
        return allRolesRequired;
    }

    public void setAllRolesRequired(boolean allRolesRequired) {
        this.allRolesRequired = allRolesRequired;
    }

    public boolean isAllPermissionsRequired() {
        return allPermissionsRequired;
    }

    public void setAllPermissionsRequired(boolean allPermissionsRequired) {
        this.allPermissionsRequired = allPermissionsRequired;
    }

    public boolean isUseResourceOwnerPasswordCredentials() {
        return useResourceOwnerPasswordCredentials;
    }

    public void setUseResourceOwnerPasswordCredentials(boolean useResourceOwnerPasswordCredentials) {
        this.useResourceOwnerPasswordCredentials = useResourceOwnerPasswordCredentials;
    }

    public Keycloak getKeycloakClient() {
        return keycloakClient;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    public boolean isUseTokenIntrospection() {
        return useTokenIntrospection;
    }

    public void setUseTokenIntrospection(boolean useTokenIntrospection) {
        this.useTokenIntrospection = useTokenIntrospection;
    }

    public boolean isIntrospectionCacheEnabled() {
        return introspectionCacheEnabled;
    }

    public void setIntrospectionCacheEnabled(boolean introspectionCacheEnabled) {
        this.introspectionCacheEnabled = introspectionCacheEnabled;
    }

    public long getIntrospectionCacheTtl() {
        return introspectionCacheTtl;
    }

    public void setIntrospectionCacheTtl(long introspectionCacheTtl) {
        this.introspectionCacheTtl = introspectionCacheTtl;
    }

    public KeycloakTokenIntrospector getTokenIntrospector() {
        return tokenIntrospector;
    }

    public boolean isValidateTokenBinding() {
        return validateTokenBinding;
    }

    public void setValidateTokenBinding(boolean validateTokenBinding) {
        this.validateTokenBinding = validateTokenBinding;
    }

    public boolean isAllowTokenFromHeader() {
        return allowTokenFromHeader;
    }

    public void setAllowTokenFromHeader(boolean allowTokenFromHeader) {
        this.allowTokenFromHeader = allowTokenFromHeader;
    }

    public boolean isPreferPropertyOverHeader() {
        return preferPropertyOverHeader;
    }

    public void setPreferPropertyOverHeader(boolean preferPropertyOverHeader) {
        this.preferPropertyOverHeader = preferPropertyOverHeader;
    }

    public boolean isValidateIssuer() {
        return validateIssuer;
    }

    public void setValidateIssuer(boolean validateIssuer) {
        this.validateIssuer = validateIssuer;
    }

    public boolean isAutoFetchPublicKey() {
        return autoFetchPublicKey;
    }

    public void setAutoFetchPublicKey(boolean autoFetchPublicKey) {
        this.autoFetchPublicKey = autoFetchPublicKey;
    }

    public KeycloakPublicKeyResolver getPublicKeyResolver() {
        return publicKeyResolver;
    }

    /**
     * Returns the expected issuer URL for this policy's realm.
     *
     * @return the expected issuer URL (e.g., "http://localhost:8080/realms/myrealm")
     */
    public String getExpectedIssuer() {
        return serverUrl + "/realms/" + realm;
    }

    /**
     * Gets the expected audience(s) as a comma-separated string.
     *
     * @return comma-separated audiences (e.g., "my-client,my-other-client"), or null if not configured
     */
    public String getExpectedAudience() {
        return expectedAudience;
    }

    /**
     * Sets the expected audience(s) as a comma-separated string. When set, tokens whose "aud" claim does not contain
     * every one of the configured audiences are rejected.
     *
     * @param expectedAudience comma-separated audiences (e.g., "my-client,my-other-client")
     */
    public void setExpectedAudience(String expectedAudience) {
        this.expectedAudience = expectedAudience;
    }

    /**
     * Gets the expected audiences as a list.
     *
     * @return list of expected audiences, or an empty list if not configured
     */
    public List<String> getExpectedAudienceAsList() {
        if (ObjectHelper.isEmpty(expectedAudience)) {
            return Collections.emptyList();
        }
        return Arrays.stream(expectedAudience.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Gets the accepted token type(s) as a comma-separated string.
     *
     * @return comma-separated token types (e.g., "Bearer"), or null if not configured
     */
    public String getExpectedTokenTypes() {
        return expectedTokenTypes;
    }

    /**
     * Sets the accepted token type(s) as a comma-separated string. When set, a token whose "typ" claim is not one of
     * the configured values is rejected.
     *
     * @param expectedTokenTypes comma-separated token types (e.g., "Bearer")
     */
    public void setExpectedTokenTypes(String expectedTokenTypes) {
        this.expectedTokenTypes = expectedTokenTypes;
    }

    /**
     * Gets the accepted token types as a list.
     *
     * @return list of accepted token types, or an empty list if not configured
     */
    public List<String> getExpectedTokenTypesAsList() {
        if (ObjectHelper.isEmpty(expectedTokenTypes)) {
            return Collections.emptyList();
        }
        return Arrays.stream(expectedTokenTypes.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Gets the expected authorized party ({@code azp}).
     *
     * @return the expected authorized party, or null if not configured
     */
    public String getExpectedAuthorizedParty() {
        return expectedAuthorizedParty;
    }

    /**
     * Sets the expected authorized party ({@code azp}). When set, a token whose "azp" claim does not equal the
     * configured value is rejected.
     *
     * @param expectedAuthorizedParty the expected authorized party (e.g., "my-client")
     */
    public void setExpectedAuthorizedParty(String expectedAuthorizedParty) {
        this.expectedAuthorizedParty = expectedAuthorizedParty;
    }

    /**
     * Gets the JWKS cache refresh interval in seconds.
     *
     * @return the cache refresh interval in seconds
     */
    public long getJwksRefreshInterval() {
        return jwksRefreshInterval;
    }

    /**
     * Sets the JWKS cache refresh interval in seconds. Default is 300 seconds (5 minutes).
     *
     * @param jwksRefreshInterval cache refresh interval in seconds
     */
    public void setJwksRefreshInterval(long jwksRefreshInterval) {
        this.jwksRefreshInterval = jwksRefreshInterval;
    }

    /**
     * Gets the minimum cooldown interval in seconds between remote JWKS refreshes for unknown key IDs.
     *
     * @return the minimum cooldown interval in seconds
     */
    public long getMinJwksRefreshInterval() {
        return minJwksRefreshInterval;
    }

    /**
     * Sets the minimum cooldown interval in seconds between remote JWKS refreshes for unknown key IDs. Default is 10
     * seconds.
     *
     * @param minJwksRefreshInterval minimum cooldown interval in seconds
     */
    public void setMinJwksRefreshInterval(long minJwksRefreshInterval) {
        this.minJwksRefreshInterval = minJwksRefreshInterval;
    }
}
