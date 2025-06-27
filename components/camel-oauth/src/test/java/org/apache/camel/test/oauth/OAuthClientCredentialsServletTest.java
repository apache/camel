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

import io.undertow.Undertow;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.oauth.OAuth;
import org.apache.camel.oauth.OAuthBearerTokenProcessor;
import org.apache.camel.oauth.OAuthClientCredentialsProcessor;
import org.apache.camel.oauth.OAuthFactory;
import org.apache.camel.spi.PropertiesComponent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.apache.camel.oauth.OAuth.CAMEL_OAUTH_BASE_URI;
import static org.apache.camel.oauth.OAuth.CAMEL_OAUTH_CLIENT_ID;
import static org.apache.camel.oauth.OAuth.CAMEL_OAUTH_CLIENT_SECRET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class OAuthClientCredentialsServletTest extends AbstractOAuthClientCredentialsTest {

    static Undertow server;

    @BeforeAll
    static void setUp() throws Exception {
        server = createUndertowServer();
        server.start();
    }

    @AfterAll
    static void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void testOAuthSetup() throws Exception {

        var admin = new KeycloakAdmin(new KeycloakAdmin.AdminParams(KEYCLOAK_BASE_URL));
        Assumptions.assumeTrue(admin.isKeycloakRunning(), "Keycloak is not running");

        try (CamelContext context = createCamelContext()) {
            context.start();

            var factory = OAuthFactory.lookupFactory(context);
            assertInstanceOf(OAuth.class, factory.createOAuth());

            var oauth = factory.findOAuth().orElseThrow();
            var config = oauth.getOAuthConfig();

            assertEquals(KEYCLOAK_REALM_URL, config.getBaseUrl());
            assertEquals(TEST_CLIENT_ID, config.getClientId());
            assertEquals(TEST_CLIENT_SECRET, config.getClientSecret());
            assertNotNull(config.getAuthorizationPath());
            assertNotNull(config.getIntrospectionPath());
        }
    }

    @Override
    CamelContext createCamelContext() throws Exception {

        var context = new DefaultCamelContext();

        PropertiesComponent props = context.getPropertiesComponent();
        props.addInitialProperty(CAMEL_OAUTH_BASE_URI, KEYCLOAK_REALM_URL);
        props.addInitialProperty(CAMEL_OAUTH_CLIENT_ID, TEST_CLIENT_ID);
        props.addInitialProperty(CAMEL_OAUTH_CLIENT_SECRET, TEST_CLIENT_SECRET);

        return context;
    }

    @Override
    void addOAuthClientCredentialsRoutes(CamelContext context) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("servlet:/plain")
                        .routeId("plain")
                        .setBody(simple("${body} - No auth"));
                from("servlet:/creds")
                        .routeId("creds")
                        // Obtain an Authorization Token
                        .process(new OAuthClientCredentialsProcessor())
                        // Extract the Authorization Token
                        .process(exc -> {
                            var msg = exc.getMessage();
                            var authToken = msg.getHeader("Authorization", String.class);
                            context.getGlobalOptions().put("Authorization", authToken);
                        })
                        .setBody(simple("${body} - OAuthClientCredentials"));
                from("servlet:/bearer")
                        .routeId("bearer")
                        .process(new OAuthBearerTokenProcessor())
                        .setBody(simple("${body} - OAuthBearerToken"));
            }
        });
    }
}
