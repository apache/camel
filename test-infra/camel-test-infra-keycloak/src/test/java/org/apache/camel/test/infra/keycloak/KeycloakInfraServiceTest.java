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
package org.apache.camel.test.infra.keycloak;

import org.apache.camel.test.infra.keycloak.services.KeycloakInfraService;
import org.apache.camel.test.infra.keycloak.services.KeycloakRemoteInfraService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class KeycloakInfraServiceTest {

    @Test
    public void testRemoteServiceConfiguration() {
        KeycloakInfraService service = new KeycloakRemoteInfraService(
                "http://localhost:8080",
                "master",
                "admin",
                "admin");

        assertEquals("http://localhost:8080", service.getKeycloakServerUrl());
        assertEquals("master", service.getKeycloakRealm());
        assertEquals("admin", service.getKeycloakUsername());
        assertEquals("admin", service.getKeycloakPassword());
    }

    @Test
    public void testRemoteServiceWithSystemProperties() {
        System.setProperty("keycloak.server.url", "http://test:8080");
        System.setProperty("keycloak.realm", "test");
        System.setProperty("keycloak.username", "testuser");
        System.setProperty("keycloak.password", "testpass");

        try {
            KeycloakInfraService service = new KeycloakRemoteInfraService();

            assertEquals("http://test:8080", service.getKeycloakServerUrl());
            assertEquals("test", service.getKeycloakRealm());
            assertEquals("testuser", service.getKeycloakUsername());
            assertEquals("testpass", service.getKeycloakPassword());
        } finally {
            System.clearProperty("keycloak.server.url");
            System.clearProperty("keycloak.realm");
            System.clearProperty("keycloak.username");
            System.clearProperty("keycloak.password");
        }
    }
}
