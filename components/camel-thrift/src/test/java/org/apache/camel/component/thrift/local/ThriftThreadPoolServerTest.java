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
package org.apache.camel.component.thrift.local;

import java.io.IOException;
import java.net.InetAddress;

import org.apache.camel.component.thrift.ThriftProducerSecurityTest;
import org.apache.camel.component.thrift.generated.Calculator;
import org.apache.camel.component.thrift.impl.CalculatorSyncServerImpl;
import org.apache.camel.component.thrift.server.ThriftThreadPoolServer;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.server.TServer;
import org.apache.thrift.transport.TSSLTransportFactory;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TTransport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TBD
 */
public class ThriftThreadPoolServerTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(ThriftProducerSecurityTest.class);

    private static final int THRIFT_TEST_PORT = AvailablePortFinder.getNextAvailable();
    private static final int THRIFT_TEST_NUM1 = 12;
    private static final int THRIFT_TEST_NUM2 = 13;

    private static final String TRUST_STORE_PATH = "src/test/resources/certs/truststore.jks";
    private static final String KEY_STORE_PATH = "src/test/resources/certs/keystore.jks";
    private static final String SECURITY_STORE_PASSWORD = "camelinaction";
    private static final int THRIFT_CLIENT_TIMEOUT = 2000;

    private static TServerSocket serverTransport;
    private static TTransport clientTransport;
    private static TServer server;
    private static TProtocol protocol;
    @SuppressWarnings({"rawtypes"})
    private static Calculator.Processor processor;

    @Before
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void startThriftServer() throws Exception {
        processor = new Calculator.Processor(new CalculatorSyncServerImpl());

        TSSLTransportFactory.TSSLTransportParameters sslParams = new TSSLTransportFactory.TSSLTransportParameters();

        sslParams.setKeyStore(KEY_STORE_PATH, SECURITY_STORE_PASSWORD);
        serverTransport = TSSLTransportFactory.getServerSocket(THRIFT_TEST_PORT, THRIFT_CLIENT_TIMEOUT, InetAddress.getByName("localhost"), sslParams);
        ThriftThreadPoolServer.Args args = new ThriftThreadPoolServer.Args(serverTransport);

        args.processor(processor);
        args.executorService(this.context().getExecutorServiceManager().newThreadPool(this, "test-server-invoker", 1, 10));
        args.startThreadPool(this.context().getExecutorServiceManager().newSingleThreadExecutor(this, "test-start-thread"));
        args.context(this.context());

        server = new ThriftThreadPoolServer(args);
        server.serve();
        LOG.info("Thrift secured server started on port: {}", THRIFT_TEST_PORT);
    }

    @After
    public void stopThriftServer() throws IOException {
        if (server != null) {
            server.stop();
            serverTransport.close();
            LOG.info("Thrift secured server stoped");
        }
    }

    @Test
    public void clientConnectionTest() throws TException {
        TSSLTransportFactory.TSSLTransportParameters sslParams = new TSSLTransportFactory.TSSLTransportParameters();
        sslParams.setTrustStore(TRUST_STORE_PATH, SECURITY_STORE_PASSWORD);
        clientTransport = TSSLTransportFactory.getClientSocket("localhost", THRIFT_TEST_PORT, 1000, sslParams);

        protocol = new TBinaryProtocol(clientTransport);
        Calculator.Client client = new Calculator.Client(protocol);
        int addResult = client.add(THRIFT_TEST_NUM1, THRIFT_TEST_NUM2);

        assertEquals(addResult, THRIFT_TEST_NUM1 + THRIFT_TEST_NUM2);
    }
}
