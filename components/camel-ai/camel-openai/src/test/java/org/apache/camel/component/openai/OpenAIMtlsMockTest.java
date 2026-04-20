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

import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.openai.mock.MockExpectation;
import org.apache.camel.test.infra.openai.mock.OpenAIMockServerHandler;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class OpenAIMtlsMockTest extends CamelTestSupport {

    private static final String SSL_RESOURCES = "src/test/resources/org/apache/camel/component/openai/ssl/";
    private static final String KEYSTORE_PATH = SSL_RESOURCES + "test-keystore.jks";
    private static final String TRUSTSTORE_PATH = SSL_RESOURCES + "test-truststore.jks";
    private static final String KEYSTORE_DIFFPASS_PATH = SSL_RESOURCES + "test-keystore-diffpass.jks";
    private static final String TRUSTSTORE_DIFFPASS_PATH = SSL_RESOURCES + "test-truststore-diffpass.jks";
    private static final String STORE_PASSWORD = "changeit";
    private static final String KEY_PASSWORD = "keypass123";

    private HttpsServer httpsServer;
    private ExecutorService executor;
    private int port;

    @BeforeEach
    void startMtlsServer() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(KEYSTORE_PATH)) {
            keyStore.load(fis, STORE_PASSWORD.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(keyStore, STORE_PASSWORD.toCharArray());

        KeyStore trustStore = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(TRUSTSTORE_PATH)) {
            trustStore.load(fis, STORE_PASSWORD.toCharArray());
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(trustStore);

        SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        httpsServer = HttpsServer.create(new InetSocketAddress(0), 0);
        httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
            @Override
            public void configure(HttpsParameters params) {
                SSLParameters sslParams = sslContext.getDefaultSSLParameters();
                sslParams.setNeedClientAuth(true);
                params.setSSLParameters(sslParams);
            }
        });

        MockExpectation expectation = new MockExpectation("hello");
        expectation.setExpectedResponse("Hello over mTLS!");
        List<MockExpectation> expectations = new ArrayList<>();
        expectations.add(expectation);

        httpsServer.createContext("/",
                new OpenAIMockServerHandler(expectations, List.of(), new ObjectMapper()));

        executor = Executors.newSingleThreadExecutor();
        httpsServer.setExecutor(executor);
        httpsServer.start();
        port = httpsServer.getAddress().getPort();
    }

    @AfterEach
    void stopMtlsServer() {
        if (httpsServer != null) {
            httpsServer.stop(0);
            executor.shutdownNow();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:chat-mtls")
                        .toD("openai:chat-completion?model=gpt-5&apiKey=dummy"
                             + "&baseUrl=https://localhost:${header.port}/v1"
                             + "&sslTruststoreLocation=" + TRUSTSTORE_PATH
                             + "&sslTruststorePassword=" + STORE_PASSWORD
                             + "&sslKeystoreLocation=" + KEYSTORE_PATH
                             + "&sslKeystorePassword=" + STORE_PASSWORD
                             + "&sslKeyPassword=" + STORE_PASSWORD);

                from("direct:chat-mtls-no-client-cert")
                        .toD("openai:chat-completion?model=gpt-5&apiKey=dummy"
                             + "&baseUrl=https://localhost:${header.port}/v1"
                             + "&sslTruststoreLocation=" + TRUSTSTORE_PATH
                             + "&sslTruststorePassword=" + STORE_PASSWORD);

                from("direct:chat-mtls-key-password-fallback")
                        .toD("openai:chat-completion?model=gpt-5&apiKey=dummy"
                             + "&baseUrl=https://localhost:${header.port}/v1"
                             + "&sslTruststoreLocation=" + TRUSTSTORE_PATH
                             + "&sslTruststorePassword=" + STORE_PASSWORD
                             + "&sslKeystoreLocation=" + KEYSTORE_PATH
                             + "&sslKeystorePassword=" + STORE_PASSWORD);

                from("direct:chat-mtls-diffpass")
                        .toD("openai:chat-completion?model=gpt-5&apiKey=dummy"
                             + "&baseUrl=https://localhost:${header.port}/v1"
                             + "&sslTruststoreLocation=" + TRUSTSTORE_DIFFPASS_PATH
                             + "&sslTruststorePassword=" + STORE_PASSWORD
                             + "&sslKeystoreLocation=" + KEYSTORE_DIFFPASS_PATH
                             + "&sslKeystorePassword=" + STORE_PASSWORD
                             + "&sslKeyPassword=" + KEY_PASSWORD);

                from("direct:chat-mtls-diffpass-wrong-keypass")
                        .toD("openai:chat-completion?model=gpt-5&apiKey=dummy"
                             + "&baseUrl=https://localhost:${header.port}/v1"
                             + "&sslTruststoreLocation=" + TRUSTSTORE_DIFFPASS_PATH
                             + "&sslTruststorePassword=" + STORE_PASSWORD
                             + "&sslKeystoreLocation=" + KEYSTORE_DIFFPASS_PATH
                             + "&sslKeystorePassword=" + STORE_PASSWORD);
            }
        };
    }

    @Test
    void chatOverMutualTls() {
        Exchange result = template.request("direct:chat-mtls", e -> {
            e.getIn().setBody("hello");
            e.getIn().setHeader("port", port);
        });
        assertNull(result.getException(), "Exchange should not have an exception");
        assertEquals("Hello over mTLS!", result.getMessage().getBody(String.class));
        assertNotNull(result.getMessage().getHeader(OpenAIConstants.RESPONSE_ID));
        assertEquals("openai-mock", result.getMessage().getHeader(OpenAIConstants.RESPONSE_MODEL));
    }

    @Test
    void chatOverMutualTlsWithKeyPasswordFallback() {
        Exchange result = template.request("direct:chat-mtls-key-password-fallback", e -> {
            e.getIn().setBody("hello");
            e.getIn().setHeader("port", port);
        });
        assertNull(result.getException(),
                "Exchange should not have an exception when sslKeyPassword falls back to sslKeystorePassword");
        assertEquals("Hello over mTLS!", result.getMessage().getBody(String.class));
        assertNotNull(result.getMessage().getHeader(OpenAIConstants.RESPONSE_ID));
    }

    @Test
    void chatOverMutualTlsWithDifferentKeyPassword() throws Exception {
        HttpsServer diffPassServer = startMtlsServer(KEYSTORE_DIFFPASS_PATH, TRUSTSTORE_DIFFPASS_PATH, KEY_PASSWORD);
        int diffPassPort = diffPassServer.getAddress().getPort();
        try {
            Exchange result = template.request("direct:chat-mtls-diffpass", e -> {
                e.getIn().setBody("hello");
                e.getIn().setHeader("port", diffPassPort);
            });
            assertNull(result.getException(), "Exchange should not have an exception");
            assertEquals("Hello over mTLS!", result.getMessage().getBody(String.class));
            assertNotNull(result.getMessage().getHeader(OpenAIConstants.RESPONSE_ID));
        } finally {
            diffPassServer.stop(0);
        }
    }

    @Test
    void chatOverMutualTlsWithWrongKeyPasswordFallbackFails() throws Exception {
        HttpsServer diffPassServer = startMtlsServer(KEYSTORE_DIFFPASS_PATH, TRUSTSTORE_DIFFPASS_PATH, KEY_PASSWORD);
        int diffPassPort = diffPassServer.getAddress().getPort();
        try {
            Exchange result = template.request("direct:chat-mtls-diffpass-wrong-keypass", e -> {
                e.getIn().setBody("hello");
                e.getIn().setHeader("port", diffPassPort);
            });
            assertNotNull(result.getException(),
                    "Exchange should fail when sslKeyPassword is not set and key password differs from store password");
        } finally {
            diffPassServer.stop(0);
        }
    }

    @Test
    void chatOverMutualTlsWithoutClientCertFails() {
        Exchange result = template.request("direct:chat-mtls-no-client-cert", e -> {
            e.getIn().setBody("hello");
            e.getIn().setHeader("port", port);
        });
        assertNotNull(result.getException(),
                "Exchange should fail when server requires client certificate but none is provided");
    }

    private HttpsServer startMtlsServer(String keystorePath, String truststorePath, String keyPassword) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(keystorePath)) {
            keyStore.load(fis, STORE_PASSWORD.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(keyStore, keyPassword.toCharArray());

        KeyStore trustStore = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(truststorePath)) {
            trustStore.load(fis, STORE_PASSWORD.toCharArray());
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(trustStore);

        SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        HttpsServer server = HttpsServer.create(new InetSocketAddress(0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
            @Override
            public void configure(HttpsParameters params) {
                SSLParameters sslParams = sslContext.getDefaultSSLParameters();
                sslParams.setNeedClientAuth(true);
                params.setSSLParameters(sslParams);
            }
        });

        MockExpectation expectation = new MockExpectation("hello");
        expectation.setExpectedResponse("Hello over mTLS!");
        List<MockExpectation> expectations = new ArrayList<>();
        expectations.add(expectation);

        server.createContext("/",
                new OpenAIMockServerHandler(expectations, List.of(), new ObjectMapper()));

        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        return server;
    }
}
