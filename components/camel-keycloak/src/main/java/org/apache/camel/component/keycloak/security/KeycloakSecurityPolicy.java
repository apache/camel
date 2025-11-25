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
import org.apache.camel.util.ObjectHelper;
import org.keycloak.admin.client.Keycloak;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KeycloakSecurityPolicy implements AuthorizationPolicy {
    private static final Logger LOG = LoggerFactory.getLogger(KeycloakSecurityPolicy.class);

    private String serverUrl;
    private String realm;
    private String clientId;
    private String clientSecret;
    private String username;
    private String password;
    /**
     * Comma-separated list of required roles for authorization. Example: "admin,user,manager"
     */
    private String requiredRoles;
    /**
     * Comma-separated list of required permissions for authorization. Example: "read:documents,write:documents"
     */
    private String requiredPermissions;
    private boolean allRolesRequired = true;
    private boolean allPermissionsRequired = true;
    private boolean useResourceOwnerPasswordCredentials = false;
    private PublicKey publicKey;
    /**
     * Enable OAuth 2.0 token introspection for real-time token validation. When enabled, tokens are validated by
     * calling Keycloak's introspection endpoint instead of local JWT parsing. This allows detecting revoked tokens
     * before expiration.
     */
    private boolean useTokenIntrospection = false;
    /**
     * Enable caching of token introspection results to reduce API calls to Keycloak.
     */
    private boolean introspectionCacheEnabled = true;
    /**
     * Time-to-live for cached introspection results in seconds. Default is 60 seconds.
     */
    private long introspectionCacheTtl = 60;
    /**
     * Enable validation that tokens are bound to specific sessions. When enabled, tokens from headers must match the
     * session-bound token to prevent session fixation attacks. Default is true for security.
     */
    private boolean validateTokenBinding = true;
    /**
     * Allow token retrieval from HTTP headers. When disabled, tokens can only come from exchange properties. Disabling
     * this prevents attackers from injecting tokens via headers. Default is true for backward compatibility, but should
     * be set to false in production environments where tokens are set programmatically.
     */
    private boolean allowTokenFromHeader = true;
    /**
     * Prefer tokens from exchange properties over headers. When true, if a token exists in both property and header,
     * the property value is used. This prevents header-based token override attacks. Default is true for security.
     */
    private boolean preferPropertyOverHeader = true;

    private Keycloak keycloakClient;
    private KeycloakTokenIntrospector tokenIntrospector;

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
}
