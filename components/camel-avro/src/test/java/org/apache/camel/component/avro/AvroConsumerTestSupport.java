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

package org.apache.camel.component.avro;

import java.io.IOException;

import org.apache.avro.Protocol;
import org.apache.avro.ipc.Requestor;
import org.apache.avro.ipc.Transceiver;
import org.apache.camel.CamelContext;
import org.apache.camel.avro.generated.Key;
import org.apache.camel.avro.generated.KeyValueProtocol;
import org.apache.camel.avro.generated.Value;
import org.apache.camel.avro.impl.KeyValueProtocolImpl;

import org.junit.After;
import org.junit.Test;

public abstract class AvroConsumerTestSupport extends AvroTestSupport {

    Transceiver transceiver;
    Requestor requestor;
    KeyValueProtocolImpl keyValue = new KeyValueProtocolImpl();

    protected abstract void initializeTranceiver() throws IOException;

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();

        if (transceiver != null) {
            transceiver.close();
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

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        Protocol protocol = KeyValueProtocol.PROTOCOL;
        AvroConfiguration configuration = new AvroConfiguration();
        configuration.setProtocol(protocol);
        AvroComponent component = new AvroComponent(context);
        component.setConfiguration(configuration);
        context.addComponent("avro", component);
        return context;
    }
}