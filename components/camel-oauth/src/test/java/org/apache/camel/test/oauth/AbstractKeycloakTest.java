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

import jakarta.servlet.ServletException;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.Servlets;
import org.apache.camel.component.servlet.CamelHttpTransportServlet;
import org.junit.jupiter.api.Assumptions;

import static org.apache.camel.test.oauth.KeycloakAdmin.AdminParams;
import static org.apache.camel.test.oauth.KeycloakAdmin.ClientParams;
import static org.apache.camel.test.oauth.KeycloakAdmin.RealmParams;
import static org.apache.camel.test.oauth.KeycloakAdmin.UserParams;

abstract class AbstractKeycloakTest {

    static final int port = 8080; // AvailablePortFinder.getNextAvailable();
    static final String APP_BASE_URL = "http://127.0.0.1:" + port + "/";

    static final String KEYCLOAK_REALM = "camel";
    static final String KEYCLOAK_BASE_URL = "https://keycloak.local/kc/";
    static final String KEYCLOAK_REALM_URL = KEYCLOAK_BASE_URL + "realms/" + KEYCLOAK_REALM;
    static final String TEST_CLIENT_ID = "camel-client";
    static final String TEST_CLIENT_SECRET = "camel-client-secret";

    private KeycloakAdmin admin;
    private boolean removeRealm;

    protected KeycloakAdmin getKeycloakAdmin() {
        if (admin == null) {
            admin = new KeycloakAdmin(new AdminParams(KEYCLOAK_BASE_URL));
        }
        return admin;
    }

    protected void setupKeycloakRealm() throws Exception {

        var admin = getKeycloakAdmin();
        Assumptions.assumeTrue(admin.isKeycloakRunning(), "Keycloak is not running");

        // Setup Keycloak realm, client, user
        //
        if (!admin.realmExists(KEYCLOAK_REALM)) {
            admin.withRealm(new RealmParams(KEYCLOAK_REALM))
                    .withUser(new UserParams("alice")
                            .setEmail("alice@example.com")
                            .setFirstName("Alice")
                            .setLastName("Brown"))
                    .withClient(new ClientParams(TEST_CLIENT_ID)
                            .setClientSecret(TEST_CLIENT_SECRET)
                            .setLogoutRedirectUri(APP_BASE_URL)
                            .setServiceAccountsEnabled(true)
                            .setRedirectUri(APP_BASE_URL + "auth"));
            removeRealm = true;
        }
    }

    protected void removeKeycloakRealm() {
        if (admin != null && removeRealm) {
            admin.removeRealm().close();
        }
    }

    protected static Undertow createUndertowServer() throws ServletException {
        var deploymentInfo = Servlets.deployment()
                .setContextPath("/")
                .setDeploymentName("CamelServlet")
                .setClassLoader(OAuthClientCredentialsServletTest.class.getClassLoader())
                .addServlet(Servlets.servlet("CamelServlet", CamelHttpTransportServlet.class).addMapping("/*"));

        var manager = Servlets.newContainer().addDeployment(deploymentInfo);
        manager.deploy();

        PathHandler path = Handlers.path(Handlers.redirect("/")).addPrefixPath("/", manager.start());
        return Undertow.builder().addHttpListener(port, "0.0.0.0").setHandler(path).build();
    }
}
