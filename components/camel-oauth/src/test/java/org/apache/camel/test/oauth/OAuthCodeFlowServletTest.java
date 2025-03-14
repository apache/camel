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
import org.apache.camel.oauth.OAuthCodeFlowCallback;
import org.apache.camel.oauth.OAuthCodeFlowProcessor;
import org.apache.camel.oauth.OAuthLogoutProcessor;
import org.apache.camel.spi.PropertiesComponent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import static org.apache.camel.oauth.OAuth.CAMEL_OAUTH_BASE_URI;
import static org.apache.camel.oauth.OAuth.CAMEL_OAUTH_CLIENT_ID;
import static org.apache.camel.oauth.OAuth.CAMEL_OAUTH_CLIENT_SECRET;
import static org.apache.camel.oauth.OAuth.CAMEL_OAUTH_LOGOUT_REDIRECT_URI;
import static org.apache.camel.oauth.OAuth.CAMEL_OAUTH_REDIRECT_URI;

/**
 * Test OIDC CodeFlow for a simple WebApp deployed on servlet
 */
class OAuthCodeFlowServletTest extends AbstractOAuthCodeFlowTest {

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

    @Override
    CamelContext createCamelContext() throws Exception {

        var context = new DefaultCamelContext();

        PropertiesComponent props = context.getPropertiesComponent();
        props.addInitialProperty(CAMEL_OAUTH_BASE_URI, KEYCLOAK_REALM_URL);
        props.addInitialProperty(CAMEL_OAUTH_CLIENT_ID, TEST_CLIENT_ID);
        props.addInitialProperty(CAMEL_OAUTH_CLIENT_SECRET, TEST_CLIENT_SECRET);
        props.addInitialProperty(CAMEL_OAUTH_REDIRECT_URI, APP_BASE_URL + "auth");
        props.addInitialProperty(CAMEL_OAUTH_LOGOUT_REDIRECT_URI, APP_BASE_URL);

        return context;
    }

    @Override
    void addOAuthCodeFlowRoutes(CamelContext context) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("servlet:/")
                        .setBody(simple("resource:classpath:index.html"));
                from("servlet:/static/styles.css")
                        .setBody(simple("resource:classpath:styles.css"));
                from("servlet:/auth")
                        .process(new OAuthCodeFlowCallback());
                from("servlet:/protected")
                        .process(new OAuthCodeFlowProcessor())
                        .setBody(simple("resource:classpath:protected.html"));
                from("servlet:/logout")
                        .process(new OAuthLogoutProcessor())
                        .process(exc -> exc.getContext().getGlobalOptions().put("OAuthLogout", "ok"));
            }
        });
    }
}
