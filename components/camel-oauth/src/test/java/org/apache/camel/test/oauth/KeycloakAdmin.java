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
package org.apache.camel.test.oauth;

import java.util.ArrayList;
import java.util.List;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

class KeycloakAdmin {

    private static Keycloak keycloak;
    private String lastRealm;

    public KeycloakAdmin(AdminParams params) {
        keycloak = KeycloakBuilder.builder()
                .serverUrl(params.serverUrl)
                .realm(params.realm)
                .clientId(params.clientId)
                .grantType(OAuth2Constants.PASSWORD)
                .username(params.username)
                .password(params.password)
                .build();
    }

    public Keycloak getKeycloak() {
        return keycloak;
    }

    public KeycloakAdmin withRealm(RealmParams params) {
        RealmRepresentation realm = new RealmRepresentation();
        realm.setRealm(params.realm);
        realm.setEnabled(true);
        getKeycloak().realms().create(realm);
        this.lastRealm = params.realm;
        return this;
    }

    public KeycloakAdmin withClient(ClientParams params) {
        List<String> redirectUris = new ArrayList<>(List.of(params.redirectUri));
        if (params.logoutRedirectUri != null) {
            redirectUris.add(params.logoutRedirectUri);
        }
        ClientRepresentation client = new ClientRepresentation();
        client.setClientId(params.clientId);
        client.setSecret(params.clientSecret);
        client.setPublicClient(params.publicClient);
        client.setServiceAccountsEnabled(params.serviceAccounts);
        client.setRedirectUris(redirectUris);
        client.setEnabled(true);
        getKeycloak().realm(lastRealm).clients().create(client);
        return this;
    }

    public KeycloakAdmin withUser(UserParams params) {

        if (params.password == null) {
            params.password = params.username;
        }

        UsersResource users = getKeycloak().realm(lastRealm).users();

        UserRepresentation user = new UserRepresentation();
        user.setUsername(params.username);
        user.setEmail(params.email);
        user.setEmailVerified(true);
        user.setFirstName(params.firstName);
        user.setLastName(params.lastName);
        user.setEnabled(true);
        users.create(user);

        String userId = users.search(params.username).get(0).getId();

        CredentialRepresentation password = new CredentialRepresentation();
        password.setType(CredentialRepresentation.PASSWORD);
        password.setValue(params.password);
        password.setTemporary(false);
        users.get(userId).resetPassword(password);

        return this;
    }

    public KeycloakAdmin removeRealm() {
        if (lastRealm != null) {
            getKeycloak().realm(lastRealm).remove();
            lastRealm = null;
        }
        return this;
    }

    public void close() {
        getKeycloak().close();
        keycloak = null;
    }

    public boolean isKeycloakRunning() {
        try {
            var serverInfo = getKeycloak().serverInfo().getInfo();
            return serverInfo != null;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean realmExists(String realm) {
        try {
            var realmRepresentation = getKeycloak().realm(realm).toRepresentation();
            return realmRepresentation != null;
        } catch (Exception e) {
            return false;
        }
    }

    public static class AdminParams {
        String serverUrl;
        String realm = "master";
        String clientId = "admin-cli";
        String username = "admin";
        String password = "admin";

        public AdminParams(String serverUrl) {
            this.serverUrl = serverUrl;
        }

        public AdminParams setUsername(String username) {
            this.username = username;
            return this;
        }

        public AdminParams setPassword(String password) {
            this.password = password;
            return this;
        }
    }

    public static class RealmParams {
        String realm;

        public RealmParams(String realm) {
            this.realm = realm;
        }
    }

    public static class ClientParams {
        String clientId;
        String clientSecret;
        String redirectUri;
        String logoutRedirectUri;
        boolean publicClient;
        boolean serviceAccounts;

        public ClientParams(String clientId) {
            this.clientId = clientId;
        }

        public ClientParams setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
            return this;
        }

        public ClientParams setPublicClient(boolean enabled) {
            this.publicClient = enabled;
            return this;
        }

        public ClientParams setRedirectUri(String redirectUri) {
            this.redirectUri = redirectUri;
            return this;
        }

        public ClientParams setLogoutRedirectUri(String redirectUri) {
            this.logoutRedirectUri = redirectUri;
            return this;
        }

        public ClientParams setServiceAccountsEnabled(boolean enabled) {
            this.serviceAccounts = enabled;
            return this;
        }
    }

    public static class UserParams {
        String username;
        String password;
        String email;
        String firstName;
        String lastName;

        public UserParams(String username) {
            this.username = username;
        }

        public UserParams setPassword(String password) {
            this.password = password;
            return this;
        }

        public UserParams setEmail(String email) {
            this.email = email;
            return this;
        }

        public UserParams setFirstName(String firstName) {
            this.firstName = firstName;
            return this;
        }

        public UserParams setLastName(String lastName) {
            this.lastName = lastName;
            return this;
        }
    }
}
