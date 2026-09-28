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

import java.security.KeyStore;

import org.apache.camel.CamelContext;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class MainSSLTrustStoreTest {

    private static KeyStore emptyTrustStore() throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        return ks;
    }

    @Test
    public void testTrustStoreBean() throws Exception {
        KeyStore trust = emptyTrustStore();
        Main main = new Main();
        main.bind("myTrust", trust);
        main.addInitialProperty("camel.ssl.enabled", "true");
        main.addInitialProperty("camel.ssl.selfSigned", "true");
        main.addInitialProperty("camel.ssl.trustStore", "#bean:myTrust");
        main.start();
        try {
            CamelContext context = main.getCamelContext();
            SSLContextParameters scp = context.getSSLContextParameters();
            assertNotNull(scp);
            assertSame(trust, scp.getTrustManagers().getKeyStore().createKeyStore());
            assertDoesNotThrow(() -> scp.createSSLContext(context));
        } finally {
            main.stop();
        }
    }

    @Test
    public void testOnlyTrustStore() throws Exception {
        Main main = new Main();
        main.bind("myTrust", emptyTrustStore());
        main.addInitialProperty("camel.ssl.enabled", "true");
        main.addInitialProperty("camel.ssl.trustStore", "#bean:myTrust");
        main.start();
        try {
            CamelContext context = main.getCamelContext();
            SSLContextParameters scp = context.getSSLContextParameters();
            // client side only, so there are no key managers
            assertNotNull(scp);
            assertNull(scp.getKeyManagers());
            assertNotNull(scp.getTrustManagers());
            assertDoesNotThrow(() -> scp.createSSLContext(context));
        } finally {
            main.stop();
        }
    }
}
