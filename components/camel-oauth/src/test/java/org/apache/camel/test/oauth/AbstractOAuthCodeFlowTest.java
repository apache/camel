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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Test OIDC CodeFlow for a simple WebApp deployed on platform-http
 */
abstract class AbstractOAuthCodeFlowTest extends AbstractKeycloakTest {

    @Test
    void testCodeFlowAuth() throws Exception {

        var admin = new KeycloakAdmin(new KeycloakAdmin.AdminParams(KEYCLOAK_BASE_URL));
        Assumptions.assumeTrue(admin.isKeycloakRunning(), "Keycloak is not running");

        try (CamelContext context = createCamelContext()) {
            addOAuthCodeFlowRoutes(context);
            context.start();

            // Verify Realm, Client, and User exist
            var keycloak = getKeycloakAdmin().getKeycloak();
            Assertions.assertNotNull(keycloak.realm(KEYCLOAK_REALM).toRepresentation());
            Assertions.assertEquals(1, keycloak.realm(KEYCLOAK_REALM).clients().findByClientId(TEST_CLIENT_ID).size());
            Assertions.assertEquals(1, keycloak.realm(KEYCLOAK_REALM).users().search("alice").size());

            System.out.println("✅ Keycloak realm, client, and user available!");
            System.out.println("✅ Open: " + APP_BASE_URL);

            // Open WebApp in Browser (works on macOS)
            // Runtime.getRuntime().exec("open " + APP_BASE_URL);

            // Increase for manual testing
            int maxLoopCount = 10; // 500ms per loop

            for (int i = maxLoopCount; i > 0; i--) {
                var options = context.getGlobalOptions();
                if ("ok".equals(options.get("OAuthLogout"))) {
                    System.out.println("✅ OAuthLogout - ok");
                    i = 0;
                }
                if (i % 4 == 0) {
                    System.out.printf("Waiting on logout: %d/%d - %s%n", maxLoopCount - i, maxLoopCount,
                            APP_BASE_URL + "logout");
                }
                Thread.sleep(500L);
            }
        }
    }

    abstract CamelContext createCamelContext() throws Exception;

    abstract void addOAuthCodeFlowRoutes(CamelContext context) throws Exception;
}
