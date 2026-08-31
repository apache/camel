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
package org.apache.camel.maven;

import org.apache.camel.component.salesforce.AuthenticationType;
import org.apache.camel.component.salesforce.codegen.AbstractSalesforceExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for authentication parameter validation in {@link AbstractSalesforceMojo}. These tests verify that
 * {@code validateAuthenticationParameters()} rejects invalid credential combinations and accepts valid ones for all
 * supported authentication types (USERNAME_PASSWORD, JWT, CLIENT_CREDENTIALS).
 */
public class SalesforceMojoValidationTest {

    private static final String VALIDATION_PASSED = "validation passed";

    private AbstractSalesforceMojo createMojo() {
        return new AbstractSalesforceMojo() {
            @Override
            protected AbstractSalesforceExecution getSalesforceExecution() {
                throw new RuntimeException(VALIDATION_PASSED);
            }
        };
    }

    // --- Validation rejection tests ---

    // Validation must fail when no authentication credential (clientSecret or keystoreResource) is provided
    @Test
    void shouldRejectWhenNeitherClientSecretNorKeystoreProvided() {
        AbstractSalesforceMojo mojo = createMojo();
        mojo.clientId = "test-client-id";

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("Either property: clientSecret or property: keystoreResource must be provided");
    }

    // clientSecret and keystoreResource are mutually exclusive — providing both must be rejected
    @Test
    void shouldRejectWhenBothClientSecretAndKeystoreProvided() {
        AbstractSalesforceMojo mojo = createMojo();
        mojo.clientId = "test-client-id";
        mojo.clientSecret = "test-secret";
        mojo.keystoreResource = "/some/keystore.jks";

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("Only one of clientSecret or keystoreResource may be provided, not both");
    }

    // JWT authentication requires a keystore password to unlock the keystore
    @Test
    void shouldRejectKeystoreWithoutPassword() {
        AbstractSalesforceMojo mojo = createMojo();
        mojo.clientId = "test-client-id";
        mojo.keystoreResource = "/some/keystore.jks";

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("keystorePassword' must be provided");
    }

    // When clientSecret and userName are set but password is missing, the configuration is ambiguous:
    // it could be USERNAME_PASSWORD (missing password) or CLIENT_CREDENTIALS (stray userName).
    // Validation must reject this unless authenticationType is set explicitly.
    @Test
    void shouldRejectAmbiguousCredentialsWithoutAuthenticationType() {
        AbstractSalesforceMojo mojo = createMojo();
        mojo.clientId = "test-client-id";
        mojo.clientSecret = "test-secret";
        mojo.userName = "user@example.com";

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("Ambiguous authentication configuration");
    }

    // --- Validation acceptance tests (one per auth method) ---

    // USERNAME_PASSWORD: clientSecret + userName + password is a valid, unambiguous combination
    @Test
    void shouldAcceptUsernamePasswordCredentials() {
        AbstractSalesforceMojo mojo = createMojo();
        mojo.clientId = "test-client-id";
        mojo.clientSecret = "test-secret";
        mojo.userName = "user@example.com";
        mojo.password = "test-password";

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining(VALIDATION_PASSED);
    }

    // JWT: keystoreResource + keystorePassword + userName is valid — no ambiguity since clientSecret is absent
    @Test
    void shouldAcceptJwtCredentials() {
        AbstractSalesforceMojo mojo = createMojo();
        mojo.clientId = "test-client-id";
        mojo.keystoreResource = "/some/keystore.jks";
        mojo.keystorePassword = "test-password";
        mojo.userName = "user@example.com";

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining(VALIDATION_PASSED);
    }

    // CLIENT_CREDENTIALS (auto-detected): clientSecret without userName is unambiguously Client Credentials
    @Test
    void shouldAcceptClientCredentialsWithoutUserName() {
        AbstractSalesforceMojo mojo = createMojo();
        mojo.clientId = "test-client-id";
        mojo.clientSecret = "test-secret";

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining(VALIDATION_PASSED);
    }

    // CLIENT_CREDENTIALS (explicit): clientSecret + userName would normally be ambiguous, but setting
    // authenticationType explicitly resolves it — validation must accept this
    @Test
    void shouldAcceptExplicitClientCredentialsWithUserName() {
        AbstractSalesforceMojo mojo = createMojo();
        mojo.clientId = "test-client-id";
        mojo.clientSecret = "test-secret";
        mojo.userName = "user@example.com";
        mojo.authenticationType = AuthenticationType.CLIENT_CREDENTIALS;

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining(VALIDATION_PASSED);
    }
}
