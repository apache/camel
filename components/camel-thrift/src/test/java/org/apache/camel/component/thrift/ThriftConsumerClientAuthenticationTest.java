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
package org.apache.camel.component.thrift;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.thrift.generated.Calculator;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.jsse.ClientAuthentication;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.SSLContextServerParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TSSLTransportFactory;
import org.apache.thrift.transport.TTransport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The SSL consumer applies the server parameters of sslParameters, such as a required client certificate.
 */
class ThriftConsumerClientAuthenticationTest extends CamelTestSupport {

    private static final String TRUST_STORE_RESOURCE = "file:src/test/resources/certs/truststore.jks";
    private static final String KEY_STORE_RESOURCE = "file:src/test/resources/certs/keystore.jks";
    private static final String SECURITY_STORE_PASSWORD = "camelinaction";
    private static final int THRIFT_TEST_PORT = AvailablePortFinder.getNextAvailable();
    private static final int THRIFT_CLIENT_TIMEOUT = 2000;

    @Test
    void clientWithoutCertificateIsRejected() throws Exception {
        TSSLTransportFactory.TSSLTransportParameters params = new TSSLTransportFactory.TSSLTransportParameters();
        params.setTrustStore(TRUST_STORE_RESOURCE, SECURITY_STORE_PASSWORD);

        // depending on the TLS version the handshake fails when the connection is opened or on the first call
        assertThrows(TException.class, () -> {
            TTransport transport = open(params);
            try {
                new Calculator.Client(new TBinaryProtocol(transport)).add(12, 13);
            } finally {
                transport.close();
            }
        });
    }

    @Test
    void clientWithTrustedCertificateIsServed() throws Exception {
        TSSLTransportFactory.TSSLTransportParameters params = new TSSLTransportFactory.TSSLTransportParameters();
        params.setTrustStore(TRUST_STORE_RESOURCE, SECURITY_STORE_PASSWORD);
        params.setKeyStore(KEY_STORE_RESOURCE, SECURITY_STORE_PASSWORD);

        TTransport transport = open(params);
        try {
            Calculator.Client client = new Calculator.Client(new TBinaryProtocol(transport));
            assertEquals(25, client.add(12, 13));
        } finally {
            transport.close();
        }
    }

    private TTransport open(TSSLTransportFactory.TSSLTransportParameters params) throws Exception {
        return TSSLTransportFactory.getClientSocket("localhost", THRIFT_TEST_PORT, THRIFT_CLIENT_TIMEOUT, params);
    }

    @Override
    protected void bindToRegistry(Registry registry) {
        KeyStoreParameters keyStore = new KeyStoreParameters();
        keyStore.setResource(KEY_STORE_RESOURCE);
        keyStore.setPassword(SECURITY_STORE_PASSWORD);
        KeyManagersParameters keyManagers = new KeyManagersParameters();
        keyManagers.setKeyStore(keyStore);
        keyManagers.setKeyPassword(SECURITY_STORE_PASSWORD);

        KeyStoreParameters trustStore = new KeyStoreParameters();
        trustStore.setResource(TRUST_STORE_RESOURCE);
        trustStore.setPassword(SECURITY_STORE_PASSWORD);
        TrustManagersParameters trustManagers = new TrustManagersParameters();
        trustManagers.setKeyStore(trustStore);

        SSLContextServerParameters serverParameters = new SSLContextServerParameters();
        serverParameters.setClientAuthentication(ClientAuthentication.REQUIRE.name());

        SSLContextParameters sslParameters = new SSLContextParameters();
        sslParameters.setKeyManagers(keyManagers);
        sslParameters.setTrustManagers(trustManagers);
        sslParameters.setServerParameters(serverParameters);

        registry.bind("sslParams", sslParameters);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("thrift://localhost:" + THRIFT_TEST_PORT
                     + "/org.apache.camel.component.thrift.generated.Calculator"
                     + "?negotiationType=SSL&sslParameters=#sslParams&synchronous=true")
                        .setBody(constant(25));
            }
        };
    }
}
