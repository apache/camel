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

package org.apache.camel.test.infra.keycloak.services;

import org.apache.camel.test.infra.keycloak.common.KeycloakProperties;
import org.keycloak.admin.client.Keycloak;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Remote Keycloak infrastructure service for testing with external Keycloak instances
 */
public class KeycloakRemoteInfraService implements KeycloakInfraService {

    private static final Logger LOG = LoggerFactory.getLogger(KeycloakRemoteInfraService.class);

    private final String keycloakServerUrl;
    private final String keycloakRealm;
    private final String keycloakUsername;
    private final String keycloakPassword;

    public KeycloakRemoteInfraService() {
        this(
                System.getProperty(KeycloakProperties.KEYCLOAK_SERVER_URL),
                System.getProperty(KeycloakProperties.KEYCLOAK_REALM, "master"),
                System.getProperty(KeycloakProperties.KEYCLOAK_USERNAME),
                System.getProperty(KeycloakProperties.KEYCLOAK_PASSWORD));
    }

    public KeycloakRemoteInfraService(
            String keycloakServerUrl, String keycloakRealm, String keycloakUsername, String keycloakPassword) {
        this.keycloakServerUrl = keycloakServerUrl;
        this.keycloakRealm = keycloakRealm;
        this.keycloakUsername = keycloakUsername;
        this.keycloakPassword = keycloakPassword;
    }

    @Override
    public void registerProperties() {
        System.setProperty(KeycloakProperties.KEYCLOAK_SERVER_URL, getKeycloakServerUrl());
        System.setProperty(KeycloakProperties.KEYCLOAK_REALM, getKeycloakRealm());
        System.setProperty(KeycloakProperties.KEYCLOAK_USERNAME, getKeycloakUsername());
        System.setProperty(KeycloakProperties.KEYCLOAK_PASSWORD, getKeycloakPassword());
    }

    @Override
    public void initialize() {
        LOG.info("Using remote Keycloak instance at {}", getKeycloakServerUrl());
        registerProperties();
    }

    @Override
    public void shutdown() {
        LOG.info("Remote Keycloak service shutdown (no-op)");
    }

    @Override
    public String getKeycloakServerUrl() {
        return keycloakServerUrl;
    }

    @Override
    public String getKeycloakRealm() {
        return keycloakRealm;
    }

    @Override
    public String getKeycloakUsername() {
        return keycloakUsername;
    }

    @Override
    public String getKeycloakPassword() {
        return keycloakPassword;
    }

    @Override
    public Keycloak getKeycloakAdminClient() {
        return Keycloak.getInstance(
                getKeycloakServerUrl(), getKeycloakRealm(), getKeycloakUsername(), getKeycloakPassword(), "admin-cli");
    }
}
