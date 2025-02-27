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

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.platform.http.main.MainHttpServer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.oauth.OAuthCodeFlowCallbackProcessor;
import org.apache.camel.oauth.OAuthCodeFlowProcessor;
import org.apache.camel.oauth.OAuthLogoutProcessor;
import org.apache.camel.spi.PropertiesComponent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.apache.camel.oauth.OAuth.CAMEL_OAUTH_CLIENT_ID;
import static org.apache.camel.oauth.OAuth.CAMEL_OAUTH_CLIENT_SECRET;
import static org.apache.camel.oauth.OAuth.CAMEL_OAUTH_LOGOUT_REDIRECT_URI;
import static org.apache.camel.oauth.OAuth.CAMEL_OAUTH_PROVIDER_BASE_URI;
import static org.apache.camel.oauth.OAuth.CAMEL_OAUTH_REDIRECT_URI;

class KeycloakOAuthWebAppTest extends AbstractKeycloakTest {

    private static CamelContext camelContext;

    @BeforeAll
    static void setUp() throws Exception {

        setupKeycloakRealm();

        camelContext = new DefaultCamelContext();
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("platform-http:/")
                        .setBody(simple("resource:classpath:index.html"));
                from("platform-http:/static/styles.css")
                        .setBody(simple("resource:classpath:styles.css"));
                from("platform-http:/auth")
                        .process(new OAuthCodeFlowCallbackProcessor());
                from("platform-http:/protected")
                        .process(new OAuthCodeFlowProcessor())
                        .setBody(simple("resource:classpath:protected.html"));
                from("platform-http:/logout")
                        .process(new OAuthLogoutProcessor())
                        .process(exc -> exc.getContext().getGlobalOptions().put("OAuthLogout", "ok"));
            }
        });

        PropertiesComponent props = camelContext.getPropertiesComponent();
        props.addInitialProperty(CAMEL_OAUTH_PROVIDER_BASE_URI, KEYCLOAK_BASE_URL + "realms/" + TEST_REALM);
        props.addInitialProperty(CAMEL_OAUTH_REDIRECT_URI, APP_BASE_URL + "auth");
        props.addInitialProperty(CAMEL_OAUTH_CLIENT_ID, TEST_CLIENT_ID);
        props.addInitialProperty(CAMEL_OAUTH_CLIENT_SECRET, TEST_CLIENT_SECRET);
        props.addInitialProperty(CAMEL_OAUTH_LOGOUT_REDIRECT_URI, APP_BASE_URL);

        MainHttpServer httpServer = new MainHttpServer();
        httpServer.setPort(port);

        camelContext.addService(httpServer);
        camelContext.start();
    }

    @AfterAll
    static void tearDown() {
        if (camelContext != null) {
            camelContext.stop();
        }
        removeKeycloakRealm();
    }

    @Test
    void testCodeFlowAuth() throws Exception {

        // Verify Realm, Client, and User exist
        var keycloak = admin.getKeycloak();
        Assertions.assertNotNull(keycloak.realm(TEST_REALM).toRepresentation());
        Assertions.assertEquals(1, keycloak.realm(TEST_REALM).clients().findByClientId(TEST_CLIENT_ID).size());
        Assertions.assertEquals(1, keycloak.realm(TEST_REALM).users().search("alice").size());

        System.out.println("✅ Keycloak realm, client, and user created successfully!");
        System.out.println("✅ Open: " + APP_BASE_URL);

        // Open WebApp in Browser (works on macOS)
        // Runtime.getRuntime().exec("open " + APP_BASE_URL);

        // Increase for manual testing
        int maxLoopCount = 10;

        for (int i = maxLoopCount; i > 0; i--) { // timeout after 20sec
            var options = camelContext.getGlobalOptions();
            if ("ok".equals(options.get("OAuthLogout"))) {
                System.out.println("✅ OAuthLogout - ok");
                i = 0;
            }
            if (i % 4 == 0) {
                System.out.printf("Waiting on logout: %d/%d - %s%n", maxLoopCount - i, maxLoopCount, APP_BASE_URL + "logout");
            }
            Thread.sleep(500L);
        }
    }
}
