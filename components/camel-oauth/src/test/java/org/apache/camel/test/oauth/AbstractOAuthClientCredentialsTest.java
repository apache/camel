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
import org.apache.camel.oauth.OAuth;
import org.apache.camel.oauth.OAuthFactory;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class AbstractOAuthClientCredentialsTest extends AbstractKeycloakTest {

    @Test
    public void testOAuthClientCredentials() throws Exception {

        var admin = new KeycloakAdmin(new KeycloakAdmin.AdminParams(KEYCLOAK_BASE_URL));
        Assumptions.assumeTrue(admin.isKeycloakRunning(), "Keycloak is not running");

        try (CamelContext context = createCamelContext()) {
            addOAuthClientCredentialsRoutes(context);
            context.start();

            var factory = OAuthFactory.lookupFactory(context);
            assertInstanceOf(OAuth.class, factory.createOAuth());

            given()
                    .body("Hello Kermit")
                    .when()
                    .post("/plain")
                    .then()
                    .statusCode(200)
                    .body(equalTo("Hello Kermit - No auth"));

            given()
                    .body("Hello Kermit")
                    .when()
                    .post("/creds")
                    .then()
                    .statusCode(200)
                    .body(equalTo("Hello Kermit - OAuthClientCredentials"));

            // Verify the Authorization Token
            var authToken = context.getGlobalOptions().remove("Authorization");
            assertNotNull(authToken);
            assertTrue(authToken.startsWith("Bearer "));

            given()
                    // Set the Authorization header
                    .header("Authorization", authToken)
                    .body("Hello Kermit")
                    .when()
                    .post("/bearer")
                    .then()
                    .statusCode(200)
                    .body(equalTo("Hello Kermit - OAuthBearerToken"));
        }
    }

    abstract CamelContext createCamelContext() throws Exception;

    abstract void addOAuthClientCredentialsRoutes(CamelContext context) throws Exception;
}
