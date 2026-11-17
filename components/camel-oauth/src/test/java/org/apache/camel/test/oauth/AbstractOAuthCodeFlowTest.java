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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test OIDC CodeFlow for a simple WebApp deployed on platform-http
 */
abstract class AbstractOAuthCodeFlowTest extends AbstractKeycloakTest {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractOAuthCodeFlowTest.class);

    @Test
    void testCodeFlowAuth() throws Exception {

        var admin = new KeycloakAdmin(new KeycloakAdmin.AdminParams(KEYCLOAK_BASE_URL));
        Assumptions.assumeTrue(admin.isKeycloakRunning(), "Keycloak is not running");
        admin.close();

        try (CamelContext context = createCamelContext()) {
            addOAuthCodeFlowRoutes(context);
            context.start();

            // Verify Realm, Client, and User exist
            var keycloak = getKeycloakAdmin().getKeycloak();
            Assumptions.assumeTrue(keycloak.realm(KEYCLOAK_REALM).toRepresentation() != null);
            Assumptions.assumeTrue(keycloak.realm(KEYCLOAK_REALM).clients().findByClientId(TEST_CLIENT_ID).size() == 1);
            Assumptions.assumeTrue(keycloak.realm(KEYCLOAK_REALM).users().search("alice").size() == 1);

            LOG.info("Keycloak realm, client, and user available!");
            LOG.info("Open: {}", getAppBaseUrl());
        }
    }

    abstract CamelContext createCamelContext() throws Exception;

    abstract void addOAuthCodeFlowRoutes(CamelContext context) throws Exception;
}
