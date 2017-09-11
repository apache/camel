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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.thrift.generated.InvalidOperation;
import org.apache.camel.component.thrift.generated.Operation;
import org.apache.camel.component.thrift.generated.Work;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThriftProducerSyncTest extends ThriftProducerBaseTest {
    private static final Logger LOG = LoggerFactory.getLogger(ThriftProducerSyncTest.class);

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void testCalculateMethodInvocation() throws Exception {
        LOG.info("Thrift calculate method sync test start");

        List requestBody = new ArrayList();

        requestBody.add((int)1);
        requestBody.add(new Work(THRIFT_TEST_NUM1, THRIFT_TEST_NUM2, Operation.MULTIPLY));

        Object responseBody = template.requestBody("direct:thrift-calculate", requestBody);

        assertNotNull(responseBody);
        assertTrue(responseBody instanceof Integer);
        assertEquals(THRIFT_TEST_NUM1 * THRIFT_TEST_NUM2, responseBody);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void testAddMethodInvocation() throws Exception {
        LOG.info("Thrift add method (primitive parameters only) sync test start");

        List requestBody = new ArrayList();

        requestBody.add((int)THRIFT_TEST_NUM1);
        requestBody.add((int)THRIFT_TEST_NUM2);

        Object responseBody = template.requestBody("direct:thrift-add", requestBody);

        assertNotNull(responseBody);
        assertTrue(responseBody instanceof Integer);
        assertEquals(THRIFT_TEST_NUM1 + THRIFT_TEST_NUM2, responseBody);
    }
    
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void testCalculateWithException() throws Exception {
        LOG.info("Thrift calculate method with business exception sync test start");

        List requestBody = new ArrayList();

        requestBody.add((int)1);
        requestBody.add(new Work(THRIFT_TEST_NUM1, 0, Operation.DIVIDE));

        try {
            template.requestBody("direct:thrift-calculate", requestBody);
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
        Object responseBody = template.requestBody("direct:thrift-ping", requestBody);
        assertNull(responseBody);
    }
    
    @Test
    public void testOneWayMethodInvocation() throws Exception {
        LOG.info("Thrift one-way method sync test start");

        Object requestBody = null;
        Object responseBody = template.requestBody("direct:thrift-zip", requestBody);
        assertNull(responseBody);
    }
    
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void testAllTypesMethodInvocation() throws Exception {
        LOG.info("Thrift method with all possile types sync test start");
        
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

        Object responseBody = template.requestBody("direct:thrift-alltypes", requestBody);

        assertNotNull(responseBody);
        assertTrue(responseBody instanceof Integer);
        assertEquals(1, responseBody);
    }
    
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void testEchoMethodInvocation() throws Exception {
        LOG.info("Thrift echo method (return output as pass input parameter) sync test start");

        List requestBody = new ArrayList();

        requestBody.add(new Work(THRIFT_TEST_NUM1, THRIFT_TEST_NUM2, Operation.MULTIPLY));
        
        Object responseBody = template.requestBody("direct:thrift-echo", requestBody);

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
                    .to("thrift://localhost:" + THRIFT_TEST_PORT + "/org.apache.camel.component.thrift.generated.Calculator?method=calculate&synchronous=true");
                from("direct:thrift-add")
                    .to("thrift://localhost:" + THRIFT_TEST_PORT + "/org.apache.camel.component.thrift.generated.Calculator?method=add&synchronous=true");
                from("direct:thrift-ping")
                    .to("thrift://localhost:" + THRIFT_TEST_PORT + "/org.apache.camel.component.thrift.generated.Calculator?method=ping&synchronous=true");
                from("direct:thrift-zip")
                    .to("thrift://localhost:" + THRIFT_TEST_PORT + "/org.apache.camel.component.thrift.generated.Calculator?method=zip&synchronous=true");
                from("direct:thrift-alltypes")
                    .to("thrift://localhost:" + THRIFT_TEST_PORT + "/org.apache.camel.component.thrift.generated.Calculator?method=alltypes&synchronous=true");
                from("direct:thrift-echo")
                    .to("thrift://localhost:" + THRIFT_TEST_PORT + "/org.apache.camel.component.thrift.generated.Calculator?method=echo&synchronous=true");
            }
        };
    }
}
