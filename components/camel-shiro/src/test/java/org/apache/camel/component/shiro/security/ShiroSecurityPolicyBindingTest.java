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
package org.apache.camel.component.shiro.security;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.PropertyBindingSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Binds the policy the way a YAML {@code beans:} entry does, where every value arrives as a string.
 */
class ShiroSecurityPolicyBindingTest {

    @Test
    void bindsTheOptionsAsAYamlBeanDefinitionGivesThem() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("passPhrase", "CamelSecureRoute");
            properties.put("alwaysReauthenticate", "false");
            properties.put("base64", "true");
            properties.put("allRolesRequired", "true");
            properties.put("rolesList[0]", "admin");
            properties.put("rolesList[1]", "auditor");

            ShiroSecurityPolicy policy = new ShiroSecurityPolicy("classpath:securityconfig.ini");
            PropertyBindingSupport.setPropertiesOnTarget(context, policy, properties);

            // the generated configurer binds the options, not reflection: it is found by the class name
            PropertyConfigurer configurer = PluginHelper.getConfigurerResolver(context)
                    .resolvePropertyConfigurer(ShiroSecurityPolicy.class.getName(), context);
            assertNotNull(configurer);
            assertEquals("ShiroSecurityPolicyConfigurer", configurer.getClass().getSimpleName());

            assertArrayEquals("CamelSecureRoute".getBytes(StandardCharsets.UTF_8), policy.getPassPhrase());
            assertFalse(policy.isAlwaysReauthenticate());
            assertTrue(policy.isBase64());
            assertTrue(policy.isAllRolesRequired());
            assertFalse(policy.isAllPermissionsRequired());
            assertEquals(List.of("admin", "auditor"), policy.getRolesList());
        }
    }

    @Test
    void reauthenticatesByDefault() {
        // the catalog documents the default the constructor sets, not the field's
        assertTrue(new ShiroSecurityPolicy().isAlwaysReauthenticate());
    }
}
