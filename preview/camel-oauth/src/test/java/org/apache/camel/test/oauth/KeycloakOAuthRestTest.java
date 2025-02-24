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

import java.util.Map;

import com.nimbusds.jose.util.JSONObjectUtils;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.platform.http.main.MainHttpServer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.oauth.OAuthBearerTokenProcessor;
import org.apache.camel.oauth.OAuthClientCredentialsProcessor;
import org.apache.camel.spi.PropertiesComponent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.apache.camel.oauth.OAuth.CAMEL_OAUTH_CLIENT_ID;
import static org.apache.camel.oauth.OAuth.CAMEL_OAUTH_CLIENT_SECRET;
import static org.apache.camel.oauth.OAuth.CAMEL_OAUTH_PROVIDER_BASE_URI;
import static org.apache.camel.oauth.OAuth.CAMEL_OAUTH_SESSION_ID;

class KeycloakOAuthRestTest extends AbstractKeycloakTest {

    private static CamelContext camelContext;

    @BeforeAll
    static void setUp() throws Exception {

        setupKeycloakRealm();

        camelContext = new DefaultCamelContext();
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {

                // Expose a REST endpoint on platform-http
                restConfiguration().component("platform-http");

                rest("/provider")
                        .get("/data")
                        .to("direct:processData");

                from("direct:processData")
                        .process(new OAuthBearerTokenProcessor())
                        .setBody(exc -> Map.of("msg", "Hello " + exc.getMessage().getBody(String.class)))
                        .marshal().json(JsonLibrary.Jackson)
                        .setHeader("Content-Type", constant("application/json"));

                from("direct:start")
                        .process(new OAuthClientCredentialsProcessor())
                        .setHeader("CamelHttpMethod", constant("GET"))
                        .to("vertx-http:http://127.0.0.1:" + port + "/provider/data")
                        .log("Response: ${body}");
            }
        });

        PropertiesComponent props = camelContext.getPropertiesComponent();
        props.addInitialProperty(CAMEL_OAUTH_PROVIDER_BASE_URI, KEYCLOAK_BASE_URL + "realms/" + TEST_REALM);
        props.addInitialProperty(CAMEL_OAUTH_CLIENT_ID, TEST_CLIENT_ID);
        props.addInitialProperty(CAMEL_OAUTH_CLIENT_SECRET, TEST_CLIENT_SECRET);

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
    void testClientCredentialsAuth() throws Exception {

        // Verify Realm, Client, and User exist
        var keycloak = admin.getKeycloak();
        Assertions.assertNotNull(keycloak.realm(TEST_REALM).toRepresentation());
        Assertions.assertEquals(1, keycloak.realm(TEST_REALM).clients().findByClientId(TEST_CLIENT_ID).size());
        Assertions.assertEquals(1, keycloak.realm(TEST_REALM).users().search("alice").size());

        System.out.println("✅ Keycloak realm, client, and user created successfully!");

        ProducerTemplate template = camelContext.createProducerTemplate();
        var res = template.request("direct:start", exc -> exc.getIn().setBody("Kermit"));
        var msg = res.getMessage();

        var body = msg.getBody(String.class);
        Assertions.assertEquals("Hello Kermit", JSONObjectUtils.parse(body).get("msg"));

        // Assert that the CamelOAuthSessionId is present
        var sessionId = msg.getHeader(CAMEL_OAUTH_SESSION_ID, String.class);
        Assertions.assertNotNull(sessionId);

        // Set the CamelOAuthSessionId on the second run, which should shortcut
        // client credential authentication with much faster token introspection
        body = template.requestBodyAndHeader("direct:start", "Kermit", CAMEL_OAUTH_SESSION_ID, sessionId, String.class);
        Assertions.assertEquals("Hello Kermit", JSONObjectUtils.parse(body).get("msg"));
    }
}
