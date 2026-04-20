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
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.apache.camel.test.infra.openai.mock.MockExpectation;
import org.apache.camel.test.infra.openai.mock.OpenAIMockServerHandler;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAISslContextParametersTest extends CamelTestSupport {

    private static final String SSL_RESOURCES = "src/test/resources/org/apache/camel/component/openai/ssl/";
    private static final String KEYSTORE_PATH = SSL_RESOURCES + "test-keystore.jks";
    private static final String TRUSTSTORE_PATH = SSL_RESOURCES + "test-truststore.jks";
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

    @Test
    void sslContextParametersTakesPrecedenceOverIndividualProperties() throws Exception {
        CamelContext camelContext = context();

        // Configure endpoint with BOTH SSLContextParameters and individual properties pointing to a non-existent file.
        // If SSLContextParameters takes precedence, the non-existent truststore path is ignored and the test succeeds.
        SSLContextParameters sslContextParameters = createSslContextParameters();

        OpenAIEndpoint endpoint = (OpenAIEndpoint) camelContext.getEndpoint(
                "openai:chat-completion?apiKey=dummy"
                                                                            + "&baseUrl=https://localhost:" + port + "/v1"
                                                                            + "&sslTruststoreLocation=/nonexistent/truststore.jks"
                                                                            + "&sslTruststorePassword=bogus"
                                                                            + "&sslEndpointAlgorithm=https");

        endpoint.getConfiguration().setSslContextParameters(sslContextParameters);

        assertNotNull(endpoint.createClient(),
                "Client should be created using SSLContextParameters, ignoring invalid properties");
    }

    @Test
    void endpointSslContextParametersTakesPrecedenceOverGlobal() throws Exception {
        SSLContextParameters endpointParams = createSslContextParameters();
        SSLContextParameters globalParams = new SSLContextParameters();

        CamelContext camelContext = context();
        camelContext.setSSLContextParameters(globalParams);

        OpenAIComponent component = (OpenAIComponent) camelContext.getComponent("openai");
        component.setUseGlobalSslContextParameters(true);

        OpenAIEndpoint endpoint = (OpenAIEndpoint) camelContext.getEndpoint(
                "openai:chat-completion?apiKey=dummy"
                                                                            + "&baseUrl=https://localhost:" + port + "/v1"
                                                                            + "&sslEndpointAlgorithm=https");

        endpoint.getConfiguration().setSslContextParameters(endpointParams);

        assertSame(endpointParams, endpoint.getConfiguration().getSslContextParameters(),
                "Endpoint-level SSLContextParameters should not be overwritten by global");
    }

    @Test
    void globalSslContextParametersUsedWhenEndpointHasNone() throws Exception {
        SSLContextParameters globalParams = createSslContextParameters();

        CamelContext camelContext = context();
        camelContext.setSSLContextParameters(globalParams);

        OpenAIComponent component = (OpenAIComponent) camelContext.getComponent("openai");
        component.setUseGlobalSslContextParameters(true);

        OpenAIEndpoint endpoint = (OpenAIEndpoint) camelContext.getEndpoint(
                "openai:chat-completion?apiKey=dummy"
                                                                            + "&baseUrl=https://localhost:" + port + "/v1"
                                                                            + "&sslEndpointAlgorithm=https");

        assertSame(globalParams, endpoint.getConfiguration().getSslContextParameters(),
                "Global SSLContextParameters should be used when endpoint has none");
    }

    @Test
    void globalSslContextParametersNotUsedWhenFlagDisabled() throws Exception {
        SSLContextParameters globalParams = createSslContextParameters();

        CamelContext camelContext = context();
        camelContext.setSSLContextParameters(globalParams);

        OpenAIComponent component = (OpenAIComponent) camelContext.getComponent("openai");
        component.setUseGlobalSslContextParameters(false);

        OpenAIEndpoint endpoint = (OpenAIEndpoint) camelContext.getEndpoint(
                "openai:chat-completion?apiKey=dummy"
                                                                            + "&baseUrl=https://localhost:" + port + "/v1");

        assertNull(endpoint.getConfiguration().getSslContextParameters(),
                "Global SSLContextParameters should not be used when useGlobalSslContextParameters is false");
    }

    @Test
    void chatOverTlsWithSslContextParameters() {
        Exchange result = template.request("direct:chat-ssl-context-params", e -> {
            e.getIn().setBody("hello");
            e.getIn().setHeader("port", port);
        });
        assertNull(result.getException(), "Exchange should not have an exception");
        assertEquals("Hello over TLS!", result.getMessage().getBody(String.class));
        assertNotNull(result.getMessage().getHeader(OpenAIConstants.RESPONSE_ID));
    }

    @Test
    void componentImplementsSslContextParametersAware() {
        OpenAIComponent component = (OpenAIComponent) context().getComponent("openai");
        assertTrue(component instanceof org.apache.camel.SSLContextParametersAware,
                "OpenAIComponent should implement SSLContextParametersAware");
    }

    @Test
    void fallbackToIndividualPropertiesWhenNoSslContextParameters() {
        Exchange result = template.request("direct:chat-ssl-individual-props", e -> {
            e.getIn().setBody("hello");
            e.getIn().setHeader("port", port);
        });
        assertNull(result.getException(), "Exchange should not have an exception when using individual SSL properties");
        assertEquals("Hello over TLS!", result.getMessage().getBody(String.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:chat-ssl-context-params")
                        .toD("openai:chat-completion?model=gpt-5&apiKey=dummy"
                             + "&baseUrl=https://localhost:${header.port}/v1"
                             + "&sslEndpointAlgorithm=https");

                from("direct:chat-ssl-individual-props")
                        .toD("openai:chat-completion?model=gpt-5&apiKey=dummy"
                             + "&baseUrl=https://localhost:${header.port}/v1"
                             + "&sslTruststoreLocation=" + TRUSTSTORE_PATH
                             + "&sslTruststorePassword=" + STORE_PASSWORD
                             + "&sslEndpointAlgorithm=https");
            }
        };
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        // Set global SSLContextParameters for the route-based test
        SSLContextParameters globalParams = createSslContextParameters();
        camelContext.setSSLContextParameters(globalParams);

        OpenAIComponent component = (OpenAIComponent) camelContext.getComponent("openai");
        component.setUseGlobalSslContextParameters(true);

        return camelContext;
    }

    private SSLContextParameters createSslContextParameters() {
        KeyStoreParameters trustStoreParams = new KeyStoreParameters();
        trustStoreParams.setResource(TRUSTSTORE_PATH);
        trustStoreParams.setPassword(STORE_PASSWORD);
        trustStoreParams.setType("JKS");

        TrustManagersParameters trustManagersParams = new TrustManagersParameters();
        trustManagersParams.setKeyStore(trustStoreParams);

        SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setTrustManagers(trustManagersParams);
        sslContextParameters.setSecureSocketProtocol("TLSv1.3");

        return sslContextParameters;
    }
}
