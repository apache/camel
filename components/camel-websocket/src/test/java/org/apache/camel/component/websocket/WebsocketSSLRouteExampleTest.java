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
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.SSLContextServerParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.infra.common.http.WebsocketTestClient;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WebsocketSSLRouteExampleTest extends CamelTestSupport {

    private String pwd = "changeit";
    private int port;
    private Logger log = LoggerFactory.getLogger(getClass());

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        port = AvailablePortFinder.getNextAvailable();

        super.setUp();
    }

    private SSLContext createSSLContext() throws GeneralSecurityException, IOException {
        SSLContextParameters sslContextParameters = new SSLContextParameters();

        KeyStoreParameters truststoreParameters = new KeyStoreParameters();
        truststoreParameters.setResource("jsse/localhost.p12");
        truststoreParameters.setPassword(pwd);

        TrustManagersParameters clientSSLTrustManagers = new TrustManagersParameters();
        clientSSLTrustManagers.setKeyStore(truststoreParameters);
        sslContextParameters.setTrustManagers(clientSSLTrustManagers);

        SSLContext sslContext = sslContextParameters.createSSLContext(context());
        return sslContext;
    }

    protected SSLContextParameters defineSSLContextParameters() {

        KeyStoreParameters ksp = new KeyStoreParameters();
        // ksp.setResource(this.getClass().getClassLoader().getResource("jsse/localhost.p12").toString());
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

        return sslContextParameters;
    }

    @Test
    public void testWSHttpCall() throws Exception {
        SSLContext sslContext = createSSLContext();
        WebsocketTestClient testClient = new WebsocketTestClient("wss://localhost:" + port + "/test", 10, sslContext);
        testClient.connect();

        getMockEndpoint("mock:client").expectedBodiesReceived("Hello from WS client");

        testClient.sendTextMessage("Hello from WS client");
        assertTrue(testClient.await(10, TimeUnit.SECONDS));

        MockEndpoint.assertIsSatisfied(context);

        assertEquals(10, testClient.getReceived().size());
        for (int i = 0; i < 10; i++) {
            assertEquals(">> Welcome on board!", testClient.getReceived().get(i));
        }

        testClient.close();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {

                WebsocketComponent websocketComponent = (WebsocketComponent) context.getComponent("websocket");
                websocketComponent.setSslContextParameters(defineSSLContextParameters());
                websocketComponent.setPort(port);
                websocketComponent.setMinThreads(1);
                websocketComponent.setMaxThreads(25);

                from("websocket://test")
                        .log(">>> Message received from WebSocket Client : ${body}")
                        .to("mock:client")
                        .loop(10)
                        .setBody().constant(">> Welcome on board!")
                        .to("websocket://test");
            }
        };
    }
}
