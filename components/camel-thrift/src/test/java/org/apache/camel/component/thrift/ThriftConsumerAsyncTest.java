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
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.thrift.generated.Calculator;
import org.apache.camel.component.thrift.generated.Operation;
import org.apache.camel.component.thrift.generated.Work;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.async.TAsyncClientManager;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TNonblockingSocket;
import org.apache.thrift.transport.TNonblockingTransport;
import org.apache.thrift.transport.TTransportException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThriftConsumerAsyncTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(ThriftConsumerAsyncTest.class);
    private static final int THRIFT_TEST_PORT = AvailablePortFinder.getNextAvailable();
    private static final int THRIFT_TEST_NUM1 = 12;
    private static final int THRIFT_TEST_NUM2 = 13;
    private static Calculator.AsyncClient thriftClient;

    private TNonblockingTransport transport;
    private int calculateResult;
    private int zipResult = -1;
    private int pingResult = -1;
    private int allTypesResult;
    private Work echoResult;

    @Before
    public void startThriftClient() throws IOException, TTransportException {
        if (transport == null) {
            LOG.info("Connecting to the Thrift server on port: {}", THRIFT_TEST_PORT);
            transport = new TNonblockingSocket("localhost", THRIFT_TEST_PORT);
            thriftClient = (new Calculator.AsyncClient.Factory(new TAsyncClientManager(), new TBinaryProtocol.Factory())).getAsyncClient(transport);
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

    @Test
    public void testCalculateMethodInvocation() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Work work = new Work(THRIFT_TEST_NUM1, THRIFT_TEST_NUM2, Operation.MULTIPLY);

        thriftClient.calculate(1, work, new AsyncMethodCallback<Integer>() {

            @Override
            public void onComplete(Integer response) {
                calculateResult = response;
                latch.countDown();
            }

            @Override
            public void onError(Exception exception) {
                LOG.info("Exception", exception);
                latch.countDown();
            }

        });
        latch.await(5, TimeUnit.SECONDS);

        MockEndpoint mockEndpoint = getMockEndpoint("mock:thrift-service");
        mockEndpoint.expectedMessageCount(1);
        mockEndpoint.expectedHeaderValuesReceivedInAnyOrder(ThriftConstants.THRIFT_METHOD_NAME_HEADER, "calculate");
        mockEndpoint.assertIsSatisfied();

        assertEquals(THRIFT_TEST_NUM1 * THRIFT_TEST_NUM2, calculateResult);
    }

    @Test
    public void testVoidMethodInvocation() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        thriftClient.ping(new AsyncMethodCallback<Void>() {

            @Override
            public void onComplete(Void response) {
                pingResult = 0;
                latch.countDown();
            }

            @Override
            public void onError(Exception exception) {
                LOG.info("Exception", exception);
                latch.countDown();
            }

        });
        latch.await(5, TimeUnit.SECONDS);

        MockEndpoint mockEndpoint = getMockEndpoint("mock:thrift-service");
        mockEndpoint.expectedMessageCount(1);
        mockEndpoint.expectedHeaderValuesReceivedInAnyOrder(ThriftConstants.THRIFT_METHOD_NAME_HEADER, "ping");
        mockEndpoint.assertIsSatisfied();

        assertEquals(0, pingResult);
    }

    @Test
    public void testOneWayMethodInvocation() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        thriftClient.zip(new AsyncMethodCallback<Void>() {

            @Override
            public void onComplete(Void response) {
                zipResult = 0;
                latch.countDown();
            }

            @Override
            public void onError(Exception exception) {
                LOG.info("Exception", exception);
                latch.countDown();
            }

        });
        latch.await(5, TimeUnit.SECONDS);

        MockEndpoint mockEndpoint = getMockEndpoint("mock:thrift-service");
        mockEndpoint.expectedMessageCount(1);
        mockEndpoint.expectedHeaderValuesReceivedInAnyOrder(ThriftConstants.THRIFT_METHOD_NAME_HEADER, "zip");
        mockEndpoint.assertIsSatisfied();

        assertEquals(0, zipResult);
    }

    @Test
    public void testAllTypesMethodInvocation() throws Exception {
        LOG.info("Thrift method with all possile types async test start");

        final CountDownLatch latch = new CountDownLatch(1);

        thriftClient.alltypes(true, (byte)THRIFT_TEST_NUM1, (short)THRIFT_TEST_NUM1, THRIFT_TEST_NUM1, THRIFT_TEST_NUM1, THRIFT_TEST_NUM1, "empty",
                              ByteBuffer.allocate(10), new Work(THRIFT_TEST_NUM1, THRIFT_TEST_NUM2, Operation.MULTIPLY), new ArrayList<Integer>(), new HashSet<String>(),
                              new HashMap<String, Long>(), new AsyncMethodCallback<Integer>() {

                                  @Override
                                  public void onComplete(Integer response) {
                                      allTypesResult = response;
                                      latch.countDown();
                                  }

                                  @Override
                                  public void onError(Exception exception) {
                                      LOG.info("Exception", exception);
                                      latch.countDown();
                                  }

                              });
        latch.await(5, TimeUnit.SECONDS);

        MockEndpoint mockEndpoint = getMockEndpoint("mock:thrift-service");
        mockEndpoint.expectedMessageCount(1);
        mockEndpoint.expectedHeaderValuesReceivedInAnyOrder(ThriftConstants.THRIFT_METHOD_NAME_HEADER, "alltypes");
        mockEndpoint.assertIsSatisfied();

        assertEquals(THRIFT_TEST_NUM1, allTypesResult);
    }
    
    @Test
    public void testEchoMethodInvocation() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Work work = new Work(THRIFT_TEST_NUM1, THRIFT_TEST_NUM2, Operation.MULTIPLY);

        thriftClient.echo(work, new AsyncMethodCallback<Work>() {

            @Override
            public void onComplete(Work response) {
                echoResult = response;
                latch.countDown();
            }

            @Override
            public void onError(Exception exception) {
                LOG.info("Exception", exception);
                latch.countDown();
            }

        });
        latch.await(5, TimeUnit.SECONDS);

        MockEndpoint mockEndpoint = getMockEndpoint("mock:thrift-service");
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
                from("thrift://localhost:" + THRIFT_TEST_PORT + "/org.apache.camel.component.thrift.generated.Calculator")
                    .to("mock:thrift-service").choice()
                        .when(header(ThriftConstants.THRIFT_METHOD_NAME_HEADER).isEqualTo("calculate")).setBody(simple(new Integer(THRIFT_TEST_NUM1 * THRIFT_TEST_NUM2).toString()))
                        .when(header(ThriftConstants.THRIFT_METHOD_NAME_HEADER).isEqualTo("ping"))
                        .when(header(ThriftConstants.THRIFT_METHOD_NAME_HEADER).isEqualTo("zip"))
                        .when(header(ThriftConstants.THRIFT_METHOD_NAME_HEADER).isEqualTo("alltypes")).setBody(simple(new Integer(THRIFT_TEST_NUM1).toString()))
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
