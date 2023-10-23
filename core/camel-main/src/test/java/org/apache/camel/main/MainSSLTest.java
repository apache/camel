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
package org.apache.camel.main;

import org.apache.camel.CamelContext;
import org.apache.camel.support.jsse.ClientAuthentication;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.SSLContextServerParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MainSSLTest {

    @Test
    public void testMainSSLParameters() throws Exception {
        Main main = new Main();

        main.addInitialProperty("camel.main.sslEnabled", "true");
        main.addInitialProperty("camel.main.sslKeyStore", "server.jks");
        main.addInitialProperty("camel.main.sslKeystorePassword", "security");
        main.addInitialProperty("camel.main.sslTrustStore", "client.jks");
        main.addInitialProperty("camel.main.sslTrustStorePassword", "storepass");
        main.addInitialProperty("camel.main.sslClientAuthentication", "REQUIRE");

        main.start();

        CamelContext context = main.getCamelContext();
        assertNotNull(context);

        SSLContextParameters sslParams = context.getSSLContextParameters();
        assertNotNull(sslParams);

        KeyManagersParameters kmp = sslParams.getKeyManagers();
        assertNotNull(kmp);

        Assertions.assertEquals("security", kmp.getKeyPassword());

        KeyStoreParameters ksp = kmp.getKeyStore();
        assertNotNull(ksp);

        Assertions.assertEquals("server.jks", ksp.getResource());
        Assertions.assertEquals("security", ksp.getPassword());

        TrustManagersParameters tmp = sslParams.getTrustManagers();
        assertNotNull(tmp);

        KeyStoreParameters tsp = tmp.getKeyStore();
        Assertions.assertEquals("client.jks", tsp.getResource());
        Assertions.assertEquals("storepass", tsp.getPassword());

        SSLContextServerParameters scsp = sslParams.getServerParameters();
        assertNotNull(scsp);

        Assertions.assertEquals(ClientAuthentication.REQUIRE.name(), scsp.getClientAuthentication());

        main.stop();
    }

    @Test
    public void testMainDefaultSSLParameters() throws Exception {
        Main main = new Main();

        main.addInitialProperty("camel.main.sslEnabled", "true");
        main.addInitialProperty("camel.main.sslKeyStore", "server.jks");
        main.addInitialProperty("camel.main.sslTrustStore", "client.jks");

        main.start();

        CamelContext context = main.getCamelContext();
        assertNotNull(context);

        SSLContextParameters sslParams = context.getSSLContextParameters();
        assertNotNull(sslParams);

        KeyManagersParameters kmp = sslParams.getKeyManagers();
        assertNotNull(kmp);

        Assertions.assertEquals("changeit", kmp.getKeyPassword());

        KeyStoreParameters ksp = kmp.getKeyStore();
        assertNotNull(ksp);

        Assertions.assertEquals("server.jks", ksp.getResource());
        Assertions.assertEquals("changeit", ksp.getPassword());

        TrustManagersParameters tmp = sslParams.getTrustManagers();
        assertNotNull(tmp);

        KeyStoreParameters tsp = tmp.getKeyStore();
        Assertions.assertEquals("client.jks", tsp.getResource());
        Assertions.assertEquals("changeit", tsp.getPassword());

        SSLContextServerParameters scsp = sslParams.getServerParameters();
        assertNotNull(scsp);

        Assertions.assertEquals(ClientAuthentication.NONE.name(), scsp.getClientAuthentication());

        main.stop();
    }

    @Test
    public void testMainSSLParametersFluent() throws Exception {
        Main main = new Main();

        main.configure()
                .withSslEnabled(true)
                .withSslKeyStore("server.jks")
                .withSslKeystorePassword("security")
                .withSslTrustStore("client.jks")
                .withSslTrustStorePassword("storepass")
                .withSslClientAuthentication("REQUIRE");

        main.start();

        CamelContext context = main.getCamelContext();
        assertNotNull(context);

        SSLContextParameters sslParams = context.getSSLContextParameters();
        assertNotNull(sslParams);

        KeyManagersParameters kmp = sslParams.getKeyManagers();
        assertNotNull(kmp);

        Assertions.assertEquals("security", kmp.getKeyPassword());

        KeyStoreParameters ksp = kmp.getKeyStore();
        assertNotNull(ksp);

        Assertions.assertEquals("server.jks", ksp.getResource());
        Assertions.assertEquals("security", ksp.getPassword());

        TrustManagersParameters tmp = sslParams.getTrustManagers();
        assertNotNull(tmp);

        KeyStoreParameters tsp = tmp.getKeyStore();
        Assertions.assertEquals("client.jks", tsp.getResource());
        Assertions.assertEquals("storepass", tsp.getPassword());

        SSLContextServerParameters scsp = sslParams.getServerParameters();
        assertNotNull(scsp);

        Assertions.assertEquals(ClientAuthentication.REQUIRE.name(), scsp.getClientAuthentication());

        main.stop();
    }
}
