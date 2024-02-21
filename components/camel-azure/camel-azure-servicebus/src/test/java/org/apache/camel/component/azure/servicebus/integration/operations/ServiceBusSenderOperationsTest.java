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
package org.apache.camel.component.azure.servicebus.integration.operations;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.StreamSupport;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceiverAsyncClient;
import com.azure.messaging.servicebus.ServiceBusSenderAsyncClient;
import org.apache.camel.component.azure.servicebus.ServiceBusTestUtils;
import org.apache.camel.component.azure.servicebus.client.ServiceBusReceiverAsyncClientWrapper;
import org.apache.camel.component.azure.servicebus.client.ServiceBusSenderAsyncClientWrapper;
import org.apache.camel.component.azure.servicebus.operations.ServiceBusSenderOperations;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "connectionString", matches = ".*",
                         disabledReason = "Make sure to supply azure servicebus connectionString, e.g:  mvn verify -DconnectionString=string")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ServiceBusSenderOperationsTest {

    private ServiceBusSenderAsyncClientWrapper clientSenderWrapper;
    private ServiceBusReceiverAsyncClientWrapper clientReceiverWrapper;

    @BeforeAll
    void prepare() throws Exception {
        final Properties properties = ServiceBusTestUtils.loadAzureAccessFromJvmEnv();

        final ServiceBusSenderAsyncClient senderClient = new ServiceBusClientBuilder()
                .connectionString(properties.getProperty(ServiceBusTestUtils.CONNECTION_STRING))
                .sender()
                .buildAsyncClient();

        clientSenderWrapper = new ServiceBusSenderAsyncClientWrapper(senderClient);
    }

    @BeforeEach
    void prepareReceiver() throws Exception {
        final Properties properties = ServiceBusTestUtils.loadAzureAccessFromJvmEnv();

        final ServiceBusReceiverAsyncClient receiverClient = new ServiceBusClientBuilder()
                .connectionString(properties.getProperty(ServiceBusTestUtils.CONNECTION_STRING))
                .receiver()
                .topicName(properties.getProperty(ServiceBusTestUtils.TOPIC_NAME))
                .subscriptionName(properties.getProperty(ServiceBusTestUtils.SUBSCRIPTION_NAME))
                .buildAsyncClient();

        clientReceiverWrapper = new ServiceBusReceiverAsyncClientWrapper(receiverClient);
    }

    @AfterAll
    void closeClient() {
        clientSenderWrapper.close();
    }

    @AfterEach
    void closeSubscriber() {
        clientReceiverWrapper.close();
    }

    @Test
    void testSendSingleMessage() {
        final ServiceBusSenderOperations operations = new ServiceBusSenderOperations(clientSenderWrapper);

        operations.sendMessages("test data", null, Map.of("customKey", "customValue"), null).block();

        final boolean exists = StreamSupport.stream(clientReceiverWrapper.receiveMessages().toIterable().spliterator(), false)
                .anyMatch(serviceBusReceivedMessage -> serviceBusReceivedMessage.getBody().toString().equals("test data"));

        assertTrue(exists, "test message body");

        //test bytes
        byte[] testByteBody = "test data".getBytes(StandardCharsets.UTF_8);
        operations.sendMessages(testByteBody, null, Map.of("customKey", "customValue"), null).block();
        final boolean exists2 = StreamSupport.stream(clientReceiverWrapper.receiveMessages().toIterable().spliterator(), false)
                .anyMatch(serviceBusReceivedMessage -> Arrays.equals(serviceBusReceivedMessage.getBody().toBytes(),
                        testByteBody));
        assertTrue(exists2, "test byte body");

        // test if we have something other than string or byte[]
        assertThrows(IllegalArgumentException.class, () -> {
            operations.sendMessages(12345, null, null, null).block();
        });
    }

    @Test
    void testSendingBatchMessages() {
        final ServiceBusSenderOperations operations = new ServiceBusSenderOperations(clientSenderWrapper);

        final List<String> inputBatch = new LinkedList<>();
        inputBatch.add("test batch 1");
        inputBatch.add("test batch 2");
        inputBatch.add("test batch 3");

        operations.sendMessages(inputBatch, null, null, null).block();

        final Spliterator<ServiceBusReceivedMessage> receivedMessages
                = clientReceiverWrapper.receiveMessages().toIterable().spliterator();

        final boolean batch1Exists = StreamSupport.stream(receivedMessages, false)
                .anyMatch(serviceBusReceivedMessage -> serviceBusReceivedMessage.getBody().toString().equals("test batch 1"));

        final boolean batch2Exists = StreamSupport.stream(receivedMessages, false)
                .anyMatch(serviceBusReceivedMessage -> serviceBusReceivedMessage.getBody().toString().equals("test batch 2"));

        final boolean batch3Exists = StreamSupport.stream(receivedMessages, false)
                .anyMatch(serviceBusReceivedMessage -> serviceBusReceivedMessage.getBody().toString().equals("test batch 3"));

        assertTrue(batch1Exists, "test message body 1");
        assertTrue(batch2Exists, "test message body 2");
        assertTrue(batch3Exists, "test message body 3");

        //test bytes
        final List<byte[]> inputBatch2 = new LinkedList<>();
        byte[] byteBody1 = "test data".getBytes(StandardCharsets.UTF_8);
        byte[] byteBody2 = "test data2".getBytes(StandardCharsets.UTF_8);
        inputBatch2.add(byteBody1);
        inputBatch2.add(byteBody2);

        operations.sendMessages(inputBatch2, null, null, null).block();
        final Spliterator<ServiceBusReceivedMessage> receivedMessages2
                = clientReceiverWrapper.receiveMessages().toIterable().spliterator();

        final boolean byteBody1Exists = StreamSupport.stream(receivedMessages2, false)
                .anyMatch(serviceBusReceivedMessage -> Arrays.equals(serviceBusReceivedMessage.getBody().toBytes(), byteBody1));
        final boolean byteBody2Exists = StreamSupport.stream(receivedMessages2, false)
                .anyMatch(serviceBusReceivedMessage -> Arrays.equals(serviceBusReceivedMessage.getBody().toBytes(), byteBody2));

        assertTrue(byteBody1Exists, "test byte body 1");
        assertTrue(byteBody2Exists, "test byte body 2");

    }

    @Test
    void testScheduleMessage() {
        final ServiceBusSenderOperations operations = new ServiceBusSenderOperations(clientSenderWrapper);

        operations.scheduleMessages("testScheduleMessage", OffsetDateTime.now(), null, null, null).block();

        final boolean exists = StreamSupport.stream(clientReceiverWrapper.receiveMessages().toIterable().spliterator(), false)
                .anyMatch(serviceBusReceivedMessage -> serviceBusReceivedMessage.getBody().toString()
                        .equals("testScheduleMessage"));

        assertTrue(exists, "test message body");

        //test bytes
        byte[] testByteBody = "test data".getBytes(StandardCharsets.UTF_8);
        operations.scheduleMessages(testByteBody, OffsetDateTime.now(), null, null, null).block();
        final boolean exists2 = StreamSupport.stream(clientReceiverWrapper.receiveMessages().toIterable().spliterator(), false)
                .anyMatch(serviceBusReceivedMessage -> Arrays.equals(serviceBusReceivedMessage.getBody().toBytes(),
                        testByteBody));
        assertTrue(exists2, "test byte body");

        // test if we have something other than string or byte[]
        assertThrows(IllegalArgumentException.class, () -> {
            operations.scheduleMessages(12345, OffsetDateTime.now(), null, null, null).block();
        });
    }

    @Test
    void testSchedulingBatchMessages() {
        final ServiceBusSenderOperations operations = new ServiceBusSenderOperations(clientSenderWrapper);

        final List<String> inputBatch = new LinkedList<>();
        inputBatch.add("testSchedulingBatchMessages 1");
        inputBatch.add("testSchedulingBatchMessages 2");
        inputBatch.add("testSchedulingBatchMessages 3");

        operations.scheduleMessages(inputBatch, OffsetDateTime.now(), null, null, null).block();

        final Spliterator<ServiceBusReceivedMessage> receivedMessages
                = clientReceiverWrapper.receiveMessages().toIterable().spliterator();

        final boolean batch1Exists = StreamSupport.stream(receivedMessages, false)
                .anyMatch(serviceBusReceivedMessage -> serviceBusReceivedMessage.getBody().toString()
                        .equals("testSchedulingBatchMessages 1"));

        final boolean batch2Exists = StreamSupport.stream(receivedMessages, false)
                .anyMatch(serviceBusReceivedMessage -> serviceBusReceivedMessage.getBody().toString()
                        .equals("testSchedulingBatchMessages 2"));

        final boolean batch3Exists = StreamSupport.stream(receivedMessages, false)
                .anyMatch(serviceBusReceivedMessage -> serviceBusReceivedMessage.getBody().toString()
                        .equals("testSchedulingBatchMessages 3"));

        assertTrue(batch1Exists, "test message body 1");
        assertTrue(batch2Exists, "test message body 2");
        assertTrue(batch3Exists, "test message body 3");

        //test bytes
        final List<byte[]> inputBatch2 = new LinkedList<>();
        byte[] byteBody1 = "test data".getBytes(StandardCharsets.UTF_8);
        byte[] byteBody2 = "test data2".getBytes(StandardCharsets.UTF_8);
        inputBatch2.add(byteBody1);
        inputBatch2.add(byteBody2);

        operations.scheduleMessages(inputBatch2, OffsetDateTime.now(), null, null, null).block();
        final Spliterator<ServiceBusReceivedMessage> receivedMessages2
                = clientReceiverWrapper.receiveMessages().toIterable().spliterator();

        final boolean byteBody1Exists = StreamSupport.stream(receivedMessages2, false)
                .anyMatch(serviceBusReceivedMessage -> Arrays.equals(serviceBusReceivedMessage.getBody().toBytes(), byteBody1));
        final boolean byteBody2Exists = StreamSupport.stream(receivedMessages2, false)
                .anyMatch(serviceBusReceivedMessage -> Arrays.equals(serviceBusReceivedMessage.getBody().toBytes(), byteBody2));

        assertTrue(byteBody1Exists, "test byte body 1");
        assertTrue(byteBody2Exists, "test byte body 2");
    }
}
