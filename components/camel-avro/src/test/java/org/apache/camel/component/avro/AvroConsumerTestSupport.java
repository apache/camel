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
package org.apache.camel.component.avro;

import java.io.IOException;

import org.apache.avro.AvroRuntimeException;
import org.apache.avro.ipc.Requestor;
import org.apache.avro.ipc.Transceiver;
import org.apache.camel.avro.generated.Key;
import org.apache.camel.avro.generated.Value;
import org.apache.camel.avro.impl.KeyValueProtocolImpl;
import org.apache.camel.avro.test.TestPojo;
import org.apache.camel.avro.test.TestReflection;
import org.apache.camel.avro.test.TestReflectionImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public abstract class AvroConsumerTestSupport extends AvroTestSupport {
    public static final String REFLECTION_TEST_NAME = "Chucky";
    public static final int REFLECTION_TEST_AGE = 100;

    protected int avroPortMessageInRoute = setupFreePort("avroPortMessageInRoute");
    protected int avroPortForWrongMessages = setupFreePort("avroPortForWrongMessages");

    Transceiver transceiver;
    Requestor requestor;

    Transceiver transceiverMessageInRoute;
    Requestor requestorMessageInRoute;

    Transceiver transceiverForWrongMessages;
    Requestor requestorForWrongMessages;

    Transceiver reflectTransceiver;
    Requestor reflectRequestor;

    KeyValueProtocolImpl keyValue = new KeyValueProtocolImpl();
    TestReflection testReflection = new TestReflectionImpl();

    protected abstract void initializeTranceiver() throws IOException;

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();

        if (transceiver != null) {
            transceiver.close();
        }

        if (transceiverMessageInRoute != null) {
            transceiverMessageInRoute.close();
        }

        if (transceiverForWrongMessages != null) {
            transceiverForWrongMessages.close();
        }

        if (reflectTransceiver != null) {
            reflectTransceiver.close();
        }
    }

    @Test
    public void testInOnly() throws Exception {
        initializeTranceiver();
        Key key = Key.newBuilder().setKey("1").build();
        Value value = Value.newBuilder().setValue("test value").build();
        Object[] request = {key, value};
        requestor.request("put", request);
    }

    @Test
    public void testInOnlyMessageInRoute() throws Exception {
        initializeTranceiver();
        Key key = Key.newBuilder().setKey("1").build();
        Value value = Value.newBuilder().setValue("test value").build();
        Object[] request = {key, value};
        requestorMessageInRoute.request("put", request);
    }

    @Test
    public void testInOnlyReflectRequestor() throws Exception {
        initializeTranceiver();
        Object[] request = {REFLECTION_TEST_NAME};
        reflectRequestor.request("setName", request);
        assertEquals(REFLECTION_TEST_NAME, testReflection.getName());
    }

    @Test
    public void testInOnlyWrongMessageName() throws Exception {
        initializeTranceiver();
        Key key = Key.newBuilder().setKey("1").build();
        Value value = Value.newBuilder().setValue("test value").build();
        Object[] request = {key, value};
        assertThrows(AvroRuntimeException.class, () -> {
            requestorMessageInRoute.request("throwException", request);
        });
    }

    @Test
    public void testInOnlyToNotExistingRoute() throws Exception {
        initializeTranceiver();
        Key key = Key.newBuilder().setKey("1").build();
        Value value = Value.newBuilder().setValue("test value").build();
        Object[] request = {key, value};
        assertThrows(AvroRuntimeException.class, () -> {
            requestorForWrongMessages.request("get", request);
        });
    }

    @Test
    public void testInOnlyReflectSingleParameterNotSet() throws Exception {
        initializeTranceiver();
        Object[] request = {100};
        reflectRequestor.request("setAge", request);
        assertEquals(0, testReflection.getAge());
    }

    @Test
    public void testInOnlyReflectionPojoTest() throws Exception {
        initializeTranceiver();
        TestPojo testPojo = new TestPojo();
        testPojo.setPojoName("pojo1");
        Object[] request = {testPojo};
        reflectRequestor.request("setTestPojo", request);
        assertEquals(testPojo.getPojoName(), testReflection.getTestPojo().getPojoName());
    }

    @Test
    public void testInOut() throws Exception {
        initializeTranceiver();
        keyValue.getStore().clear();
        Key key = Key.newBuilder().setKey("2").build();
        Value value = Value.newBuilder().setValue("test value").build();
        keyValue.getStore().put(key, value);
        Object[] request = {key};
        Object response = requestor.request("get", request);
        assertEquals(value, response);
    }

    @Test
    public void testInOutMessageInRoute() throws Exception {
        initializeTranceiver();
        keyValue.getStore().clear();
        Key key = Key.newBuilder().setKey("2").build();
        Value value = Value.newBuilder().setValue("test value").build();
        keyValue.getStore().put(key, value);
        Object[] request = {key};
        Object response = requestorMessageInRoute.request("get", request);
        assertEquals(value, response);
    }

    @Test
    public void testInOutReflectRequestor() throws Exception {
        initializeTranceiver();
        Object[] request = {REFLECTION_TEST_AGE};
        Object response = reflectRequestor.request("increaseAge", request);
        assertEquals(testReflection.getAge(), response);
    }

    @Test
    public void testInOutReflectionPojoTest() throws Exception {
        initializeTranceiver();
        TestPojo testPojo = new TestPojo();
        testPojo.setPojoName("pojo2");
        Object[] request = {testPojo};
        reflectRequestor.request("setTestPojo", request);
        request = new Object[0];
        Object response = reflectRequestor.request("getTestPojo", request);
        assertEquals(testPojo.getPojoName(), ((TestPojo) response).getPojoName());
    }
}
