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
package org.apache.camel.component.thrift;

import java.io.IOException;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.thrift.generated.Calculator;
import org.apache.camel.component.thrift.generated.Operation;
import org.apache.camel.component.thrift.generated.Work;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.jsse.KeyManagersParameters;
import org.apache.camel.util.jsse.KeyStoreParameters;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSSLTransportFactory;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThriftConsumerSecurityTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(ThriftConsumerSecurityTest.class);
    private static final int THRIFT_TEST_PORT = AvailablePortFinder.getNextAvailable();
    private static final int THRIFT_TEST_NUM1 = 12;
    private static final int THRIFT_TEST_NUM2 = 13;
    private static final String TRUST_STORE_PATH = "src/test/resources/certs/truststore.jks";
    private static final String KEY_STORE_PATH = "src/test/resources/certs/keystore.jks";
    private static final String SECURITY_STORE_PASSWORD = "camelinaction";
    private static final int THRIFT_CLIENT_TIMEOUT = 2000;
    
    private static Calculator.Client thriftClient;
    
    private TProtocol protocol;
    private TTransport transport;
    
    @Before
    public void startThriftSecureClient() throws IOException, TTransportException {
        if (transport == null) {
            LOG.info("Connecting to the secured Thrift server on port: {}", THRIFT_TEST_PORT);
            
            TSSLTransportFactory.TSSLTransportParameters sslParams = new TSSLTransportFactory.TSSLTransportParameters();
            
            sslParams.setTrustStore(TRUST_STORE_PATH, SECURITY_STORE_PASSWORD);
            transport = TSSLTransportFactory.getClientSocket("localhost", THRIFT_TEST_PORT, THRIFT_CLIENT_TIMEOUT, sslParams);
            
            protocol = new TBinaryProtocol(transport);
            thriftClient = new Calculator.Client(protocol);
            LOG.info("Connected to the secured Thrift server");
        }
    }

    @After
    public void stopThriftClient() throws Exception {
        if (transport != null) {
            transport.close();
            transport = null;
            LOG.info("Connection to the Thrift server closed");
        }
    }
    
    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        SSLContextParameters sslParameters = new SSLContextParameters();
        
        KeyStoreParameters keyStoreParams = new KeyStoreParameters();
        keyStoreParams.setResource(KEY_STORE_PATH);
        keyStoreParams.setPassword(SECURITY_STORE_PASSWORD);
        
        KeyManagersParameters keyManagerParams = new KeyManagersParameters();
        keyManagerParams.setKeyStore(keyStoreParams);
        
        sslParameters.setKeyManagers(keyManagerParams);
        
        jndi.bind("sslParams", sslParameters);
        return jndi;
    }
    
    @Test
    public void testCalculateMethodInvocation() throws Exception {
        LOG.info("Test Calculate method invocation");
        
        Work work = new Work(THRIFT_TEST_NUM1, THRIFT_TEST_NUM2, Operation.MULTIPLY);
        
        int calculateResult = thriftClient.calculate(1, work);
        
        MockEndpoint mockEndpoint = getMockEndpoint("mock:thrift-secure-service");
        mockEndpoint.expectedMessageCount(1);
        mockEndpoint.expectedHeaderValuesReceivedInAnyOrder(ThriftConstants.THRIFT_METHOD_NAME_HEADER, "calculate");
        mockEndpoint.assertIsSatisfied();
        
        assertEquals(THRIFT_TEST_NUM1 * THRIFT_TEST_NUM2, calculateResult);
    }
    
    @Test
    public void testEchoMethodInvocation() throws Exception {
        LOG.info("Test Echo method invocation");
        
        Work echoResult = thriftClient.echo(new Work(THRIFT_TEST_NUM1, THRIFT_TEST_NUM2, Operation.MULTIPLY));
        
        MockEndpoint mockEndpoint = getMockEndpoint("mock:thrift-secure-service");
        mockEndpoint.expectedMessageCount(1);
        mockEndpoint.expectedHeaderValuesReceivedInAnyOrder(ThriftConstants.THRIFT_METHOD_NAME_HEADER, "echo");
        mockEndpoint.assertIsSatisfied();

        assertNotNull(echoResult);
        assertTrue(echoResult instanceof Work);
        assertEquals(THRIFT_TEST_NUM1, echoResult.num1);
        assertEquals(Operation.MULTIPLY, echoResult.op);
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                
                from("thrift://localhost:" + THRIFT_TEST_PORT + "/org.apache.camel.component.thrift.generated.Calculator?negotiationType=SSL&sslParameters=#sslParams&synchronous=true")
                    .to("mock:thrift-secure-service").choice()
                        .when(header(ThriftConstants.THRIFT_METHOD_NAME_HEADER).isEqualTo("calculate")).setBody(simple(new Integer(THRIFT_TEST_NUM1 * THRIFT_TEST_NUM2).toString()))
                        .when(header(ThriftConstants.THRIFT_METHOD_NAME_HEADER).isEqualTo("echo")).setBody(simple("${body[0]}")).bean(new CalculatorMessageBuilder(), "echo");
            }
        };
    }
    
    public class CalculatorMessageBuilder {
        public Work echo(Work work) {
            return work.deepCopy();
        }
    }
}