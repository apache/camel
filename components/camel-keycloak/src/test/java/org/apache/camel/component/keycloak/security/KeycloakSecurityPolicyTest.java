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

import java.util.Arrays;
import java.util.List;

import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class KeycloakSecurityPolicyTest extends CamelTestSupport {

    @Test
    void testKeycloakSecurityPolicyConfiguration() {
        KeycloakSecurityPolicy policy = new KeycloakSecurityPolicy();
        policy.setServerUrl("http://localhost:8080");
        policy.setRealm("test-realm");
        policy.setClientId("test-client");
        policy.setClientSecret("test-secret");

        assertEquals("http://localhost:8080", policy.getServerUrl());
        assertEquals("test-realm", policy.getRealm());
        assertEquals("test-client", policy.getClientId());
        assertEquals("test-secret", policy.getClientSecret());
    }

    @Test
    void testKeycloakSecurityPolicyWithRoles() {
        KeycloakSecurityPolicy policy = new KeycloakSecurityPolicy();
        List<String> requiredRoles = Arrays.asList("admin", "user");
        policy.setRequiredRoles(requiredRoles);
        policy.setAllRolesRequired(true);

        assertEquals("admin,user", policy.getRequiredRoles());
        assertEquals(requiredRoles, policy.getRequiredRolesAsList());
        assertTrue(policy.isAllRolesRequired());
    }

    @Test
    void testKeycloakSecurityPolicyWithRolesAsCommaSeparatedString() {
        KeycloakSecurityPolicy policy = new KeycloakSecurityPolicy();
        policy.setRequiredRoles("admin,user,manager");
        policy.setAllRolesRequired(true);

        assertEquals("admin,user,manager", policy.getRequiredRoles());
        assertEquals(Arrays.asList("admin", "user", "manager"), policy.getRequiredRolesAsList());
        assertTrue(policy.isAllRolesRequired());
    }

    @Test
    void testKeycloakSecurityPolicyWithPermissions() {
        KeycloakSecurityPolicy policy = new KeycloakSecurityPolicy();
        List<String> requiredPermissions = Arrays.asList("read", "write");
        policy.setRequiredPermissions(requiredPermissions);
        policy.setAllPermissionsRequired(false);

        assertEquals("read,write", policy.getRequiredPermissions());
        assertEquals(requiredPermissions, policy.getRequiredPermissionsAsList());
        assertFalse(policy.isAllPermissionsRequired());
    }

    @Test
    void testKeycloakSecurityPolicyWithPermissionsAsCommaSeparatedString() {
        KeycloakSecurityPolicy policy = new KeycloakSecurityPolicy();
        policy.setRequiredPermissions("read:documents,write:documents,delete:documents");
        policy.setAllPermissionsRequired(false);

        assertEquals("read:documents,write:documents,delete:documents", policy.getRequiredPermissions());
        assertEquals(Arrays.asList("read:documents", "write:documents", "delete:documents"),
                policy.getRequiredPermissionsAsList());
        assertFalse(policy.isAllPermissionsRequired());
    }

    @Test
    void testRouteWithoutAuthorizationHeaderShouldFail() {
        CamelExecutionException ex = assertThrows(CamelExecutionException.class, () -> {
            template.sendBody("direct:protected", "test message");
        });
        assertTrue(ex.getCause() instanceof CamelAuthorizationException);
    }

    @Test
    void testResourceOwnerPasswordCredentialsConfiguration() {
        KeycloakSecurityPolicy policy = new KeycloakSecurityPolicy(
                "http://localhost:8080", "test-realm", "test-client", "testuser", "testpass");

        assertEquals("testuser", policy.getUsername());
        assertEquals("testpass", policy.getPassword());
        assertTrue(policy.isUseResourceOwnerPasswordCredentials());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        final KeycloakSecurityPolicy policy = new KeycloakSecurityPolicy();
        policy.setServerUrl("http://localhost:8080");
        policy.setRealm("test-realm");
        policy.setClientId("test-client");
        policy.setClientSecret("test-secret");

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:protected")
                        .policy(policy)
                        .to("mock:result");
            }
        };
    }
}
