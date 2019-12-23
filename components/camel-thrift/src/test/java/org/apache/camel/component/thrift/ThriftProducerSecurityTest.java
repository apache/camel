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

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.thrift.generated.Calculator;
import org.apache.camel.component.thrift.generated.InvalidOperation;
import org.apache.camel.component.thrift.generated.Operation;
import org.apache.camel.component.thrift.generated.Work;
import org.apache.camel.component.thrift.impl.CalculatorSyncServerImpl;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.SimpleRegistry;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TSSLTransportFactory;
import org.apache.thrift.transport.TServerSocket;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThriftProducerSecurityTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(ThriftProducerSecurityTest.class);
    
    private static TServerSocket serverTransport;
    private static TServer server;
    @SuppressWarnings({"rawtypes"})
    private static Calculator.Processor processor;
    
    private static final int THRIFT_TEST_PORT = AvailablePortFinder.getNextAvailable();
    private static final int THRIFT_TEST_NUM1 = 12;
    private static final int THRIFT_TEST_NUM2 = 13;
    
    private static final String TRUST_STORE_SOURCE = "file:src/test/resources/certs/truststore.jks";
    private static final String KEY_STORE_SOURCE = "file:src/test/resources/certs/keystore.jks";
    private static final String SECURITY_STORE_PASSWORD = "camelinaction";
    private static final int THRIFT_CLIENT_TIMEOUT = 2000;
    
    @BeforeClass
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void startThriftServer() throws Exception {
        processor = new Calculator.Processor(new CalculatorSyncServerImpl());
        
        TSSLTransportFactory.TSSLTransportParameters sslParams = new TSSLTransportFactory.TSSLTransportParameters();
        
        sslParams.setKeyStore(KEY_STORE_SOURCE, SECURITY_STORE_PASSWORD);
        serverTransport = TSSLTransportFactory.getServerSocket(THRIFT_TEST_PORT, THRIFT_CLIENT_TIMEOUT, InetAddress.getByName("localhost"), sslParams);
        TThreadPoolServer.Args args = new TThreadPoolServer.Args(serverTransport);
        args.processor(processor);
        server = new TThreadPoolServer(args);
        
        Runnable simple = new Runnable() {
            public void run() {
                LOG.info("Thrift secured server started on port: {}", THRIFT_TEST_PORT);
                server.serve();
            }
        };
        new Thread(simple).start();
    }

    @AfterClass
    public static void stopThriftServer() throws IOException {
        if (server != null) {
            server.stop();
            serverTransport.close();
            LOG.info("Thrift secured server stoped");
        }
    }
    
    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry reg = new SimpleRegistry();        
        SSLContextParameters sslParameters = new SSLContextParameters();
        
        KeyStoreParameters keyStoreParams = new KeyStoreParameters();
        keyStoreParams.setResource(TRUST_STORE_SOURCE);
        keyStoreParams.setPassword(SECURITY_STORE_PASSWORD);
        
        TrustManagersParameters trustManagerParams = new TrustManagersParameters();
        trustManagerParams.setKeyStore(keyStoreParams);
        
        sslParameters.setTrustManagers(trustManagerParams);
        
        reg.bind("sslParams", sslParameters);
        return reg;
    }
    
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void testCalculateMethodInvocation() throws Exception {
        LOG.info("Thrift calculate method sync test start");

        List requestBody = new ArrayList();

        requestBody.add(1);
        requestBody.add(new Work(THRIFT_TEST_NUM1, THRIFT_TEST_NUM2, Operation.MULTIPLY));

        Object responseBody = template.requestBody("direct:thrift-secured-calculate", requestBody);

        assertNotNull(responseBody);
        assertTrue(responseBody instanceof Integer);
        assertEquals(THRIFT_TEST_NUM1 * THRIFT_TEST_NUM2, responseBody);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void testCalculateWithException() throws Exception {
        LOG.info("Thrift calculate method with business exception sync test start");

        List requestBody = new ArrayList();

        requestBody.add(1);
        requestBody.add(new Work(THRIFT_TEST_NUM1, 0, Operation.DIVIDE));

        try {
            template.requestBody("direct:thrift-secured-calculate", requestBody);
            fail("Expect the exception here");
        } catch (Exception ex) {
            assertTrue("Expect CamelExecutionException", ex instanceof CamelExecutionException);
            assertTrue("Get an InvalidOperation exception", ex.getCause() instanceof InvalidOperation);
        }
    }
    
    @Test
    public void testVoidMethodInvocation() throws Exception {
        LOG.info("Thrift method with empty parameters and void output sync test start");

        Object requestBody = null;
        Object responseBody = template.requestBody("direct:thrift-secured-ping", requestBody);
        assertNull(responseBody);
    }
    
    @Test
    public void testOneWayMethodInvocation() throws Exception {
        LOG.info("Thrift one-way method sync test start");

        Object requestBody = null;
        Object responseBody = template.requestBody("direct:thrift-secured-zip", requestBody);
        assertNull(responseBody);
    }
    
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void testAllTypesMethodInvocation() throws Exception {
        LOG.info("Thrift method with all possile types sync test start");
        
        List requestBody = new ArrayList();

        requestBody.add(true);
        requestBody.add((byte)THRIFT_TEST_NUM1);
        requestBody.add((short)THRIFT_TEST_NUM1);
        requestBody.add(THRIFT_TEST_NUM1);
        requestBody.add((long)THRIFT_TEST_NUM1);
        requestBody.add((double)THRIFT_TEST_NUM1);
        requestBody.add("empty");
        requestBody.add(ByteBuffer.allocate(10));
        requestBody.add(new Work(THRIFT_TEST_NUM1, THRIFT_TEST_NUM2, Operation.MULTIPLY));
        requestBody.add(new ArrayList<Integer>());
        requestBody.add(new HashSet<String>());
        requestBody.add(new HashMap<String, Long>());

        Object responseBody = template.requestBody("direct:thrift-secured-alltypes", requestBody);

        assertNotNull(responseBody);
        assertTrue(responseBody instanceof Integer);
        assertEquals(1, responseBody);
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:thrift-secured-calculate")
                    .to("thrift://localhost:" + THRIFT_TEST_PORT + "/org.apache.camel.component.thrift.generated.Calculator?" 
                        + "method=calculate&negotiationType=SSL&sslParameters=#sslParams&synchronous=true");
                from("direct:thrift-secured-add")
                    .to("thrift://localhost:" + THRIFT_TEST_PORT + "/org.apache.camel.component.thrift.generated.Calculator?"
                        + "method=add&negotiationType=SSL&sslParameters=#sslParams&synchronous=true");
                from("direct:thrift-secured-ping")
                    .to("thrift://localhost:" + THRIFT_TEST_PORT + "/org.apache.camel.component.thrift.generated.Calculator?"
                        + "method=ping&negotiationType=SSL&sslParameters=#sslParams&synchronous=true");
                from("direct:thrift-secured-zip")
                    .to("thrift://localhost:" + THRIFT_TEST_PORT + "/org.apache.camel.component.thrift.generated.Calculator?"
                        + "method=zip&negotiationType=SSL&sslParameters=#sslParams&synchronous=true");
                from("direct:thrift-secured-alltypes")
                    .to("thrift://localhost:" + THRIFT_TEST_PORT + "/org.apache.camel.component.thrift.generated.Calculator?"
                        + "method=alltypes&negotiationType=SSL&sslParameters=#sslParams&synchronous=true");
            }
        };
    }
}
