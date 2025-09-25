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
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.spi.AuthorizationPolicy;
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
    private List<String> requiredRoles;
    private List<String> requiredPermissions;
    private boolean allRolesRequired = true;
    private boolean allPermissionsRequired = true;
    private boolean useResourceOwnerPasswordCredentials = false;
    private PublicKey publicKey;

    private Keycloak keycloakClient;

    public KeycloakSecurityPolicy() {
        this.requiredRoles = new ArrayList<>();
        this.requiredPermissions = new ArrayList<>();
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

    public List<String> getRequiredRoles() {
        return requiredRoles;
    }

    public void setRequiredRoles(List<String> requiredRoles) {
        this.requiredRoles = requiredRoles;
    }

    public List<String> getRequiredPermissions() {
        return requiredPermissions;
    }

    public void setRequiredPermissions(List<String> requiredPermissions) {
        this.requiredPermissions = requiredPermissions;
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
}
