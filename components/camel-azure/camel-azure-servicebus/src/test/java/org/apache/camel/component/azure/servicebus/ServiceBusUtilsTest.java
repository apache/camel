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
package org.apache.camel.component.azure.servicebus;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.StreamSupport;

import com.azure.messaging.servicebus.ServiceBusMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ServiceBusUtilsTest {

    @Test
    void testCreateServiceBusMessage() {
        // test string
        final ServiceBusMessage message1 = ServiceBusUtils.createServiceBusMessage("test string", null, null);

        assertEquals("test string", message1.getBody().toString());

        // test int
        final ServiceBusMessage message2 = ServiceBusUtils.createServiceBusMessage(String.valueOf(12345), null, null);

        assertEquals("12345", message2.getBody().toString());

        //test bytes
        byte[] testByteBody = "test string".getBytes(StandardCharsets.UTF_8);
        final ServiceBusMessage message3 = ServiceBusUtils.createServiceBusMessage(testByteBody, null, null);
        assertArrayEquals(testByteBody, message3.getBody().toBytes());
    }

    @Test
    void testCreateServiceBusMessages() {
        final List<String> inputMessages = new LinkedList<>();
        inputMessages.add("test data");
        inputMessages.add(String.valueOf(12345));

        final Iterable<ServiceBusMessage> busMessages = ServiceBusUtils.createServiceBusMessages(inputMessages, null, null);

        assertTrue(StreamSupport.stream(busMessages.spliterator(), false)
                .anyMatch(record -> record.getBody().toString().equals("test data")));
        assertTrue(StreamSupport.stream(busMessages.spliterator(), false)
                .anyMatch(record -> record.getBody().toString().equals("12345")));

        //Test bytes
        final List<byte[]> inputMessages2 = new LinkedList<>();
        byte[] byteBody1 = "test data".getBytes(StandardCharsets.UTF_8);
        byte[] byteBody2 = "test data2".getBytes(StandardCharsets.UTF_8);
        inputMessages2.add(byteBody1);
        inputMessages2.add(byteBody2);

        final Iterable<ServiceBusMessage> busMessages2 = ServiceBusUtils.createServiceBusMessages(inputMessages2, null, null);

        assertTrue(StreamSupport.stream(busMessages2.spliterator(), false)
                .anyMatch(message -> Arrays.equals(message.getBody().toBytes(), byteBody1)));
        assertTrue(StreamSupport.stream(busMessages2.spliterator(), false)
                .anyMatch(message -> Arrays.equals(message.getBody().toBytes(), byteBody2)));
    }
}
