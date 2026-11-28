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
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.TrustManagerFactory;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.thrift.generated.Calculator;
import org.apache.camel.component.thrift.generated.Operation;
import org.apache.camel.component.thrift.generated.Work;
import org.apache.camel.component.thrift.impl.CalculatorSyncServerImpl;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests that the SSL producer enforces hostname verification. The test certificate is issued for {@code CN=localhost}
 * with no Subject Alternative Name. A producer connecting to {@code thrift://127.0.0.1:<port>} must fail the TLS
 * handshake because {@code 127.0.0.1} does not match {@code localhost}.
 */
class ThriftProducerHostnameVerificationTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(ThriftProducerHostnameVerificationTest.class);

    private static final String TRUST_STORE_FILE_RESOURCE = "file:src/test/resources/certs/truststore.jks";
    private static final String SECURITY_STORE_PASSWORD = "camelinaction";
    private static final int THRIFT_CLIENT_TIMEOUT = 2000;

    private TServerSocket serverTransport;
    private TServer server;
    private int thriftTestPort;

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void doPreSetup() throws Exception {
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

        // Server bound to 127.0.0.1, the address the producer connects to, so the connection always reaches
        // the TLS handshake (localhost may resolve to ::1 first); the certificate is CN=localhost (no SAN)
        SSLServerSocket serverSocket = (SSLServerSocket) serverSslContext.getServerSocketFactory()
                .createServerSocket(0, 100, InetAddress.getByName("127.0.0.1"));
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
            LOG.info("Thrift SSL server started on port: {}", thriftTestPort);
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
        // Trust store only — no key managers needed (not mTLS)
        SSLContextParameters sslParameters = new SSLContextParameters();

        KeyStoreParameters trustStoreParams = new KeyStoreParameters();
        trustStoreParams.setResource(TRUST_STORE_FILE_RESOURCE);
        trustStoreParams.setPassword(SECURITY_STORE_PASSWORD);

        TrustManagersParameters trustManagers = new TrustManagersParameters();
        trustManagers.setKeyStore(trustStoreParams);

        sslParameters.setTrustManagers(trustManagers);

        registry.bind("sslParams", sslParameters);
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void producerConnectingByIpFailsHostnameVerification() {
        // The server certificate is CN=localhost (no SAN). A producer using the IP address 127.0.0.1
        // must fail the TLS handshake because the IP does not match the certificate's CN.
        List requestBody = new ArrayList();
        requestBody.add(1);
        requestBody.add(new Work(12, 13, Operation.MULTIPLY));

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("direct:thrift-ip-calculate", requestBody),
                "Expected TLS handshake failure when connecting by IP to a CN=localhost certificate");
        assertNotNull(ObjectHelper.getException(SSLHandshakeException.class, e),
                "Expected a TLS handshake failure (hostname mismatch), got: " + e);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Connect by IP address — hostname verification must reject CN=localhost
                from("direct:thrift-ip-calculate")
                        .to("thrift://127.0.0.1:" + thriftTestPort
                            + "/org.apache.camel.component.thrift.generated.Calculator?"
                            + "method=calculate&negotiationType=SSL&sslParameters=#sslParams&synchronous=true");
            }
        };
    }
}
