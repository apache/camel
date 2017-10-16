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
package org.apache.camel.component.lumberjack;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.jsse.KeyManagersParameters;
import org.apache.camel.util.jsse.KeyStoreParameters;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.apache.camel.util.jsse.TrustManagersParameters;
import org.junit.BeforeClass;
import org.junit.Test;

public class LumberjackComponentGlobalSSLTest extends CamelTestSupport {
    private static int port;

    @BeforeClass
    public static void beforeClass() {
        port = AvailablePortFinder.getNextAvailable();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.setSSLContextParameters(createServerSSLContextParameters());
        LumberjackComponent component = (LumberjackComponent) context.getComponent("lumberjack");
        component.setUseGlobalSslContextParameters(true);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {

                // Lumberjack configured with SSL
                from("lumberjack:0.0.0.0:" + port).to("mock:output");
            }
        };
    }

    @Test
    public void shouldListenToMessagesOverSSL() throws Exception {
        // cannot test on java 1.9
        if (isJava19()) {
            return;
        }

        // We're expecting 25 messages with Maps
        MockEndpoint mock = getMockEndpoint("mock:output");
        mock.expectedMessageCount(25);
        mock.allMessages().body().isInstanceOf(Map.class);

        // When sending messages
        List<Integer> responses = LumberjackUtil.sendMessages(port, createClientSSLContextParameters());

        // Then we should have the messages we're expecting
        mock.assertIsSatisfied();

        // And we should have replied with 2 acknowledgments for each window frame
        assertEquals(Arrays.asList(10, 15), responses);
    }

    /**
     * Creates the {@link SSLContextParameters} Camel object for the Lumberjack component
     *
     * @return The {@link SSLContextParameters} Camel object for the Lumberjack component
     */
    private SSLContextParameters createServerSSLContextParameters() {
        SSLContextParameters sslContextParameters = new SSLContextParameters();

        KeyManagersParameters keyManagersParameters = new KeyManagersParameters();
        KeyStoreParameters keyStore = new KeyStoreParameters();
        keyStore.setPassword("changeit");
        keyStore.setResource("org/apache/camel/component/lumberjack/keystore.jks");
        keyManagersParameters.setKeyPassword("changeit");
        keyManagersParameters.setKeyStore(keyStore);
        sslContextParameters.setKeyManagers(keyManagersParameters);

        return sslContextParameters;
    }

    private SSLContextParameters createClientSSLContextParameters() {
        SSLContextParameters sslContextParameters = new SSLContextParameters();

        TrustManagersParameters trustManagersParameters = new TrustManagersParameters();
        KeyStoreParameters trustStore = new KeyStoreParameters();
        trustStore.setPassword("changeit");
        trustStore.setResource("org/apache/camel/component/lumberjack/keystore.jks");
        trustManagersParameters.setKeyStore(trustStore);
        sslContextParameters.setTrustManagers(trustManagersParameters);

        return sslContextParameters;
    }
}
