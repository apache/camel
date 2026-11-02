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
package org.apache.camel.component.netty.http;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit6.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NettyHttpSharedSecurityConfigurationTest extends BaseNettyTestSupport {

    @Override
    public void doPreSetup() {
        System.setProperty("java.security.auth.login.config", "src/test/resources/myjaas.config");
    }

    @Override
    public void doPostTearDown() {
        System.clearProperty("java.security.auth.login.config");
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        NettyHttpComponent component = context.getComponent("netty-http", NettyHttpComponent.class);

        NettyHttpSecurityConfiguration security = new NettyHttpSecurityConfiguration();
        security.setRealm("karaf");
        security.setAuthenticate(true);
        SecurityAuthenticator auth = new JAASSecurityAuthenticator();
        auth.setName("karaf");
        security.setSecurityAuthenticator(auth);

        component.setSecurityConfiguration(security);
        return context;
    }

    @Test
    void testSecurityConfigurationCopiedPerEndpoint() {
        NettyHttpComponent component = context.getComponent("netty-http", NettyHttpComponent.class);
        NettyHttpEndpoint adminEndpoint = context.getEndpoint("netty-http:http://0.0.0.0:" + getPort() + "/admin",
                NettyHttpEndpoint.class);
        NettyHttpEndpoint publicEndpoint = context.getEndpoint(
                "netty-http:http://0.0.0.0:" + getPort() + "/public?securityConfiguration.authenticate=false",
                NettyHttpEndpoint.class);

        // Component security configuration must remain authenticate = true
        assertTrue(component.getSecurityConfiguration().isAuthenticate());

        // Admin endpoint must remain authenticate = true
        assertTrue(adminEndpoint.getSecurityConfiguration().isAuthenticate());

        // Public endpoint must have authenticate = false
        assertFalse(publicEndpoint.getSecurityConfiguration().isAuthenticate());

        // Endpoints must have distinct configuration instances
        assertNotSame(component.getSecurityConfiguration(), adminEndpoint.getSecurityConfiguration());
        assertNotSame(component.getSecurityConfiguration(), publicEndpoint.getSecurityConfiguration());
        assertNotSame(adminEndpoint.getSecurityConfiguration(), publicEndpoint.getSecurityConfiguration());
    }

    @Test
    void testAuthenticationEnforcement() {
        // Unauthenticated request to admin route should fail with 401
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("netty-http:http://localhost:{{port}}/admin", "Hello Admin", String.class));
        NettyHttpOperationFailedException cause = assertIsInstanceOf(NettyHttpOperationFailedException.class, e.getCause());
        assertEquals(401, cause.getStatusCode());

        // Authenticated request to admin route should succeed
        String auth = "Basic c2NvdHQ6c2VjcmV0";
        String adminResponse = template.requestBodyAndHeader("netty-http:http://localhost:{{port}}/admin", "Hello Admin",
                "Authorization", auth, String.class);
        assertEquals("Admin OK", adminResponse);

        // Unauthenticated request to public route should succeed
        String publicResponse
                = template.requestBody("netty-http:http://localhost:{{port}}/public", "Hello Public", String.class);
        assertEquals("Public OK", publicResponse);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("netty-http:http://0.0.0.0:{{port}}/admin")
                        .transform().constant("Admin OK");

                from("netty-http:http://0.0.0.0:{{port}}/public?securityConfiguration.authenticate=false")
                        .transform().constant("Public OK");
            }
        };
    }
}
