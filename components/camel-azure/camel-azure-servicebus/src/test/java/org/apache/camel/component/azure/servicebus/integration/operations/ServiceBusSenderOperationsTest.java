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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.azure.servicebus.integration.BaseServiceBusTestSupport;
import org.apache.camel.component.azure.servicebus.operations.ServiceBusSenderOperations;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = BaseServiceBusTestSupport.CONNECTION_STRING_PROPERTY_NAME, matches = ".*",
                         disabledReason = "Service Bus connection string must be supplied to run this test, e.g:  mvn verify -D"
                                          + BaseServiceBusTestSupport.CONNECTION_STRING_PROPERTY_NAME + "=connectionString")
public class ServiceBusSenderOperationsTest extends BaseServiceBusTestSupport {

    private static ServiceBusSenderClient client;
    private static ServiceBusSenderClient sessionClient;
    private static ServiceBusSenderClient batchClient;
    private static ServiceBusSenderClient batchSessionClient;

    @BeforeAll
    static void prepare() {
        client = new ServiceBusClientBuilder().connectionString(CONNECTION_STRING)
                .sender().topicName(TOPIC_NAME).buildClient();
        sessionClient = new ServiceBusClientBuilder().connectionString(CONNECTION_STRING)
                .sender().topicName(TOPIC_WITH_SESSIONS_NAME).buildClient();
        batchClient = new ServiceBusClientBuilder().connectionString(CONNECTION_STRING)
                .sender().queueName(QUEUE_NAME).buildClient();
        batchSessionClient = new ServiceBusClientBuilder().connectionString(CONNECTION_STRING)
                .sender().queueName(QUEUE_WITH_SESSIONS_NAME).buildClient();
    }

    @AfterAll
    static void closeClient() {
        client.close();
    }

    @BeforeEach
    void beforeEach() {
        receivedMessageContexts = new ArrayList<>();
    }

    @Test
    void testSendSingleMessage() throws InterruptedException {
        final ServiceBusSenderOperations operations = new ServiceBusSenderOperations(client);
        messageLatch = new CountDownLatch(2);

        try (ServiceBusProcessorClient processorClient = createTopicProcessorClient()) {
            processorClient.start();
            operations.sendMessages("test data", null, Map.of("customKey", "customValue"), null, null);
            //test bytes
            byte[] testByteBody = "test data".getBytes(StandardCharsets.UTF_8);
            operations.sendMessages(testByteBody, null, Map.of("customKey", "customValue"), null, null);

            assertTrue(messageLatch.await(3000, TimeUnit.MILLISECONDS));

            final boolean exists = receivedMessageContexts.stream()
                    .anyMatch(messageContext -> messageContext.getMessage().getBody().toString().equals("test data"));
            assertTrue(exists, "test message body");

            final boolean exists2 = receivedMessageContexts.stream()
                    .anyMatch(messageContext -> Arrays.equals(messageContext.getMessage().getBody().toBytes(), testByteBody));
            assertTrue(exists2, "test byte body");
        }

        // test if we have something other than string or byte[]
        assertThrows(IllegalArgumentException.class, () -> {
            operations.sendMessages(12345, null, null, null, null);
        });
    }

    @Test
    void testSendSingleMessageWithSessions() throws InterruptedException {
        final ServiceBusSenderOperations operations = new ServiceBusSenderOperations(sessionClient);
        messageLatch = new CountDownLatch(2);

        try (ServiceBusProcessorClient processorClient = createTopicSessionProcessorClient()) {
            processorClient.start();
            operations.sendMessages("test data", null, Map.of("customKey", "customValue"), null, "session-1");
            //test bytes
            byte[] testByteBody = "test data".getBytes(StandardCharsets.UTF_8);
            operations.sendMessages(testByteBody, null, Map.of("customKey", "customValue"), null, "session-1");

            assertTrue(messageLatch.await(5000, TimeUnit.MILLISECONDS));

            final boolean exists = receivedMessageContexts.stream()
                    .anyMatch(messageContext -> messageContext.getMessage().getBody().toString().equals("test data"));
            assertTrue(exists, "test message body");

            final boolean exists2 = receivedMessageContexts.stream()
                    .anyMatch(messageContext -> Arrays.equals(messageContext.getMessage().getBody().toBytes(), testByteBody));
            assertTrue(exists2, "test byte body");
        }

        // test if we have something other than string or byte[]
        assertThrows(IllegalArgumentException.class, () -> {
            operations.sendMessages(12345, null, null, null, "session-1");
        });
    }

    @Test
    void testSendingBatchMessages() throws InterruptedException {
        final ServiceBusSenderOperations operations = new ServiceBusSenderOperations(batchClient);
        messageLatch = new CountDownLatch(5);

        try (ServiceBusProcessorClient processorClient = createQueueProcessorClient()) {
            processorClient.start();

            final List<String> inputBatch = new LinkedList<>();
            inputBatch.add("test batch 1");
            inputBatch.add("test batch 2");
            inputBatch.add("test batch 3");

            operations.sendMessages(inputBatch, null, null, null, null);
            //test bytes
            final List<byte[]> inputBatch2 = new LinkedList<>();
            byte[] byteBody1 = "test data".getBytes(StandardCharsets.UTF_8);
            byte[] byteBody2 = "test data2".getBytes(StandardCharsets.UTF_8);
            inputBatch2.add(byteBody1);
            inputBatch2.add(byteBody2);
            operations.sendMessages(inputBatch2, null, null, null, null);

            assertTrue(messageLatch.await(8000, TimeUnit.MILLISECONDS));

            final boolean batch1Exists = receivedMessageContexts.stream()
                    .anyMatch(messageContext -> messageContext.getMessage().getBody().toString().equals("test batch 1"));
            final boolean batch2Exists = receivedMessageContexts.stream()
                    .anyMatch(messageContext -> messageContext.getMessage().getBody().toString().equals("test batch 2"));
            final boolean batch3Exists = receivedMessageContexts.stream()
                    .anyMatch(messageContext -> messageContext.getMessage().getBody().toString().equals("test batch 3"));

            assertTrue(batch1Exists, "test message body 1");
            assertTrue(batch2Exists, "test message body 2");
            assertTrue(batch3Exists, "test message body 3");

            final boolean byteBody1Exists = receivedMessageContexts.stream()
                    .anyMatch(messageContext -> Arrays.equals(messageContext.getMessage().getBody().toBytes(), byteBody1));
            final boolean byteBody2Exists = receivedMessageContexts.stream()
                    .anyMatch(messageContext -> Arrays.equals(messageContext.getMessage().getBody().toBytes(), byteBody2));

            assertTrue(byteBody1Exists, "test byte body 1");
            assertTrue(byteBody2Exists, "test byte body 2");
        }
    }

    @Test
    void testSendingBatchMessagesWithSessions() throws InterruptedException {
        final ServiceBusSenderOperations operations = new ServiceBusSenderOperations(batchSessionClient);
        messageLatch = new CountDownLatch(5);

        try (ServiceBusProcessorClient processorClient = createSessionQueueProcessorClient()) {
            processorClient.start();

            final List<String> inputBatch = new LinkedList<>();
            inputBatch.add("test batch 1");
            inputBatch.add("test batch 2");
            inputBatch.add("test batch 3");

            operations.sendMessages(inputBatch, null, null, null, "session-1");
            //test bytes
            final List<byte[]> inputBatch2 = new LinkedList<>();
            byte[] byteBody1 = "test data".getBytes(StandardCharsets.UTF_8);
            byte[] byteBody2 = "test data2".getBytes(StandardCharsets.UTF_8);
            inputBatch2.add(byteBody1);
            inputBatch2.add(byteBody2);
            operations.sendMessages(inputBatch2, null, null, null, "session-1");

            assertTrue(messageLatch.await(8000, TimeUnit.MILLISECONDS));

            final boolean batch1Exists = receivedMessageContexts.stream()
                    .anyMatch(messageContext -> messageContext.getMessage().getBody().toString().equals("test batch 1"));
            final boolean batch2Exists = receivedMessageContexts.stream()
                    .anyMatch(messageContext -> messageContext.getMessage().getBody().toString().equals("test batch 2"));
            final boolean batch3Exists = receivedMessageContexts.stream()
                    .anyMatch(messageContext -> messageContext.getMessage().getBody().toString().equals("test batch 3"));

            assertTrue(batch1Exists, "test message body 1");
            assertTrue(batch2Exists, "test message body 2");
            assertTrue(batch3Exists, "test message body 3");

            final boolean byteBody1Exists = receivedMessageContexts.stream()
                    .anyMatch(messageContext -> Arrays.equals(messageContext.getMessage().getBody().toBytes(), byteBody1));
            final boolean byteBody2Exists = receivedMessageContexts.stream()
                    .anyMatch(messageContext -> Arrays.equals(messageContext.getMessage().getBody().toBytes(), byteBody2));

            assertTrue(byteBody1Exists, "test byte body 1");
            assertTrue(byteBody2Exists, "test byte body 2");
        }
    }

    @Test
    void testScheduleMessage() throws InterruptedException {
        final ServiceBusSenderOperations operations = new ServiceBusSenderOperations(client);
        messageLatch = new CountDownLatch(2);

        try (ServiceBusProcessorClient processorClient = createTopicProcessorClient()) {
            processorClient.start();

            operations.scheduleMessages("testScheduleMessage", OffsetDateTime.now(), null, null, null, null);
            //test bytes
            byte[] testByteBody = "test data".getBytes(StandardCharsets.UTF_8);
            operations.scheduleMessages(testByteBody, OffsetDateTime.now(), null, null, null, null);

            assertTrue(messageLatch.await(3000, TimeUnit.MILLISECONDS));

            final boolean exists = receivedMessageContexts.stream()
                    .anyMatch(messageContext -> messageContext.getMessage().getBody().toString().equals("testScheduleMessage"));
            final boolean exists2 = receivedMessageContexts.stream()
                    .anyMatch(messageContext -> Arrays.equals(messageContext.getMessage().getBody().toBytes(), testByteBody));

            assertTrue(exists, "test message body");
            assertTrue(exists2, "test byte body");
        }
        // test if we have something other than string or byte[]
        assertThrows(IllegalArgumentException.class, () -> {
            operations.scheduleMessages(12345, OffsetDateTime.now(), null, null, null, null);
        });
    }

    @Test
    void testScheduleMessageWithSessions() throws InterruptedException {
        final ServiceBusSenderOperations operations = new ServiceBusSenderOperations(sessionClient);
        messageLatch = new CountDownLatch(2);

        try (ServiceBusProcessorClient processorClient = createTopicSessionProcessorClient()) {
            processorClient.start();

            operations.scheduleMessages("testScheduleMessage", OffsetDateTime.now(), null, null, null, "session-1");
            //test bytes
            byte[] testByteBody = "test data".getBytes(StandardCharsets.UTF_8);
            operations.scheduleMessages(testByteBody, OffsetDateTime.now(), null, null, null, "session-1");

            assertTrue(messageLatch.await(3000, TimeUnit.MILLISECONDS));

            final boolean exists = receivedMessageContexts.stream()
                    .anyMatch(messageContext -> messageContext.getMessage().getBody().toString().equals("testScheduleMessage"));
            final boolean exists2 = receivedMessageContexts.stream()
                    .anyMatch(messageContext -> Arrays.equals(messageContext.getMessage().getBody().toBytes(), testByteBody));

            assertTrue(exists, "test message body");
            assertTrue(exists2, "test byte body");
        }
        // test if we have something other than string or byte[]
        assertThrows(IllegalArgumentException.class, () -> {
            operations.scheduleMessages(12345, OffsetDateTime.now(), null, null, null, "session-1");
        });
    }

    @Test
    void testSchedulingBatchMessages() throws InterruptedException {
        final ServiceBusSenderOperations operations = new ServiceBusSenderOperations(client);
        messageLatch = new CountDownLatch(5);

        try (ServiceBusProcessorClient processorClient = createTopicProcessorClient()) {
            processorClient.start();

            final List<String> inputBatch = new LinkedList<>();
            inputBatch.add("testSchedulingBatchMessages 1");
            inputBatch.add("testSchedulingBatchMessages 2");
            inputBatch.add("testSchedulingBatchMessages 3");
            operations.scheduleMessages(inputBatch, OffsetDateTime.now(), null, null, null, null);
            //test bytes
            final List<byte[]> inputBatch2 = new LinkedList<>();
            byte[] byteBody1 = "test data".getBytes(StandardCharsets.UTF_8);
            byte[] byteBody2 = "test data2".getBytes(StandardCharsets.UTF_8);
            inputBatch2.add(byteBody1);
            inputBatch2.add(byteBody2);
            operations.scheduleMessages(inputBatch2, OffsetDateTime.now(), null, null, null, null);

            assertTrue(messageLatch.await(5000, TimeUnit.MILLISECONDS));

            final boolean batch1Exists = receivedMessageContexts.stream().anyMatch(
                    messageContext -> messageContext.getMessage().getBody().toString().equals("testSchedulingBatchMessages 1"));
            final boolean batch2Exists = receivedMessageContexts.stream().anyMatch(
                    messageContext -> messageContext.getMessage().getBody().toString().equals("testSchedulingBatchMessages 2"));
            final boolean batch3Exists = receivedMessageContexts.stream().anyMatch(
                    messageContext -> messageContext.getMessage().getBody().toString().equals("testSchedulingBatchMessages 3"));

            assertTrue(batch1Exists, "test message body 1");
            assertTrue(batch2Exists, "test message body 2");
            assertTrue(batch3Exists, "test message body 3");

            final boolean byteBody1Exists = receivedMessageContexts.stream()
                    .anyMatch(messageContext -> Arrays.equals(messageContext.getMessage().getBody().toBytes(), byteBody1));
            final boolean byteBody2Exists = receivedMessageContexts.stream()
                    .anyMatch(messageContext -> Arrays.equals(messageContext.getMessage().getBody().toBytes(), byteBody2));

            assertTrue(byteBody1Exists, "test byte body 1");
            assertTrue(byteBody2Exists, "test byte body 2");
        }
    }

    @Test
    void testSchedulingBatchMessagesWithSessions() throws InterruptedException {
        final ServiceBusSenderOperations operations = new ServiceBusSenderOperations(batchSessionClient);
        messageLatch = new CountDownLatch(5);

        try (ServiceBusProcessorClient processorClient = createSessionQueueProcessorClient()) {
            processorClient.start();

            final List<String> inputBatch = new LinkedList<>();
            inputBatch.add("testSchedulingBatchMessages 1");
            inputBatch.add("testSchedulingBatchMessages 2");
            inputBatch.add("testSchedulingBatchMessages 3");
            operations.scheduleMessages(inputBatch, OffsetDateTime.now(), null, null, null, "session-2");
            //test bytes
            final List<byte[]> inputBatch2 = new LinkedList<>();
            byte[] byteBody1 = "test data".getBytes(StandardCharsets.UTF_8);
            byte[] byteBody2 = "test data2".getBytes(StandardCharsets.UTF_8);
            inputBatch2.add(byteBody1);
            inputBatch2.add(byteBody2);
            operations.scheduleMessages(inputBatch2, OffsetDateTime.now(), null, null, null, "session-2");

            assertTrue(messageLatch.await(8000, TimeUnit.MILLISECONDS));

            final boolean batch1Exists = receivedMessageContexts.stream().anyMatch(
                    messageContext -> messageContext.getMessage().getBody().toString().equals("testSchedulingBatchMessages 1"));
            final boolean batch2Exists = receivedMessageContexts.stream().anyMatch(
                    messageContext -> messageContext.getMessage().getBody().toString().equals("testSchedulingBatchMessages 2"));
            final boolean batch3Exists = receivedMessageContexts.stream().anyMatch(
                    messageContext -> messageContext.getMessage().getBody().toString().equals("testSchedulingBatchMessages 3"));

            assertTrue(batch1Exists, "test message body 1");
            assertTrue(batch2Exists, "test message body 2");
            assertTrue(batch3Exists, "test message body 3");

            final boolean byteBody1Exists = receivedMessageContexts.stream()
                    .anyMatch(messageContext -> Arrays.equals(messageContext.getMessage().getBody().toBytes(), byteBody1));
            final boolean byteBody2Exists = receivedMessageContexts.stream()
                    .anyMatch(messageContext -> Arrays.equals(messageContext.getMessage().getBody().toBytes(), byteBody2));

            assertTrue(byteBody1Exists, "test byte body 1");
            assertTrue(byteBody2Exists, "test byte body 2");
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // No Routes required for this test
            }
        };
    }
}
