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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.thrift.generated.InvalidOperation;
import org.apache.camel.component.thrift.generated.Operation;
import org.apache.camel.component.thrift.generated.Work;
import org.apache.camel.support.SynchronizationAdapter;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThriftProducerAsyncTest extends ThriftProducerBaseTest {
    private static final Logger LOG = LoggerFactory.getLogger(ThriftProducerAsyncTest.class);

    private Object responseBody;

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void testCalculateMethodInvocation() throws Exception {
        LOG.info("Thrift calculate method async test start");

        List requestBody = new ArrayList();
        final CountDownLatch latch = new CountDownLatch(1);

        requestBody.add((int)1);
        requestBody.add(new Work(THRIFT_TEST_NUM1, THRIFT_TEST_NUM2, Operation.MULTIPLY));

        template.asyncCallbackSendBody("direct:thrift-calculate", requestBody, new SynchronizationAdapter() {

            @Override
            public void onComplete(Exchange exchange) {
                responseBody = exchange.getOut().getBody();
                latch.countDown();
            }
            
            @Override
            public void onFailure(Exchange exchange) {
                responseBody = exchange.getException();
                latch.countDown();
            }
        });
        latch.await(5, TimeUnit.SECONDS);

        assertNotNull(responseBody);
        assertTrue(responseBody instanceof Integer);
        assertEquals(THRIFT_TEST_NUM1 * THRIFT_TEST_NUM2, responseBody);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void testAddMethodInvocation() throws Exception {
        LOG.info("Thrift add method (primitive parameters only) async test start");

        final CountDownLatch latch = new CountDownLatch(1);
        List requestBody = new ArrayList();
        responseBody = null;

        requestBody.add((int)THRIFT_TEST_NUM1);
        requestBody.add((int)THRIFT_TEST_NUM2);

        template.asyncCallbackSendBody("direct:thrift-add", requestBody, new SynchronizationAdapter() {

            @Override
            public void onComplete(Exchange exchange) {
                responseBody = exchange.getOut().getBody();
                latch.countDown();
            }
            
            @Override
            public void onFailure(Exchange exchange) {
                responseBody = exchange.getException();
                latch.countDown();
            }
        });
        latch.await(5, TimeUnit.SECONDS);

        assertNotNull(responseBody);
        assertTrue(responseBody instanceof Integer);
        assertEquals(THRIFT_TEST_NUM1 + THRIFT_TEST_NUM2, responseBody);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void testCalculateWithException() throws Exception {
        LOG.info("Thrift calculate method with business exception async test start");

        final CountDownLatch latch = new CountDownLatch(1);
        List requestBody = new ArrayList();

        requestBody.add((int)1);
        requestBody.add(new Work(THRIFT_TEST_NUM1, 0, Operation.DIVIDE));

        template.asyncCallbackSendBody("direct:thrift-calculate", requestBody, new SynchronizationAdapter() {

            @Override
            public void onComplete(Exchange exchange) {
                latch.countDown();
            }

            @Override
            public void onFailure(Exchange exchange) {
                responseBody = exchange.getException();
                latch.countDown();
            }
        });
        latch.await(5, TimeUnit.SECONDS);
        
        assertTrue("Get an InvalidOperation exception", responseBody instanceof InvalidOperation);
 
    }
    
    @Test
    public void testVoidMethodInvocation() throws Exception {
        LOG.info("Thrift method with empty parameters and void output async test start");
        
        final CountDownLatch latch = new CountDownLatch(1);
        final Object requestBody = null;
        
        responseBody = new Object();
        template.asyncCallbackSendBody("direct:thrift-ping", requestBody, new SynchronizationAdapter() {

            @Override
            public void onComplete(Exchange exchange) {
                responseBody = exchange.getOut().getBody();
                latch.countDown();
            }

            @Override
            public void onFailure(Exchange exchange) {
                responseBody = exchange.getException();
                latch.countDown();
            }
        });
        latch.await(5, TimeUnit.SECONDS);
        
        assertNull(responseBody);
    }
    
    @Test
    public void testOneWayMethodInvocation() throws Exception {
        LOG.info("Thrift one-way method async test start");

        final CountDownLatch latch = new CountDownLatch(1);
        final Object requestBody = null;
        
        responseBody = new Object();
        template.asyncCallbackSendBody("direct:thrift-zip", requestBody, new SynchronizationAdapter() {

            @Override
            public void onComplete(Exchange exchange) {
                responseBody = exchange.getOut().getBody();
                latch.countDown();
            }

            @Override
            public void onFailure(Exchange exchange) {
                responseBody = exchange.getException();
                latch.countDown();
            }
        });
        latch.await(5, TimeUnit.SECONDS);
        
        assertNull(responseBody);
    }
    
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void testAllTypesMethodInvocation() throws Exception {
        LOG.info("Thrift method with all possile types async test start");
        
        final CountDownLatch latch = new CountDownLatch(1);
        List requestBody = new ArrayList();

        requestBody.add((boolean)true);
        requestBody.add((byte)THRIFT_TEST_NUM1);
        requestBody.add((short)THRIFT_TEST_NUM1);
        requestBody.add((int)THRIFT_TEST_NUM1);
        requestBody.add((long)THRIFT_TEST_NUM1);
        requestBody.add((double)THRIFT_TEST_NUM1);
        requestBody.add("empty");
        requestBody.add(ByteBuffer.allocate(10));
        requestBody.add(new Work(THRIFT_TEST_NUM1, THRIFT_TEST_NUM2, Operation.MULTIPLY));
        requestBody.add(new ArrayList<Integer>());
        requestBody.add(new HashSet<String>());
        requestBody.add(new HashMap<String, Long>());

        responseBody = new Object();
        template.asyncCallbackSendBody("direct:thrift-alltypes", requestBody, new SynchronizationAdapter() {

            @Override
            public void onComplete(Exchange exchange) {
                responseBody = exchange.getOut().getBody();
                latch.countDown();
            }

            @Override
            public void onFailure(Exchange exchange) {
                responseBody = exchange.getException();
                latch.countDown();
            }
        });
        latch.await(5, TimeUnit.SECONDS);

        assertNotNull(responseBody);
        assertTrue(responseBody instanceof Integer);
        assertEquals(1, responseBody);
    }
    
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void testEchoMethodInvocation() throws Exception {
        LOG.info("Thrift echo method (return output as pass input parameter) async test start");

        final CountDownLatch latch = new CountDownLatch(1);
        List requestBody = new ArrayList();

        requestBody.add(new Work(THRIFT_TEST_NUM1, THRIFT_TEST_NUM2, Operation.MULTIPLY));
        
        responseBody = new Object();
        template.asyncCallbackSendBody("direct:thrift-echo", requestBody, new SynchronizationAdapter() {

            @Override
            public void onComplete(Exchange exchange) {
                responseBody = exchange.getOut().getBody();
                latch.countDown();
            }

            @Override
            public void onFailure(Exchange exchange) {
                responseBody = exchange.getException();
                latch.countDown();
            }
        });
        latch.await(5, TimeUnit.SECONDS);

        assertNotNull(responseBody);
        assertTrue(responseBody instanceof Work);
        assertEquals(THRIFT_TEST_NUM1, ((Work)responseBody).num1);
        assertEquals(Operation.MULTIPLY, ((Work)responseBody).op);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:thrift-calculate")
                    .to("thrift://localhost:" + THRIFT_TEST_PORT + "/org.apache.camel.component.thrift.generated.Calculator?method=calculate");
                from("direct:thrift-add")
                    .to("thrift://localhost:" + THRIFT_TEST_PORT + "/org.apache.camel.component.thrift.generated.Calculator?method=add");
                from("direct:thrift-ping")
                    .to("thrift://localhost:" + THRIFT_TEST_PORT + "/org.apache.camel.component.thrift.generated.Calculator?method=ping");
                from("direct:thrift-zip")
                    .to("thrift://localhost:" + THRIFT_TEST_PORT + "/org.apache.camel.component.thrift.generated.Calculator?method=zip");
                from("direct:thrift-alltypes")
                    .to("thrift://localhost:" + THRIFT_TEST_PORT + "/org.apache.camel.component.thrift.generated.Calculator?method=alltypes");
                from("direct:thrift-echo")
                    .to("thrift://localhost:" + THRIFT_TEST_PORT + "/org.apache.camel.component.thrift.generated.Calculator?method=echo");
            }
        };
    }
}
