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

import org.apache.avro.ipc.Server;
import org.apache.camel.avro.generated.Key;
import org.apache.camel.avro.generated.Value;
import org.apache.camel.avro.impl.KeyValueProtocolImpl;
import org.apache.camel.avro.test.TestReflectionImpl;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class AvroProducerTestSupport extends AvroTestSupport {

    Server server;
    Server serverReflection;
    KeyValueProtocolImpl keyValue = new KeyValueProtocolImpl();
    TestReflectionImpl testReflection = new TestReflectionImpl();

    protected abstract void initializeServer() throws IOException;

    @Override
    protected void doPreSetup() throws Exception {
        super.doPreSetup();

        initializeServer();
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();

        if (server != null) {
            server.close();
        }

        if (serverReflection != null) {
            serverReflection.close();
        }
    }

    @Test
    public void testInOnly() {
        Key key = Key.newBuilder().setKey("1").build();
        Value value = Value.newBuilder().setValue("test value").build();
        Object[] request = {key, value};
        template.sendBodyAndHeader("direct:in", request, AvroConstants.AVRO_MESSAGE_NAME, "put");
        assertEquals(value, keyValue.getStore().get(key));
    }

    @Test
    public void testInOnlyWithMessageNameInRoute() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:result-in-message-name");
        mock.expectedMessageCount(1);
        Key key = Key.newBuilder().setKey("1").build();
        Value value = Value.newBuilder().setValue("test value").build();
        Object[] request = {key, value};
        template.sendBody("direct:in-message-name", request);
        assertEquals(value, keyValue.getStore().get(key));
        mock.assertIsSatisfied(5000);
    }

    @Test
    public void testInOnlyReflection() {
        String name = "Chuck";
        Object[] request = {name};
        template.sendBody("direct:in-reflection", request);
        assertEquals(name, testReflection.getName());
    }

    @Test
    public void testInOnlyWithWrongMessageNameInMessage() throws InterruptedException {
        MockEndpoint mockInMessageEnd = getMockEndpoint("mock:result-in-message-name");
        mockInMessageEnd.expectedMessageCount(0);
        MockEndpoint mockErrorChannel = getMockEndpoint("mock:in-message-name-error");
        mockErrorChannel.expectedMessageCount(1);
        Key key = Key.newBuilder().setKey("1").build();
        Value value = Value.newBuilder().setValue("test value").build();
        Object[] request = {key, value};
        template.sendBodyAndHeader("direct:in-message-name", request, AvroConstants.AVRO_MESSAGE_NAME, "/get");
        mockErrorChannel.assertIsSatisfied(5000);
        mockInMessageEnd.assertIsSatisfied();
    }

    @Test
    public void testInOut() throws InterruptedException {
        keyValue.getStore().clear();
        Key key = Key.newBuilder().setKey("2").build();
        Value value = Value.newBuilder().setValue("test value").build();
        keyValue.getStore().put(key, value);

        MockEndpoint mock = getMockEndpoint("mock:result-inout");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived(value);
        template.sendBodyAndHeader("direct:inout", key, AvroConstants.AVRO_MESSAGE_NAME, "get");
        mock.assertIsSatisfied(5000);
    }

    @Test
    public void testInOutMessageNameInRoute() throws InterruptedException {
        keyValue.getStore().clear();
        Key key = Key.newBuilder().setKey("2").build();
        Value value = Value.newBuilder().setValue("test value").build();
        keyValue.getStore().put(key, value);

        MockEndpoint mock = getMockEndpoint("mock:result-inout-message-name");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived(value);
        template.sendBody("direct:inout-message-name", key);
        mock.assertIsSatisfied(5000);
    }

    @Test
    public void testInOutReflection() throws InterruptedException {
        int age = 100;
        Object[] request = {age};

        MockEndpoint mock = getMockEndpoint("mock:result-inout-reflection");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived(++age);
        template.sendBody("direct:inout-reflection", request);
        mock.assertIsSatisfied(5000);
    }

}