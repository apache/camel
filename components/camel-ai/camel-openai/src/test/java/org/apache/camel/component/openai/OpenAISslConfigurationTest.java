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
package org.apache.camel.component.openai;

import org.apache.camel.CamelContext;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAISslConfigurationTest extends CamelTestSupport {

    private static final String TRUSTSTORE_PATH
            = "src/test/resources/org/apache/camel/component/openai/ssl/test-truststore.jks";
    private static final String KEYSTORE_PATH = "src/test/resources/org/apache/camel/component/openai/ssl/test-keystore.jks";
    private static final String STORE_PASSWORD = "changeit";

    @Test
    void sslDefaultValues() {
        OpenAIConfiguration config = new OpenAIConfiguration();

        assertEquals("JKS", config.getSslTruststoreType());
        assertEquals("JKS", config.getSslKeystoreType());
        assertEquals("TLSv1.3", config.getSslProtocol());
        assertEquals("SunX509", config.getSslKeymanagerAlgorithm());
        assertEquals("PKIX", config.getSslTrustmanagerAlgorithm());
        assertEquals("https", config.getSslEndpointAlgorithm());
    }

    @Test
    void sslConfigurationFromUri() throws Exception {
        CamelContext camelContext = context();
        OpenAIEndpoint endpoint = (OpenAIEndpoint) camelContext.getEndpoint(
                "openai:chat-completion?apiKey=dummy"
                                                                            + "&sslTruststoreLocation=" + TRUSTSTORE_PATH
                                                                            + "&sslTruststorePassword=" + STORE_PASSWORD
                                                                            + "&sslTruststoreType=JKS"
                                                                            + "&sslKeystoreLocation=" + KEYSTORE_PATH
                                                                            + "&sslKeystorePassword=" + STORE_PASSWORD
                                                                            + "&sslKeystoreType=JKS"
                                                                            + "&sslKeyPassword=" + STORE_PASSWORD
                                                                            + "&sslProtocol=TLSv1.3"
                                                                            + "&sslKeymanagerAlgorithm=SunX509"
                                                                            + "&sslTrustmanagerAlgorithm=PKIX"
                                                                            + "&sslEndpointAlgorithm=none");

        OpenAIConfiguration config = endpoint.getConfiguration();

        assertEquals(TRUSTSTORE_PATH, config.getSslTruststoreLocation());
        assertEquals(STORE_PASSWORD, config.getSslTruststorePassword());
        assertEquals("JKS", config.getSslTruststoreType());
        assertEquals(KEYSTORE_PATH, config.getSslKeystoreLocation());
        assertEquals(STORE_PASSWORD, config.getSslKeystorePassword());
        assertEquals("JKS", config.getSslKeystoreType());
        assertEquals(STORE_PASSWORD, config.getSslKeyPassword());
        assertEquals("TLSv1.3", config.getSslProtocol());
        assertEquals("SunX509", config.getSslKeymanagerAlgorithm());
        assertEquals("PKIX", config.getSslTrustmanagerAlgorithm());
        assertEquals("none", config.getSslEndpointAlgorithm());
    }

    @Test
    void createClientWithTrustStore() throws Exception {
        CamelContext camelContext = context();
        OpenAIEndpoint endpoint = (OpenAIEndpoint) camelContext.getEndpoint(
                "openai:chat-completion?apiKey=dummy"
                                                                            + "&sslTruststoreLocation=" + TRUSTSTORE_PATH
                                                                            + "&sslTruststorePassword=" + STORE_PASSWORD);

        assertNotNull(endpoint.createClient());
    }

    @Test
    void createClientWithKeyStore() throws Exception {
        CamelContext camelContext = context();
        OpenAIEndpoint endpoint = (OpenAIEndpoint) camelContext.getEndpoint(
                "openai:chat-completion?apiKey=dummy"
                                                                            + "&sslKeystoreLocation=" + KEYSTORE_PATH
                                                                            + "&sslKeystorePassword=" + STORE_PASSWORD);

        assertNotNull(endpoint.createClient());
    }

    @Test
    void createClientWithBothStores() throws Exception {
        CamelContext camelContext = context();
        OpenAIEndpoint endpoint = (OpenAIEndpoint) camelContext.getEndpoint(
                "openai:chat-completion?apiKey=dummy"
                                                                            + "&sslTruststoreLocation=" + TRUSTSTORE_PATH
                                                                            + "&sslTruststorePassword=" + STORE_PASSWORD
                                                                            + "&sslKeystoreLocation=" + KEYSTORE_PATH
                                                                            + "&sslKeystorePassword=" + STORE_PASSWORD
                                                                            + "&sslKeyPassword=" + STORE_PASSWORD);

        assertNotNull(endpoint.createClient());
    }

    @Test
    void createClientWithDisabledHostnameVerification() throws Exception {
        CamelContext camelContext = context();
        OpenAIEndpoint endpoint = (OpenAIEndpoint) camelContext.getEndpoint(
                "openai:chat-completion?apiKey=dummy"
                                                                            + "&sslTruststoreLocation=" + TRUSTSTORE_PATH
                                                                            + "&sslTruststorePassword=" + STORE_PASSWORD
                                                                            + "&sslEndpointAlgorithm=none");

        assertNotNull(endpoint.createClient());
    }

    @Test
    void createClientWithNoSslConfigUsesDefaults() throws Exception {
        CamelContext camelContext = context();
        OpenAIEndpoint endpoint = (OpenAIEndpoint) camelContext.getEndpoint(
                "openai:chat-completion?apiKey=dummy");

        assertNotNull(endpoint.createClient());
    }

    @Test
    void createClientWithInvalidTruststoreLocationFails() {
        CamelContext camelContext = context();

        Exception exception = assertThrows(Exception.class,
                () -> camelContext.getEndpoint(
                        "openai:chat-completion?apiKey=dummy"
                                               + "&sslTruststoreLocation=/nonexistent/truststore.jks"
                                               + "&sslTruststorePassword=" + STORE_PASSWORD));

        assertHasRootCause(exception, java.io.FileNotFoundException.class);
    }

    @Test
    void createClientWithInvalidKeystoreLocationFails() {
        CamelContext camelContext = context();

        Exception exception = assertThrows(Exception.class,
                () -> camelContext.getEndpoint(
                        "openai:chat-completion?apiKey=dummy"
                                               + "&sslKeystoreLocation=/nonexistent/keystore.jks"
                                               + "&sslKeystorePassword=" + STORE_PASSWORD));

        assertHasRootCause(exception, java.io.FileNotFoundException.class);
    }

    @Test
    void createClientWithInvalidTruststoreTypeFails() {
        CamelContext camelContext = context();

        Exception exception = assertThrows(Exception.class,
                () -> camelContext.getEndpoint(
                        "openai:chat-completion?apiKey=dummy"
                                               + "&sslTruststoreLocation=" + TRUSTSTORE_PATH
                                               + "&sslTruststorePassword=" + STORE_PASSWORD
                                               + "&sslTruststoreType=INVALID"));

        assertHasRootCause(exception, java.security.KeyStoreException.class);
    }

    private static void assertHasRootCause(Throwable throwable, Class<? extends Throwable> expectedCauseType) {
        Throwable cause = throwable;
        while (cause != null) {
            if (expectedCauseType.isInstance(cause)) {
                return;
            }
            cause = cause.getCause();
        }
        throw new AssertionError(
                "Expected root cause of type " + expectedCauseType.getName()
                                 + " but was: " + throwable.getClass().getName() + ": " + throwable.getMessage());
    }

    @Test
    void createClientWithEmptyEndpointAlgorithmDisablesHostnameVerification() throws Exception {
        CamelContext camelContext = context();
        OpenAIEndpoint endpoint = (OpenAIEndpoint) camelContext.getEndpoint(
                "openai:chat-completion?apiKey=dummy"
                                                                            + "&sslTruststoreLocation=" + TRUSTSTORE_PATH
                                                                            + "&sslTruststorePassword=" + STORE_PASSWORD
                                                                            + "&sslEndpointAlgorithm=");

        assertNotNull(endpoint.createClient());
    }

    @Test
    void createClientWithKeyStoreAndNoKeyPasswordFallsBackToStorePassword() throws Exception {
        CamelContext camelContext = context();
        // sslKeyPassword is not set, should fall back to sslKeystorePassword
        OpenAIEndpoint endpoint = (OpenAIEndpoint) camelContext.getEndpoint(
                "openai:chat-completion?apiKey=dummy"
                                                                            + "&sslKeystoreLocation=" + KEYSTORE_PATH
                                                                            + "&sslKeystorePassword=" + STORE_PASSWORD);

        assertNotNull(endpoint.createClient());
    }

    @Test
    void createClientWithTlsV12Protocol() throws Exception {
        CamelContext camelContext = context();
        OpenAIEndpoint endpoint = (OpenAIEndpoint) camelContext.getEndpoint(
                "openai:chat-completion?apiKey=dummy"
                                                                            + "&sslTruststoreLocation=" + TRUSTSTORE_PATH
                                                                            + "&sslTruststorePassword=" + STORE_PASSWORD
                                                                            + "&sslProtocol=TLSv1.2");

        assertNotNull(endpoint.createClient());
    }

    @Test
    void createClientWithCustomAlgorithms() throws Exception {
        CamelContext camelContext = context();
        OpenAIEndpoint endpoint = (OpenAIEndpoint) camelContext.getEndpoint(
                "openai:chat-completion?apiKey=dummy"
                                                                            + "&sslTruststoreLocation=" + TRUSTSTORE_PATH
                                                                            + "&sslTruststorePassword=" + STORE_PASSWORD
                                                                            + "&sslTrustmanagerAlgorithm=SunX509"
                                                                            + "&sslKeystoreLocation=" + KEYSTORE_PATH
                                                                            + "&sslKeystorePassword=" + STORE_PASSWORD
                                                                            + "&sslKeymanagerAlgorithm=SunX509");

        assertNotNull(endpoint.createClient());
    }

    @Test
    void sslConfigurationCopy() {
        OpenAIConfiguration config = new OpenAIConfiguration();
        config.setSslTruststoreLocation("/path/to/truststore");
        config.setSslTruststorePassword("secret");
        config.setSslTruststoreType("PKCS12");
        config.setSslKeystoreLocation("/path/to/keystore");
        config.setSslKeystorePassword("secret2");
        config.setSslKeystoreType("PKCS12");
        config.setSslKeyPassword("keypass");
        config.setSslProtocol("TLSv1.2");
        config.setSslKeymanagerAlgorithm("PKIX");
        config.setSslTrustmanagerAlgorithm("SunX509");
        config.setSslEndpointAlgorithm("none");

        OpenAIConfiguration copy = config.copy();

        assertEquals(config.getSslTruststoreLocation(), copy.getSslTruststoreLocation());
        assertEquals(config.getSslTruststorePassword(), copy.getSslTruststorePassword());
        assertEquals(config.getSslTruststoreType(), copy.getSslTruststoreType());
        assertEquals(config.getSslKeystoreLocation(), copy.getSslKeystoreLocation());
        assertEquals(config.getSslKeystorePassword(), copy.getSslKeystorePassword());
        assertEquals(config.getSslKeystoreType(), copy.getSslKeystoreType());
        assertEquals(config.getSslKeyPassword(), copy.getSslKeyPassword());
        assertEquals(config.getSslProtocol(), copy.getSslProtocol());
        assertEquals(config.getSslKeymanagerAlgorithm(), copy.getSslKeymanagerAlgorithm());
        assertEquals(config.getSslTrustmanagerAlgorithm(), copy.getSslTrustmanagerAlgorithm());
        assertEquals(config.getSslEndpointAlgorithm(), copy.getSslEndpointAlgorithm());
    }
}
