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
package org.apache.camel.component.websocket;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.JdkSslContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.SimpleRegistry;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.SSLContextServerParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.AsyncHttpClientConfig;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.asynchttpclient.ws.WebSocket;
import org.asynchttpclient.ws.WebSocketListener;
import org.asynchttpclient.ws.WebSocketUpgradeHandler;
import org.junit.Before;
import org.junit.Test;

public class WebsocketSSLContextInUriRouteExampleTest extends CamelTestSupport {

    private static List<String> received = new ArrayList<>();
    private static CountDownLatch latch = new CountDownLatch(10);
    private String pwd = "changeit";
    private String uri;
    private String server = "127.0.0.1";
    private int port;

    @Override
    @Before
    public void setUp() throws Exception {
        port = AvailablePortFinder.getNextAvailable();

        uri = "websocket://" + server + ":" + port + "/test?sslContextParameters=#sslContextParameters";

        super.setUp();
    }

    @Override
    protected Registry createCamelRegistry() throws Exception {
        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setResource("jsse/localhost.p12");
        ksp.setPassword(pwd);

        KeyManagersParameters kmp = new KeyManagersParameters();
        kmp.setKeyPassword(pwd);
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

        Registry registry = new SimpleRegistry();
        registry.bind("sslContextParameters", sslContextParameters);
        return registry;
    }

    protected AsyncHttpClient createAsyncHttpSSLClient() throws IOException, GeneralSecurityException {

        AsyncHttpClient c;
        AsyncHttpClientConfig config;

        DefaultAsyncHttpClientConfig.Builder builder =
                new DefaultAsyncHttpClientConfig.Builder();

        SSLContextParameters sslContextParameters = new SSLContextParameters();

        KeyStoreParameters truststoreParameters = new KeyStoreParameters();
        truststoreParameters.setResource("jsse/localhost.p12");
        truststoreParameters.setPassword(pwd);

        TrustManagersParameters clientSSLTrustManagers = new TrustManagersParameters();
        clientSSLTrustManagers.setKeyStore(truststoreParameters);
        sslContextParameters.setTrustManagers(clientSSLTrustManagers);

        SSLContext sslContext = sslContextParameters.createSSLContext(context());
        JdkSslContext ssl = new JdkSslContext(sslContext, true, ClientAuth.REQUIRE);
        builder.setSslContext(ssl);
        builder.setDisableHttpsEndpointIdentificationAlgorithm(true);
        config = builder.build();
        c = new DefaultAsyncHttpClient(config);

        return c;
    }

    @Test
    public void testWSHttpCall() throws Exception {

        AsyncHttpClient c = createAsyncHttpSSLClient();
        WebSocket websocket = c.prepareGet("wss://127.0.0.1:" + port + "/test").execute(
                new WebSocketUpgradeHandler.Builder()
                        .addWebSocketListener(new WebSocketListener() {
                            @Override
                            public void onOpen(WebSocket websocket) {
                            }

                            @Override
                            public void onClose(WebSocket websocket, int code, String reason) {
                            }

                            @Override
                            public void onError(Throwable t) {
                                t.printStackTrace();
                            }

                            @Override
                            public void onBinaryFrame(byte[] payload, boolean finalFragment, int rsv) {
                            }

                            @Override
                            public void onTextFrame(String payload, boolean finalFragment, int rsv) {
                                received.add(payload);
                                log.info("received --> " + payload);
                                latch.countDown();
                            }

                            @Override
                            public void onPingFrame(byte[] payload) {
                            }

                            @Override
                            public void onPongFrame(byte[] payload) {
                            }
                        }).build()).get();

        getMockEndpoint("mock:client").expectedBodiesReceived("Hello from WS client");

        websocket.sendTextFrame("Hello from WS client");
        assertTrue(latch.await(10, TimeUnit.SECONDS));

        assertMockEndpointsSatisfied();

        assertEquals(10, received.size());
        for (int i = 0; i < 10; i++) {
            assertEquals(">> Welcome on board!", received.get(i));
        }

        websocket.sendCloseFrame();
        c.close();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                WebsocketComponent websocketComponent = (WebsocketComponent) context.getComponent("websocket");
                websocketComponent.setMinThreads(1);
                websocketComponent.setMaxThreads(25);
                from(uri)
                     .log(">>> Message received from WebSocket Client : ${body}")
                     .to("mock:client")
                     .loop(10)
                         .setBody().constant(">> Welcome on board!")
                         .to(uri);
            }
        };
    }
}
