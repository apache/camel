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
package org.apache.camel.component.undertow.ws;

import java.net.URL;

import javax.net.ssl.SSLContext;

import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.undertow.BaseUndertowTest;
import org.apache.camel.component.undertow.UndertowHttpsSpringTest;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.SSLContextServerParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.apache.camel.test.infra.common.http.WebsocketTestClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UndertowWssRouteTest extends BaseUndertowTest {
    @BeforeAll
    public static void setUpJaas() throws Exception {
        URL trustStoreUrl = UndertowHttpsSpringTest.class.getClassLoader().getResource("ssl/keystore.jks");
        System.setProperty("javax.net.ssl.trustStore", trustStoreUrl.toURI().getPath());
    }

    @AfterAll
    public static void tearDownJaas() {
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

    @Test
    public void testWSHttpCall() throws Exception {
        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, InsecureTrustManagerFactory.INSTANCE.getTrustManagers(), new java.security.SecureRandom());

        WebsocketTestClient testClient = new WebsocketTestClient(
                "wss://localhost:" + getPort() + "/test",
                10, sc);
        testClient.connect();

        getMockEndpoint("mock:client").expectedBodiesReceived("Hello from WS client");

        testClient.sendTextMessage("Hello from WS client");
        assertTrue(testClient.await(10));

        MockEndpoint.assertIsSatisfied(context);

        assertEquals(10, testClient.getReceived().size());
        for (int i = 0; i < 10; i++) {
            assertEquals(">> Welcome on board!", testClient.getReceived().get(i));
        }

        testClient.close();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
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
