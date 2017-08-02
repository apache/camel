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
package org.apache.camel.component.netty.http;

import java.net.URL;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.jsse.KeyManagersParameters;
import org.apache.camel.util.jsse.KeyStoreParameters;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.apache.camel.util.jsse.TrustManagersParameters;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class NettyHttpGlobalSSLTest extends CamelTestSupport {

    @Produce
    private ProducerTemplate template;

    @EndpointInject(uri = "mock:input")
    private MockEndpoint mockEndpoint;

    private Integer port;

    @BeforeClass
    public static void setUpJaas() throws Exception {
        // ensure jsse clients can validate the self signed dummy localhost cert,
        // use the server keystore as the trust store for these tests
        URL trustStoreUrl = NettyHttpSSLTest.class.getClassLoader().getResource("jsse/localhost.ks");
        System.setProperty("javax.net.ssl.trustStore", trustStoreUrl.toURI().getPath());
    }

    @AfterClass
    public static void tearDownJaas() throws Exception {
        System.clearProperty("java.security.auth.login.config");
        System.clearProperty("javax.net.ssl.trustStore");
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        port = AvailablePortFinder.getNextAvailable(9000);

        CamelContext context = super.createCamelContext();
        SSLContextParameters sslContextParameters = new SSLContextParameters();
        KeyManagersParameters keyManagers = new KeyManagersParameters();
        keyManagers.setKeyPassword("changeit");
        KeyStoreParameters keyStore = new KeyStoreParameters();
        keyStore.setResource("jsse/localhost.ks");
        keyStore.setPassword("changeit");
        keyManagers.setKeyStore(keyStore);
        sslContextParameters.setKeyManagers(keyManagers);
        TrustManagersParameters trustManagers = new TrustManagersParameters();
        trustManagers.setKeyStore(keyStore);
        sslContextParameters.setTrustManagers(trustManagers);
        context.setSSLContextParameters(sslContextParameters);

        ((SSLContextParametersAware) context.getComponent("netty-http")).setUseGlobalSslContextParameters(true);
        return context;
    }

    @Test
    public void testSSLInOutWithNettyConsumer() throws Exception {
        mockEndpoint.expectedBodiesReceived("Hello World");

        String out = template.requestBody("https://localhost:" + port, "Hello World", String.class);
        assertEquals("Bye World", out);

        mockEndpoint.assertIsSatisfied();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("netty-http:https://localhost:" + port + "?ssl=true")
                        .to("mock:input")
                        .transform().simple("Bye World");
            }
        };
    }
}

