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
package org.apache.camel.component.http;

import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLContext;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.component.http.handler.BasicValidationHandler;
import org.apache.camel.component.http.handler.HeaderValidationHandler;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.ssl.SSLContexts;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.http.HttpMethods.GET;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * If SSLContext is created via system properties, is cached. Automatically created next sslContext (with different
 * system properties) contains values from the first creation. Therefore, it is not possible to create different test,
 * which uses systemProperties without forked JVM.
 */
public class HttpsProducerWithSystemPropertiesTest extends BaseHttpTest {

    private static Object defaultSystemHttpAgent;

    @BindToRegistry("x509HostnameVerifier")
    private NoopHostnameVerifier hostnameVerifier = new NoopHostnameVerifier();

    private HttpServer localServer;

    @BeforeAll
    public static void setUpHttpAgentSystemProperty() throws Exception {
        // the 'http.agent' java system-property corresponds to the http 'User-Agent' header
        defaultSystemHttpAgent = System.setProperty("http.agent", "myCoolCamelCaseAgent");

        final URL storeResourceUrl = HttpsServerTestSupport.class.getResource("/localhost.p12");

        System.setProperty("javax.net.ssl.keyStore", new File(storeResourceUrl.toURI()).getAbsolutePath());
        System.setProperty("javax.net.ssl.keyStorePassword", "changeit");
        System.setProperty("javax.net.ssl.trustStore", new File(storeResourceUrl.toURI()).getAbsolutePath());
        System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
    }

    @AfterAll
    public static void resetHttpAgentSystemProperty() throws Exception {
        if (defaultSystemHttpAgent != null) {
            System.setProperty("http.agent", String.valueOf(defaultSystemHttpAgent));
        } else {
            System.clearProperty("http.agent");
        }
        System.clearProperty("javax.net.ssl.trustStorePassword");
        System.clearProperty("javax.net.ssl.trustStore");
        System.clearProperty("javax.net.ssl.keyStorePassword");
        System.clearProperty("javax.net.ssl.keyStore");
    }

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        super.setUp();

        URL serverKeystoreUurl = HttpsServerTestSupport.class.getResource("/localhost.p12");
        URL serverTrustStoreUrl = HttpsServerTestSupport.class.getResource("/localhost.p12");
        SSLContext sslcontext = SSLContexts.custom()
                .loadKeyMaterial(serverKeystoreUurl, "changeit".toCharArray(), "changeit".toCharArray())
                .loadTrustMaterial(serverTrustStoreUrl, "changeit".toCharArray())
                .build();

        Map<String, String> expectedHeaders = new HashMap<>();
        expectedHeaders.put("User-Agent", "myCoolCamelCaseAgent");

        localServer = ServerBootstrap.bootstrap().setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .setSslContext(sslcontext)
                .setSslSetupHandler(socket -> socket.setNeedClientAuth(true))
                .register("/mail/", new BasicValidationHandler(GET.name(), null, null, getExpectedContent()))
                .register("/header/",
                        new HeaderValidationHandler(GET.name(), null, null, getExpectedContent(), expectedHeaders))
                .create();
        localServer.start();

    }

    @AfterEach
    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        if (localServer != null) {
            localServer.stop();
        }
    }

    @Test
    public void httpGetWithProxyFromSystemProperties() throws Exception {

        String endpointUri = "https://localhost:" + localServer.getLocalPort()
                             + "/header/?x509HostnameVerifier=x509HostnameVerifier&useSystemProperties=true";
        Exchange exchange = template.request(endpointUri, exchange1 -> {
        });

        assertExchange(exchange);
    }

    @Test
    public void testTwoWaySuccessfull() throws Exception {
        Exchange exchange = template.request("https://localhost:" + localServer.getLocalPort()
                                             + "/mail/?x509HostnameVerifier=x509HostnameVerifier&useSystemProperties=true",
                exchange1 -> {
                });

        assertExchange(exchange);
    }

    @Test
    public void testTwoWayFailure() throws Exception {
        Exchange exchange = template.request("https://localhost:" + localServer.getLocalPort()
                                             + "/mail/?x509HostnameVerifier=x509HostnameVerifier",
                exchange1 -> {
                });
        //exchange does not have response code, because it was rejected
        assertTrue(exchange.getMessage().getHeaders().isEmpty());
    }
}
