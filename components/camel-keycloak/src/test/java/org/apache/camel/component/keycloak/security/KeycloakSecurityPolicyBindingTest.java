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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.PropertyBindingSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Binds the policy the way a YAML {@code beans:} entry does, where every value arrives as a string.
 */
class KeycloakSecurityPolicyBindingTest {

    @Test
    void bindsTheOptionsAsAYamlBeanDefinitionGivesThem() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("serverUrl", "https://keycloak:8443");
            properties.put("realm", "camel");
            properties.put("clientId", "camel-client");
            properties.put("clientSecret", "s3cr3t");
            // a string, although setRequiredRoles and setRequiredPermissions also take a List
            properties.put("requiredRoles", "admin,user");
            properties.put("requiredPermissions", "read:documents");
            properties.put("allRolesRequired", "false");
            properties.put("useTokenIntrospection", "true");
            properties.put("introspectionCacheTtl", "120");
            properties.put("allowTokenFromHeader", "false");
            properties.put("validateIssuer", "true");
            properties.put("jwksRefreshInterval", "600");
            properties.put("minJwksRefreshInterval", "30");
            properties.put("expectedAudience", "camel-client");
            properties.put("expectedTokenTypes", "Bearer");
            properties.put("expectedAuthorizedParty", "camel-client");

            KeycloakSecurityPolicy policy = new KeycloakSecurityPolicy();
            PropertyBindingSupport.setPropertiesOnTarget(context, policy, properties);

            assertEquals("https://keycloak:8443", policy.getServerUrl());
            assertEquals("camel", policy.getRealm());
            assertEquals("camel-client", policy.getClientId());
            assertEquals("s3cr3t", policy.getClientSecret());
            assertEquals(List.of("admin", "user"), policy.getRequiredRolesAsList());
            assertEquals(List.of("read:documents"), policy.getRequiredPermissionsAsList());
            assertFalse(policy.isAllRolesRequired());
            assertTrue(policy.isAllPermissionsRequired());
            assertTrue(policy.isUseTokenIntrospection());
            assertEquals(120L, policy.getIntrospectionCacheTtl());
            assertFalse(policy.isAllowTokenFromHeader());
            assertTrue(policy.isValidateIssuer());
            assertEquals(600L, policy.getJwksRefreshInterval());
            assertEquals(30L, policy.getMinJwksRefreshInterval());
            assertEquals("camel-client", policy.getExpectedAudience());
            assertEquals("Bearer", policy.getExpectedTokenTypes());
            assertEquals("camel-client", policy.getExpectedAuthorizedParty());
        }
    }
}
