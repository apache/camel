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
import javax.net.ssl.TrustManagerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpsConfigurator;
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

class OpenAISslMockTest extends CamelTestSupport {

    private static final String SSL_RESOURCES = "src/test/resources/org/apache/camel/component/openai/ssl/";
    private static final String KEYSTORE_PATH = SSL_RESOURCES + "test-keystore.jks";
    private static final String TRUSTSTORE_PATH = SSL_RESOURCES + "test-truststore.jks";
    private static final String KEYSTORE_P12_PATH = SSL_RESOURCES + "test-keystore.p12";
    private static final String TRUSTSTORE_P12_PATH = SSL_RESOURCES + "test-truststore.p12";
    private static final String STORE_PASSWORD = "changeit";

    private HttpsServer httpsServer;
    private ExecutorService executor;
    private int port;

    @BeforeEach
    void startHttpsServer() throws Exception {
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
        httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext));

        MockExpectation expectation = new MockExpectation("hello");
        expectation.setExpectedResponse("Hello over TLS!");
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
    void stopHttpsServer() {
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
                from("direct:chat-ssl")
                        .toD("openai:chat-completion?model=gpt-5&apiKey=dummy"
                             + "&baseUrl=https://localhost:${header.port}/v1"
                             + "&sslTruststoreLocation=" + TRUSTSTORE_PATH
                             + "&sslTruststorePassword=" + STORE_PASSWORD
                             + "&sslEndpointAlgorithm=https");

                from("direct:chat-ssl-no-verify")
                        .toD("openai:chat-completion?model=gpt-5&apiKey=dummy"
                             + "&baseUrl=https://localhost:${header.port}/v1"
                             + "&sslTruststoreLocation=" + TRUSTSTORE_PATH
                             + "&sslTruststorePassword=" + STORE_PASSWORD
                             + "&sslEndpointAlgorithm=none");

                from("direct:chat-ssl-empty-algorithm")
                        .toD("openai:chat-completion?model=gpt-5&apiKey=dummy"
                             + "&baseUrl=https://localhost:${header.port}/v1"
                             + "&sslTruststoreLocation=" + TRUSTSTORE_PATH
                             + "&sslTruststorePassword=" + STORE_PASSWORD
                             + "&sslEndpointAlgorithm=");

                from("direct:chat-no-ssl")
                        .toD("openai:chat-completion?model=gpt-5&apiKey=dummy"
                             + "&baseUrl=https://localhost:${header.port}/v1");

                from("direct:chat-ssl-pkcs12")
                        .toD("openai:chat-completion?model=gpt-5&apiKey=dummy"
                             + "&baseUrl=https://localhost:${header.port}/v1"
                             + "&sslTruststoreLocation=" + TRUSTSTORE_P12_PATH
                             + "&sslTruststorePassword=" + STORE_PASSWORD
                             + "&sslTruststoreType=PKCS12");
            }
        };
    }

    @Test
    void chatOverTlsWithTrustStore() {
        Exchange result = template.request("direct:chat-ssl", e -> {
            e.getIn().setBody("hello");
            e.getIn().setHeader("port", port);
        });
        assertNull(result.getException(), "Exchange should not have an exception");
        assertEquals("Hello over TLS!", result.getMessage().getBody(String.class));
        assertNotNull(result.getMessage().getHeader(OpenAIConstants.RESPONSE_ID));
        assertEquals("openai-mock", result.getMessage().getHeader(OpenAIConstants.RESPONSE_MODEL));
    }

    @Test
    void chatOverTlsWithHostnameVerificationDisabled() {
        Exchange result = template.request("direct:chat-ssl-no-verify", e -> {
            e.getIn().setBody("hello");
            e.getIn().setHeader("port", port);
        });
        assertNull(result.getException(), "Exchange should not have an exception");
        assertEquals("Hello over TLS!", result.getMessage().getBody(String.class));
        assertNotNull(result.getMessage().getHeader(OpenAIConstants.RESPONSE_ID));
    }

    @Test
    void chatOverTlsWithEmptyEndpointAlgorithm() {
        Exchange result = template.request("direct:chat-ssl-empty-algorithm", e -> {
            e.getIn().setBody("hello");
            e.getIn().setHeader("port", port);
        });
        assertNull(result.getException(), "Exchange should not have an exception");
        assertEquals("Hello over TLS!", result.getMessage().getBody(String.class));
        assertNotNull(result.getMessage().getHeader(OpenAIConstants.RESPONSE_ID));
    }

    @Test
    void chatOverTlsWithPkcs12TrustStore() throws Exception {
        HttpsServer pkcs12Server = startHttpsServer(KEYSTORE_P12_PATH, TRUSTSTORE_P12_PATH, "PKCS12");
        int pkcs12Port = pkcs12Server.getAddress().getPort();
        try {
            Exchange result = template.request("direct:chat-ssl-pkcs12", e -> {
                e.getIn().setBody("hello");
                e.getIn().setHeader("port", pkcs12Port);
            });
            assertNull(result.getException(), "Exchange should not have an exception");
            assertEquals("Hello over TLS!", result.getMessage().getBody(String.class));
            assertNotNull(result.getMessage().getHeader(OpenAIConstants.RESPONSE_ID));
            assertEquals("openai-mock", result.getMessage().getHeader(OpenAIConstants.RESPONSE_MODEL));
        } finally {
            pkcs12Server.stop(0);
        }
    }

    @Test
    void chatOverTlsWithoutTrustStoreFailsWithSslError() {
        Exchange result = template.request("direct:chat-no-ssl", e -> {
            e.getIn().setBody("hello");
            e.getIn().setHeader("port", port);
        });
        assertNotNull(result.getException(), "Exchange should fail with SSL error when no trust store is configured");
    }

    private HttpsServer startHttpsServer(String keystorePath, String truststorePath, String storeType) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(storeType);
        try (FileInputStream fis = new FileInputStream(keystorePath)) {
            keyStore.load(fis, STORE_PASSWORD.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(keyStore, STORE_PASSWORD.toCharArray());

        KeyStore trustStore = KeyStore.getInstance(storeType);
        try (FileInputStream fis = new FileInputStream(truststorePath)) {
            trustStore.load(fis, STORE_PASSWORD.toCharArray());
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(trustStore);

        SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        HttpsServer server = HttpsServer.create(new InetSocketAddress(0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(sslContext));

        MockExpectation expectation = new MockExpectation("hello");
        expectation.setExpectedResponse("Hello over TLS!");
        List<MockExpectation> expectations = new ArrayList<>();
        expectations.add(expectation);

        server.createContext("/",
                new OpenAIMockServerHandler(expectations, List.of(), new ObjectMapper()));

        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        return server;
    }
}
