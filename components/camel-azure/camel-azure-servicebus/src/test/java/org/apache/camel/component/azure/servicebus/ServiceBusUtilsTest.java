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

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ServiceBusUtilsTest {

    @Test
    void testCreateServiceBusMessage() {
        // test string
        final ServiceBusMessage message1 = ServiceBusUtils.createServiceBusMessage("test string", null, null, null);

        assertEquals("test string", message1.getBody().toString());

        // test int
        final ServiceBusMessage message2
                = ServiceBusUtils.createServiceBusMessage(String.valueOf(12345), null, null, null);

        assertEquals("12345", message2.getBody().toString());

        //test bytes
        byte[] testByteBody = "test string".getBytes(StandardCharsets.UTF_8);
        final ServiceBusMessage message3 = ServiceBusUtils.createServiceBusMessage(testByteBody, null, null, null);
        assertArrayEquals(testByteBody, message3.getBody().toBytes());
    }

    @Test
    void testCreateServiceBusMessages() {
        final List<String> inputMessages = new LinkedList<>();
        inputMessages.add("test data");
        inputMessages.add(String.valueOf(12345));

        final Iterable<ServiceBusMessage> busMessages
                = ServiceBusUtils.createServiceBusMessages(inputMessages, null, null, null);

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

        final Iterable<ServiceBusMessage> busMessages2
                = ServiceBusUtils.createServiceBusMessages(inputMessages2, null, null, null);

        assertTrue(StreamSupport.stream(busMessages2.spliterator(), false)
                .anyMatch(message -> Arrays.equals(message.getBody().toBytes(), byteBody1)));
        assertTrue(StreamSupport.stream(busMessages2.spliterator(), false)
                .anyMatch(message -> Arrays.equals(message.getBody().toBytes(), byteBody2)));
    }

    @Test
    void testCreateServiceBusMessageWithSession() {
        // test string
        final ServiceBusMessage message1
                = ServiceBusUtils.createServiceBusMessage("test string", null, null, "session-1");

        assertEquals("test string", message1.getBody().toString());
        assertEquals("session-1", message1.getSessionId());

        // test int
        final ServiceBusMessage message2
                = ServiceBusUtils.createServiceBusMessage(String.valueOf(12345), null, null, "session-2");

        assertEquals("12345", message2.getBody().toString());
        assertEquals("session-2", message2.getSessionId());

        //test bytes
        byte[] testByteBody = "test string".getBytes(StandardCharsets.UTF_8);
        final ServiceBusMessage message3 = ServiceBusUtils.createServiceBusMessage(testByteBody, null, null, "session-1");
        assertArrayEquals(testByteBody, message3.getBody().toBytes());
    }

    @Test
    void testCreateServiceBusMessagesWithSession() {
        final List<String> inputMessages = new LinkedList<>();
        inputMessages.add("test data");
        inputMessages.add(String.valueOf(12345));

        final Iterable<ServiceBusMessage> busMessages
                = ServiceBusUtils.createServiceBusMessages(inputMessages, null, null, "session-1");

        assertTrue(StreamSupport.stream(busMessages.spliterator(), false)
                .anyMatch(record -> record.getBody().toString().equals("test data")));
        assertTrue(StreamSupport.stream(busMessages.spliterator(), false)
                .anyMatch(record -> record.getBody().toString().equals("12345")));
        assertTrue(StreamSupport.stream(busMessages.spliterator(), false)
                .anyMatch(record -> record.getSessionId().equals("session-1")));

        //Test bytes
        final List<byte[]> inputMessages2 = new LinkedList<>();
        byte[] byteBody1 = "test data".getBytes(StandardCharsets.UTF_8);
        byte[] byteBody2 = "test data2".getBytes(StandardCharsets.UTF_8);
        inputMessages2.add(byteBody1);
        inputMessages2.add(byteBody2);

        final Iterable<ServiceBusMessage> busMessages2
                = ServiceBusUtils.createServiceBusMessages(inputMessages2, null, null, "session-2");

        assertTrue(StreamSupport.stream(busMessages2.spliterator(), false)
                .anyMatch(message -> Arrays.equals(message.getBody().toBytes(), byteBody1)));
        assertTrue(StreamSupport.stream(busMessages2.spliterator(), false)
                .anyMatch(message -> Arrays.equals(message.getBody().toBytes(), byteBody2)));
        assertTrue(StreamSupport.stream(busMessages2.spliterator(), false)
                .anyMatch(record -> record.getSessionId().equals("session-2")));
    }

    @Test
    void validateConfigurationMissingCredentials() {
        assertThrows(IllegalArgumentException.class,
                () -> ServiceBusUtils.validateConfiguration(new ServiceBusConfiguration(), false));
    }

    @Test
    void validateConfigurationConnectionStringProvided() {
        ServiceBusConfiguration configuration = new ServiceBusConfiguration();
        configuration.setConnectionString("test");
        assertDoesNotThrow(() -> ServiceBusUtils.validateConfiguration(configuration, false));
    }

    @Test
    void validateConfigurationFQNSProvided() {
        ServiceBusConfiguration configuration = new ServiceBusConfiguration();
        configuration.setFullyQualifiedNamespace("test");
        assertDoesNotThrow(() -> ServiceBusUtils.validateConfiguration(configuration, false));
    }

    @Test
    void validateConfigurationCustomProcessorClient() {
        ServiceBusConfiguration configuration = new ServiceBusConfiguration();
        ServiceBusProcessorClient client = new ServiceBusClientBuilder()
                .connectionString("Endpoint=sb://camel.apache.org/;SharedAccessKeyName=test;SharedAccessKey=test")
                .processor()
                .queueName("test")
                .processMessage(serviceBusReceivedMessageContext -> {
                })
                .processError(serviceBusErrorContext -> {
                })
                .buildProcessorClient();
        configuration.setProcessorClient(client);
        assertDoesNotThrow(() -> ServiceBusUtils.validateConfiguration(configuration, true));
    }

    @Test
    void validateConfigurationCustomSenderClient() {
        ServiceBusConfiguration configuration = new ServiceBusConfiguration();
        ServiceBusSenderClient client = new ServiceBusClientBuilder()
                .connectionString("Endpoint=sb://camel.apache.org/;SharedAccessKeyName=test;SharedAccessKey=test")
                .sender()
                .queueName("test")
                .buildClient();
        configuration.setSenderClient(client);
        assertDoesNotThrow(() -> ServiceBusUtils.validateConfiguration(configuration, false));
    }
}
