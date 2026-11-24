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
package org.apache.camel.coap;

import java.net.InetSocketAddress;

import javax.net.ssl.KeyManager;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.eclipse.californium.elements.config.Configuration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CoAPEndpointKeyManagerTest {

    @Test
    void existingKeyManagerIsRejectedWithDTLS() throws Exception {
        KeyManagersParameters keyManagers = new KeyManagersParameters();
        keyManagers.setKeyManager(new KeyManager() {
        });
        // DTLS used to read this key store even though a key manager was set, contradicting the key manager
        // precedence, so the key manager must be rejected even when a key store is configured too
        KeyStoreParameters keyStore = new KeyStoreParameters();
        keyStore.setResource("service.jks");
        keyStore.setPassword("security");
        keyManagers.setKeyStore(keyStore);
        keyManagers.setKeyPassword("security");
        SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setKeyManagers(keyManagers);

        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            CoAPEndpoint endpoint = context.getEndpoint("coaps://localhost:5684/TestResource", CoAPEndpoint.class);
            endpoint.setSslContextParameters(sslContextParameters);

            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> endpoint.createDTLSConnector(new InetSocketAddress(0), false, Configuration.getStandard()));
            assertTrue(e.getMessage().contains("KeyManager"), e.getMessage());
        }
    }
}
