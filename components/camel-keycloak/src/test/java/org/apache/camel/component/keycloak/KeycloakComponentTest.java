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
package org.apache.camel.component.keycloak;

import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

public class KeycloakComponentTest extends CamelTestSupport {

    @Test
    public void testCreateEndpointWithKeycloakClient() throws Exception {
        Keycloak keycloakClient = Mockito.mock(Keycloak.class);

        context.getRegistry().bind("keycloakClient", keycloakClient);

        KeycloakEndpoint endpoint = (KeycloakEndpoint) context.getEndpoint("keycloak:test?keycloakClient=#keycloakClient");

        assertNotNull(endpoint);
        assertEquals("test", endpoint.getConfiguration().getLabel());
        assertSame(keycloakClient, endpoint.getConfiguration().getKeycloakClient());
    }

    @Test
    public void testCreateEndpointWithCredentials() throws Exception {
        KeycloakEndpoint endpoint = (KeycloakEndpoint) context.getEndpoint(
                "keycloak:test?serverUrl=http://localhost:8080&username=admin&password=admin&realm=master");

        assertNotNull(endpoint);
        assertEquals("test", endpoint.getConfiguration().getLabel());
        assertEquals("http://localhost:8080", endpoint.getConfiguration().getServerUrl());
        assertEquals("admin", endpoint.getConfiguration().getUsername());
        assertEquals("admin", endpoint.getConfiguration().getPassword());
        assertEquals("master", endpoint.getConfiguration().getRealm());
    }

    @Test
    public void testCreateEndpointWithClientCredentials() throws Exception {
        KeycloakEndpoint endpoint = (KeycloakEndpoint) context.getEndpoint(
                "keycloak:test?serverUrl=http://localhost:8080&clientId=admin-cli&clientSecret=secret&realm=master");

        assertNotNull(endpoint);
        assertEquals("test", endpoint.getConfiguration().getLabel());
        assertEquals("http://localhost:8080", endpoint.getConfiguration().getServerUrl());
        assertEquals("admin-cli", endpoint.getConfiguration().getClientId());
        assertEquals("secret", endpoint.getConfiguration().getClientSecret());
        assertEquals("master", endpoint.getConfiguration().getRealm());
    }

    @Test
    public void testCreateEndpointWithOperation() throws Exception {
        Keycloak keycloakClient = Mockito.mock(Keycloak.class);

        context.getRegistry().bind("keycloakClient", keycloakClient);

        KeycloakEndpoint endpoint = (KeycloakEndpoint) context.getEndpoint(
                "keycloak:test?keycloakClient=#keycloakClient&operation=createUser");

        assertNotNull(endpoint);
        assertEquals(KeycloakOperations.createUser, endpoint.getConfiguration().getOperation());
    }

    @Test
    public void testCreateEndpointMissingCredentials() {
        assertThrows(ResolveEndpointFailedException.class, () -> {
            context.getEndpoint("keycloak:test?serverUrl=http://localhost:8080");
        });
    }
}
