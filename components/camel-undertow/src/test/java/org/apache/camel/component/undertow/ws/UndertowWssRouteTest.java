/**
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
package org.apache.camel.component.undertow.ws;

import java.io.IOException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.undertow.BaseUndertowTest;
import org.apache.camel.component.undertow.UndertowHttpsSpringTest;
import org.apache.camel.util.jsse.KeyManagersParameters;
import org.apache.camel.util.jsse.KeyStoreParameters;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.apache.camel.util.jsse.SSLContextServerParameters;
import org.apache.camel.util.jsse.TrustManagersParameters;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.AsyncHttpClientConfig;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.asynchttpclient.ws.WebSocket;
import org.asynchttpclient.ws.WebSocketTextListener;
import org.asynchttpclient.ws.WebSocketUpgradeHandler;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class UndertowWssRouteTest extends BaseUndertowTest {

    @BeforeClass
    public static void setUpJaas() throws Exception {
        URL trustStoreUrl = UndertowHttpsSpringTest.class.getClassLoader().getResource("ssl/keystore.jks");
        System.setProperty("javax.net.ssl.trustStore", trustStoreUrl.toURI().getPath());
    }

    @AfterClass
    public static void tearDownJaas() throws Exception {
        System.clearProperty("java.security.auth.login.config");
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setResource("ssl/keystore.jks");
        ksp.setPassword("password");

        KeyManagersParameters kmp = new KeyManagersParameters();
        kmp.setKeyPassword("password");
        kmp.setKeyStore(ksp);

        TrustManagersParameters tmp = new TrustManagersParameters();
        tmp.setKeyStore(ksp);

        // NOTE: Needed since the client uses a loose trust configuration when no ssl context
        // is provided.  We turn on WANT client-auth to prefer using authentication
        SSLContextServerParameters scsp = new SSLContextServerParameters();

        SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setKeyManagers(kmp);
        sslContextParameters.setTrustManagers(tmp);
        sslContextParameters.setServerParameters(scsp);
        context.setSSLContextParameters(sslContextParameters);

        ((SSLContextParametersAware) context.getComponent("undertow")).setUseGlobalSslContextParameters(true);
        return context;
    }

    protected AsyncHttpClient createAsyncHttpSSLClient() throws IOException, GeneralSecurityException {

        AsyncHttpClient c;
        AsyncHttpClientConfig config;

        DefaultAsyncHttpClientConfig.Builder builder =
                new DefaultAsyncHttpClientConfig.Builder();

        SslContext sslContext = SslContextBuilder
                .forClient()
                .sslProvider(SslProvider.JDK)
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();
        builder.setSslContext(sslContext);
        builder.setAcceptAnyCertificate(true);
        config = builder.build();
        c = new DefaultAsyncHttpClient(config);

        return c;
    }

    @Test
    public void testWSHttpCall() throws Exception {
        final List<String> received = new ArrayList<String>();
        final CountDownLatch latch = new CountDownLatch(10);

        AsyncHttpClient c = createAsyncHttpSSLClient();
        WebSocket websocket = c.prepareGet("wss://localhost:" + getPort() + "/test").execute(
                new WebSocketUpgradeHandler.Builder()
                        .addWebSocketListener(new WebSocketTextListener() {
                            @Override
                            public void onMessage(String message) {
                                received.add(message);
                                log.info("received --> " + message);
                                latch.countDown();
                            }

                            @Override
                            public void onOpen(WebSocket websocket) {
                            }

                            @Override
                            public void onClose(WebSocket websocket) {
                            }

                            @Override
                            public void onError(Throwable t) {
                                t.printStackTrace();
                            }
                        }).build()).get();

        getMockEndpoint("mock:client").expectedBodiesReceived("Hello from WS client");

        websocket.sendMessage("Hello from WS client");
        assertTrue(latch.await(10, TimeUnit.SECONDS));

        assertMockEndpointsSatisfied();

        assertEquals(10, received.size());
        for (int i = 0; i < 10; i++) {
            assertEquals(">> Welcome on board!", received.get(i));
        }

        websocket.close();
        c.close();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("undertow:ws://localhost:" + getPort() + "/test")
                     .log(">>> Message received from WebSocket Client : ${body}")
                     .to("mock:client")
                     .loop(10)
                         .setBody().constant(">> Welcome on board!")
                         .to("undertow:ws://localhost:" + getPort() + "/test");
            }
        };
    }
}
