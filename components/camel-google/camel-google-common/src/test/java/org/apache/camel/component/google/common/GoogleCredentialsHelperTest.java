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
package org.apache.camel.component.google.common;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.google.api.client.auth.oauth2.Credential;
import com.google.auth.Credentials;
import com.google.auth.oauth2.ExternalAccountCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class GoogleCredentialsHelperTest {

    private CamelContext context;

    @BeforeEach
    void setUp() throws Exception {
        context = new DefaultCamelContext();
        context.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (context != null) {
            context.stop();
        }
    }

    @Test
    void testWifWithExplicitConfigReturnsExternalAccountCredentials() throws Exception {
        TestConfig config = new TestConfig();
        config.setUseWorkloadIdentityFederation(true);
        config.setWorkloadIdentityConfig("classpath:wif-config.json");

        List<String> scopes = Arrays.asList("https://www.googleapis.com/auth/cloud-platform");
        Credentials credentials = GoogleCredentialsHelper.getCredentials(context, config, scopes);

        assertNotNull(credentials);
        assertInstanceOf(ExternalAccountCredentials.class, credentials);
    }

    @Test
    void testWifWithImpersonationReturnsImpersonatedCredentials() throws Exception {
        TestConfig config = new TestConfig();
        config.setUseWorkloadIdentityFederation(true);
        config.setWorkloadIdentityConfig("classpath:wif-config.json");
        config.setImpersonatedServiceAccount("target@project.iam.gserviceaccount.com");

        List<String> scopes = Arrays.asList("https://www.googleapis.com/auth/cloud-platform");
        Credentials credentials = GoogleCredentialsHelper.getCredentials(context, config, scopes);

        assertNotNull(credentials);
        assertInstanceOf(ImpersonatedCredentials.class, credentials);
    }

    @Test
    void testWifWithExplicitConfigNoScopes() throws Exception {
        TestConfig config = new TestConfig();
        config.setUseWorkloadIdentityFederation(true);
        config.setWorkloadIdentityConfig("classpath:wif-config.json");

        Credentials credentials = GoogleCredentialsHelper.getCredentials(context, config, null);

        assertNotNull(credentials);
        assertInstanceOf(ExternalAccountCredentials.class, credentials);
    }

    @Test
    void testAuthenticateDisabledReturnsNull() throws Exception {
        TestConfig config = new TestConfig();
        config.setAuthenticate(false);

        Credentials credentials = GoogleCredentialsHelper.getCredentials(context, config, null);

        assertNull(credentials);
    }

    @Test
    void testDefaultConfigInterfaceValues() {
        // Verify that the interface defaults return the expected values
        GoogleCommonConfiguration config = new TestConfig();
        assertNull(config.getWorkloadIdentityConfig());
        assertNull(config.getImpersonatedServiceAccount());
    }

    // ==================== OAuth regression tests (CAMEL-27187) ====================

    /**
     * Regression test for CAMEL-27187: createOAuthCredential must not call setServiceAccountScopes when building an
     * OAuth credential. GoogleCredential rejects service-account scopes when no private key is present, throwing
     * IllegalArgumentException.
     * <p>
     * This test covers the base case: OAuth with explicit scopes must succeed.
     */
    @Test
    void testOAuthCredentialWithScopesDoesNotThrow() throws Exception {
        TestConfig config = new TestConfig();
        config.setClientId("test-client-id");
        config.setClientSecret("test-client-secret");
        config.setRefreshToken("test-refresh-token");

        List<String> scopes = Arrays.asList("https://www.googleapis.com/auth/calendar");
        // Before the fix this threw IllegalArgumentException from GoogleCredential.<init>
        Credential credential = GoogleCredentialsHelper.getOAuthCredential(context, config, scopes);

        assertNotNull(credential);
        assertEquals("test-refresh-token", credential.getRefreshToken());
    }

    /**
     * Regression test for CAMEL-27187: google-calendar-stream always provides non-null scopes (defaulting to
     * CalendarScopes.CALENDAR), so every OAuth login failed before the fix.
     */
    @Test
    void testOAuthCredentialWithMultipleScopesDoesNotThrow() throws Exception {
        TestConfig config = new TestConfig();
        config.setClientId("test-client-id");
        config.setClientSecret("test-client-secret");
        config.setAccessToken("test-access-token");

        List<String> scopes = Arrays.asList(
                "https://www.googleapis.com/auth/calendar",
                "https://www.googleapis.com/auth/gmail.modify",
                "https://www.googleapis.com/auth/drive");
        Credential credential = GoogleCredentialsHelper.getOAuthCredential(context, config, scopes);

        assertNotNull(credential);
    }

    /**
     * OAuth with null scopes must continue to work (pre-existing behaviour).
     */
    @Test
    void testOAuthCredentialWithNullScopesDoesNotThrow() throws Exception {
        TestConfig config = new TestConfig();
        config.setClientId("test-client-id");
        config.setClientSecret("test-client-secret");
        config.setRefreshToken("test-refresh-token");

        Credential credential = GoogleCredentialsHelper.getOAuthCredential(context, config, null);

        assertNotNull(credential);
    }

    /**
     * Test configuration implementing GoogleCommonConfiguration with WIF and OAuth support.
     */
    static class TestConfig implements GoogleCommonConfiguration {
        private String serviceAccountKey;
        private boolean useWorkloadIdentityFederation;
        private String workloadIdentityConfig;
        private String impersonatedServiceAccount;
        private boolean authenticate = true;
        private String clientId;
        private String clientSecret;
        private String accessToken;
        private String refreshToken;

        @Override
        public String getServiceAccountKey() {
            return serviceAccountKey;
        }

        public void setServiceAccountKey(String serviceAccountKey) {
            this.serviceAccountKey = serviceAccountKey;
        }

        @Override
        public boolean isUseWorkloadIdentityFederation() {
            return useWorkloadIdentityFederation;
        }

        public void setUseWorkloadIdentityFederation(boolean useWorkloadIdentityFederation) {
            this.useWorkloadIdentityFederation = useWorkloadIdentityFederation;
        }

        @Override
        public String getWorkloadIdentityConfig() {
            return workloadIdentityConfig;
        }

        public void setWorkloadIdentityConfig(String workloadIdentityConfig) {
            this.workloadIdentityConfig = workloadIdentityConfig;
        }

        @Override
        public String getImpersonatedServiceAccount() {
            return impersonatedServiceAccount;
        }

        public void setImpersonatedServiceAccount(String impersonatedServiceAccount) {
            this.impersonatedServiceAccount = impersonatedServiceAccount;
        }

        @Override
        public boolean isAuthenticate() {
            return authenticate;
        }

        public void setAuthenticate(boolean authenticate) {
            this.authenticate = authenticate;
        }

        @Override
        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        @Override
        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        @Override
        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        @Override
        public String getRefreshToken() {
            return refreshToken;
        }

        public void setRefreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
        }

        @Override
        public Collection<String> getScopesAsList() {
            return null;
        }
    }
}
