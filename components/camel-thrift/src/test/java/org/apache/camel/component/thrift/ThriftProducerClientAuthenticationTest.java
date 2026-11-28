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

import java.net.InetAddress;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.TrustManagerFactory;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.thrift.generated.Calculator;
import org.apache.camel.component.thrift.generated.Operation;
import org.apache.camel.component.thrift.generated.Work;
import org.apache.camel.component.thrift.impl.CalculatorSyncServerImpl;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that the SSL producer applies the key managers of sslContextParameters so that it can authenticate with a
 * client certificate (mTLS). The server requires a client certificate; a producer without key managers would fail the
 * TLS handshake.
 */
class ThriftProducerClientAuthenticationTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(ThriftProducerClientAuthenticationTest.class);

    private static final String KEY_STORE_RESOURCE = "src/test/resources/certs/keystore.jks";
    private static final String TRUST_STORE_RESOURCE = "src/test/resources/certs/truststore.jks";
    private static final String KEY_STORE_FILE_RESOURCE = "file:" + KEY_STORE_RESOURCE;
    private static final String TRUST_STORE_FILE_RESOURCE = "file:" + TRUST_STORE_RESOURCE;
    private static final String SECURITY_STORE_PASSWORD = "camelinaction";
    private static final int THRIFT_CLIENT_TIMEOUT = 2000;
    private static final int THRIFT_TEST_NUM1 = 12;
    private static final int THRIFT_TEST_NUM2 = 13;

    private TServerSocket serverTransport;
    private TServer server;
    private int thriftTestPort;

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void doPreSetup() throws Exception {
        // Build an SSLContext that knows the server's key (for TLS) and the trust store (to verify client certs)
        char[] password = SECURITY_STORE_PASSWORD.toCharArray();

        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (var is = getClass().getClassLoader().getResourceAsStream("certs/keystore.jks")) {
            keyStore.load(is, password);
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, password);

        KeyStore trustStore = KeyStore.getInstance("JKS");
        try (var is = getClass().getClassLoader().getResourceAsStream("certs/truststore.jks")) {
            trustStore.load(is, password);
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        SSLContext serverSslContext = SSLContext.getInstance("TLS");
        serverSslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        // Create a server socket that requires the client to present a certificate (mTLS)
        SSLServerSocket serverSocket = (SSLServerSocket) serverSslContext.getServerSocketFactory()
                .createServerSocket(0, 100, InetAddress.getByName("localhost"));
        serverSocket.setNeedClientAuth(true);
        thriftTestPort = serverSocket.getLocalPort();

        serverTransport = new TServerSocket(
                new TServerSocket.ServerSocketTransportArgs()
                        .serverSocket(serverSocket)
                        .clientTimeout(THRIFT_CLIENT_TIMEOUT));

        Calculator.Processor processor = new Calculator.Processor(new CalculatorSyncServerImpl());
        TThreadPoolServer.Args args = new TThreadPoolServer.Args(serverTransport);
        args.processor(processor);
        server = new TThreadPoolServer(args);

        new Thread(() -> {
            LOG.info("Thrift mTLS server started on port: {}", thriftTestPort);
            server.serve();
        }).start();
    }

    @Override
    protected void cleanupResources() throws Exception {
        if (server != null) {
            server.stop();
            serverTransport.close();
        }
    }

    @Override
    protected void bindToRegistry(Registry registry) {
        // Producer sslParameters with BOTH trust managers AND key managers (client certificate for mTLS)
        KeyStoreParameters keyStore = new KeyStoreParameters();
        keyStore.setResource(KEY_STORE_FILE_RESOURCE);
        keyStore.setPassword(SECURITY_STORE_PASSWORD);
        KeyManagersParameters keyManagers = new KeyManagersParameters();
        keyManagers.setKeyStore(keyStore);
        keyManagers.setKeyPassword(SECURITY_STORE_PASSWORD);

        KeyStoreParameters trustStore = new KeyStoreParameters();
        trustStore.setResource(TRUST_STORE_FILE_RESOURCE);
        trustStore.setPassword(SECURITY_STORE_PASSWORD);
        TrustManagersParameters trustManagers = new TrustManagersParameters();
        trustManagers.setKeyStore(trustStore);

        SSLContextParameters sslParameters = new SSLContextParameters();
        sslParameters.setKeyManagers(keyManagers);
        sslParameters.setTrustManagers(trustManagers);

        registry.bind("sslParams", sslParameters);
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void producerWithClientCertificateIsServedByMtlsServer() {
        List requestBody = new ArrayList();
        requestBody.add(1);
        requestBody.add(new Work(THRIFT_TEST_NUM1, THRIFT_TEST_NUM2, Operation.MULTIPLY));

        Object responseBody = template.requestBody("direct:thrift-mtls-calculate", requestBody);

        assertNotNull(responseBody);
        assertTrue(responseBody instanceof Integer);
        assertEquals(THRIFT_TEST_NUM1 * THRIFT_TEST_NUM2, responseBody);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:thrift-mtls-calculate")
                        .to("thrift://localhost:" + thriftTestPort
                            + "/org.apache.camel.component.thrift.generated.Calculator?"
                            + "method=calculate&negotiationType=SSL&sslParameters=#sslParams&synchronous=true");
            }
        };
    }
}
