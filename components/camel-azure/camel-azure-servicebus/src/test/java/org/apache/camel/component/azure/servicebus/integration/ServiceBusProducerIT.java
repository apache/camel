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
package org.apache.camel.component.azure.servicebus.integration;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.azure.servicebus.ServiceBusConstants;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = BaseServiceBusTestSupport.CONNECTION_STRING_PROPERTY_NAME, matches = ".*",
                         disabledReason = "Service Bus connection string must be supplied to run this test, e.g:  mvn verify -D"
                                          + BaseServiceBusTestSupport.CONNECTION_STRING_PROPERTY_NAME + "=connectionString")
public class ServiceBusProducerIT extends BaseServiceBusTestSupport {
    private static final String DIRECT_SEND_TO_QUEUE_URI = "direct:sendToQueue";
    private static final String DIRECT_SEND_TO_TOPIC_URI = "direct:sendToTopic";
    private static final String DIRECT_SEND_SCHEDULED_URI = "direct:sendScheduled";
    private static final String DIRECT_SEND_SCHEDULED_URI_WITH_SESSIONS = "direct:sendScheduledSessions";
    private static final String DIRECT_SEND_TO_TOPIC_SESSION_URI = "direct:sendToTopicSessions";
    private static final String DIRECT_SEND_TO_SESSION_QUEUE_URI = "direct:sendToQueueSessions";
    private static final Map<String, Object> PROPAGATED_HEADERS = new HashMap<>();
    private static final Pattern MESSAGE_BODY_PATTERN = Pattern.compile("^message-[0-4]$");

    static {
        PROPAGATED_HEADERS.put("booleanHeader", true);
        PROPAGATED_HEADERS.put("byteHeader", (byte) 1);
        PROPAGATED_HEADERS.put("characterHeader", '1');
        PROPAGATED_HEADERS.put("doubleHeader", 1.0D);
        PROPAGATED_HEADERS.put("floatHeader", 1.0F);
        PROPAGATED_HEADERS.put("integerHeader", 1);
        PROPAGATED_HEADERS.put("longHeader", 1L);
        PROPAGATED_HEADERS.put("shortHeader", (short) 1);
        PROPAGATED_HEADERS.put("stringHeader", "stringHeader");
        PROPAGATED_HEADERS.put("timestampHeader", new Date());
        PROPAGATED_HEADERS.put("uuidHeader", UUID.randomUUID());
    }

    private ProducerTemplate producerTemplate;

    @BeforeEach
    void beforeEach() {
        producerTemplate = contextExtension.getProducerTemplate();
        receivedMessageContexts = new ArrayList<>();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(DIRECT_SEND_TO_QUEUE_URI)
                        .to("azure-servicebus:" + QUEUE_NAME);

                from(DIRECT_SEND_TO_SESSION_QUEUE_URI)
                        .to("azure-servicebus:" + QUEUE_WITH_SESSIONS_NAME + "?sessionId=123");

                from(DIRECT_SEND_TO_TOPIC_URI)
                        .to("azure-servicebus:" + TOPIC_NAME + "?serviceBusType=topic");

                from(DIRECT_SEND_TO_TOPIC_SESSION_URI)
                        .to("azure-servicebus:" + TOPIC_WITH_SESSIONS_NAME + "?serviceBusType=topic&sessionId=123");

                from(DIRECT_SEND_SCHEDULED_URI)
                        .to("azure-servicebus:" + QUEUE_NAME + "?producerOperation=scheduleMessages");

                from(DIRECT_SEND_SCHEDULED_URI_WITH_SESSIONS)
                        .to("azure-servicebus:" + QUEUE_WITH_SESSIONS_NAME + "?producerOperation=scheduleMessages&sessionId=123");
            }
        };
    }

    @RouteFixture
    public void createRouteBuilder(CamelContext context) throws Exception {
        context.addRoutes(createRouteBuilder());
    }

    @Test
    void camelSendsMessageToServiceBusQueue() throws InterruptedException {
        messageLatch = new CountDownLatch(5);
        try (ServiceBusProcessorClient client = createQueueProcessorClient()) {
            client.start();
            for (int i = 0; i < 5; i++) {
                String message = "message-" + i;
                producerTemplate.sendBodyAndHeaders(DIRECT_SEND_TO_QUEUE_URI, message, PROPAGATED_HEADERS);
            }

            assertTrue(messageLatch.await(3000, TimeUnit.MILLISECONDS));
            assertEquals(5, receivedMessageContexts.size());
            receivedMessageContexts.forEach(messageContext -> {
                ServiceBusReceivedMessage message = messageContext.getMessage();
                String messageBody = message.getBody().toString();
                assertTrue(MESSAGE_BODY_PATTERN.matcher(messageBody).matches());
                Map<String, Object> applicationProperties = message.getApplicationProperties();
                assertEquals(PROPAGATED_HEADERS, applicationProperties);
            });
        }
    }

    @Test
    void camelSendsMessageBatchToServiceBusQueue() throws InterruptedException {
        messageLatch = new CountDownLatch(5);
        try (ServiceBusProcessorClient client = createQueueProcessorClient()) {
            client.start();
            List<Object> messageBatch = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                String message = "message-" + i;
                messageBatch.add(message);
            }

            producerTemplate.send(DIRECT_SEND_TO_QUEUE_URI, exchange -> {
                exchange.getIn().setHeaders(PROPAGATED_HEADERS);
                exchange.getIn().setBody(messageBatch);
            });

            assertTrue(messageLatch.await(3000, TimeUnit.MILLISECONDS));
            assertEquals(5, receivedMessageContexts.size());
            receivedMessageContexts.forEach(messageContext -> {
                ServiceBusReceivedMessage message = messageContext.getMessage();
                String messageBody = message.getBody().toString();
                assertTrue(MESSAGE_BODY_PATTERN.matcher(messageBody).matches());
                Map<String, Object> applicationProperties = message.getApplicationProperties();
                assertEquals(PROPAGATED_HEADERS, applicationProperties);
            });
        }
    }

    @Test
    void camelSendsMessageToServiceBusTopic() throws InterruptedException {
        messageLatch = new CountDownLatch(5);
        try (ServiceBusProcessorClient client = createTopicProcessorClient()) {
            client.start();
            for (int i = 0; i < 5; i++) {
                String message = "message-" + i;
                producerTemplate.sendBodyAndHeaders(DIRECT_SEND_TO_TOPIC_URI, message, PROPAGATED_HEADERS);
            }

            assertTrue(messageLatch.await(3000, TimeUnit.MILLISECONDS));
            assertEquals(5, receivedMessageContexts.size());
            receivedMessageContexts.forEach(messageContext -> {
                ServiceBusReceivedMessage message = messageContext.getMessage();
                String messageBody = message.getBody().toString();
                assertTrue(MESSAGE_BODY_PATTERN.matcher(messageBody).matches());
                Map<String, Object> applicationProperties = message.getApplicationProperties();
                assertEquals(PROPAGATED_HEADERS, applicationProperties);
            });
        }
    }

    @Test
    void camelSchedulesServiceBusMessage() throws InterruptedException {
        messageLatch = new CountDownLatch(5);
        try (ServiceBusProcessorClient client = createQueueProcessorClient()) {
            client.start();
            for (int i = 0; i < 5; i++) {
                String message = "message-" + i;
                Map<String, Object> headers = new HashMap<>(PROPAGATED_HEADERS);
                headers.put(ServiceBusConstants.SCHEDULED_ENQUEUE_TIME, OffsetDateTime.now().plusSeconds(1));
                producerTemplate.sendBodyAndHeaders(DIRECT_SEND_SCHEDULED_URI, message, headers);
            }

            assertTrue(messageLatch.await(4000, TimeUnit.MILLISECONDS));
            assertEquals(5, receivedMessageContexts.size());
            receivedMessageContexts.forEach(messageContext -> {
                ServiceBusReceivedMessage message = messageContext.getMessage();
                String messageBody = message.getBody().toString();
                assertTrue(MESSAGE_BODY_PATTERN.matcher(messageBody).matches());
                Map<String, Object> applicationProperties = message.getApplicationProperties();
                assertEquals(PROPAGATED_HEADERS, applicationProperties);
                assertInstanceOf(OffsetDateTime.class, message.getScheduledEnqueueTime());
            });
        }
    }

    @Test
    void camelSchedulesServiceBusMessageBatch() throws InterruptedException {
        messageLatch = new CountDownLatch(5);
        try (ServiceBusProcessorClient client = createQueueProcessorClient()) {
            client.start();
            List<Object> messageBatch = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                String message = "message-" + i;
                messageBatch.add(message);
            }

            producerTemplate.send(DIRECT_SEND_SCHEDULED_URI, exchange -> {
                Map<String, Object> headers = new HashMap<>(PROPAGATED_HEADERS);
                headers.put(ServiceBusConstants.SCHEDULED_ENQUEUE_TIME, OffsetDateTime.now().plusSeconds(1));
                exchange.getIn().setHeaders(headers);
                exchange.getIn().setBody(messageBatch);
            });

            assertTrue(messageLatch.await(10000, TimeUnit.MILLISECONDS));
            assertEquals(5, receivedMessageContexts.size());
            receivedMessageContexts.forEach(messageContext -> {
                ServiceBusReceivedMessage message = messageContext.getMessage();
                String messageBody = message.getBody().toString();
                assertTrue(MESSAGE_BODY_PATTERN.matcher(messageBody).matches());
                Map<String, Object> applicationProperties = message.getApplicationProperties();
                assertEquals(PROPAGATED_HEADERS, applicationProperties);
                assertInstanceOf(OffsetDateTime.class, message.getScheduledEnqueueTime());
            });
        }
    }

    /*
        Tests for entities with session support
     */

    @Test
    void camelSendsMessageToServiceBusSessionEnabledQueue() throws InterruptedException {
        messageLatch = new CountDownLatch(5);
        try (ServiceBusProcessorClient client = createSessionQueueProcessorClient()) {
            client.start();
            for (int i = 0; i < 5; i++) {
                String message = "message-" + i;
                producerTemplate.sendBodyAndHeaders(DIRECT_SEND_TO_SESSION_QUEUE_URI, message, PROPAGATED_HEADERS);
            }

            assertTrue(messageLatch.await(3000, TimeUnit.MILLISECONDS));
            assertEquals(5, receivedMessageContexts.size());
            receivedMessageContexts.forEach(messageContext -> {
                ServiceBusReceivedMessage message = messageContext.getMessage();
                String messageBody = message.getBody().toString();
                assertTrue(MESSAGE_BODY_PATTERN.matcher(messageBody).matches());
                Map<String, Object> applicationProperties = message.getApplicationProperties();
                assertEquals(PROPAGATED_HEADERS, applicationProperties);
            });
        }
    }

    @Test
    void camelSendsMessageBatchToServiceBusSessionEnabledQueue() throws InterruptedException {
        messageLatch = new CountDownLatch(5);
        try (ServiceBusProcessorClient client = createSessionQueueProcessorClient()) {
            client.start();
            List<Object> messageBatch = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                String message = "message-" + i;
                messageBatch.add(message);
            }

            producerTemplate.send(DIRECT_SEND_TO_SESSION_QUEUE_URI, exchange -> {
                exchange.getIn().setHeaders(PROPAGATED_HEADERS);
                exchange.getIn().setBody(messageBatch);
            });

            assertTrue(messageLatch.await(3000, TimeUnit.MILLISECONDS));
            assertEquals(5, receivedMessageContexts.size());
            receivedMessageContexts.forEach(messageContext -> {
                ServiceBusReceivedMessage message = messageContext.getMessage();
                String messageBody = message.getBody().toString();
                assertTrue(MESSAGE_BODY_PATTERN.matcher(messageBody).matches());
                Map<String, Object> applicationProperties = message.getApplicationProperties();
                assertEquals(PROPAGATED_HEADERS, applicationProperties);
            });
        }
    }

    @Test
    void camelSendsMessageWithSessionToServiceBusTopic() throws InterruptedException {
        messageLatch = new CountDownLatch(5);
        try (ServiceBusProcessorClient client = createTopicSessionProcessorClient()) {
            client.start();
            for (int i = 0; i < 5; i++) {
                String message = "message-" + i;
                producerTemplate.sendBodyAndHeaders(DIRECT_SEND_TO_TOPIC_SESSION_URI, message, PROPAGATED_HEADERS);
            }

            assertTrue(messageLatch.await(5000, TimeUnit.MILLISECONDS));
            assertEquals(5, receivedMessageContexts.size());
            receivedMessageContexts.forEach(messageContext -> {
                ServiceBusReceivedMessage message = messageContext.getMessage();
                String messageBody = message.getBody().toString();
                assertTrue(MESSAGE_BODY_PATTERN.matcher(messageBody).matches());
            });
        }
    }

    @Test
    void camelSchedulesServiceBusMessageWithSessions() throws InterruptedException {
        messageLatch = new CountDownLatch(5);
        try (ServiceBusProcessorClient client = createSessionQueueProcessorClient()) {
            client.start();
            for (int i = 0; i < 5; i++) {
                String message = "message-" + i;
                Map<String, Object> headers = new HashMap<>(PROPAGATED_HEADERS);
                headers.put(ServiceBusConstants.SCHEDULED_ENQUEUE_TIME, OffsetDateTime.now().plusSeconds(1));
                producerTemplate.sendBodyAndHeaders(DIRECT_SEND_SCHEDULED_URI_WITH_SESSIONS, message, headers);
            }

            assertTrue(messageLatch.await(4000, TimeUnit.MILLISECONDS));
            assertEquals(5, receivedMessageContexts.size());
            receivedMessageContexts.forEach(messageContext -> {
                ServiceBusReceivedMessage message = messageContext.getMessage();
                String messageBody = message.getBody().toString();
                assertTrue(MESSAGE_BODY_PATTERN.matcher(messageBody).matches());
                Map<String, Object> applicationProperties = message.getApplicationProperties();
                assertEquals(PROPAGATED_HEADERS, applicationProperties);
                assertInstanceOf(OffsetDateTime.class, message.getScheduledEnqueueTime());
            });
        }
    }

    @Test
    void camelSchedulesServiceBusMessageBatchWIthSessions() throws InterruptedException {
        messageLatch = new CountDownLatch(5);
        try (ServiceBusProcessorClient client = createSessionQueueProcessorClient()) {
            client.start();
            List<Object> messageBatch = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                String message = "message-" + i;
                messageBatch.add(message);
            }

            producerTemplate.send(DIRECT_SEND_SCHEDULED_URI_WITH_SESSIONS, exchange -> {
                Map<String, Object> headers = new HashMap<>(PROPAGATED_HEADERS);
                headers.put(ServiceBusConstants.SCHEDULED_ENQUEUE_TIME, OffsetDateTime.now().plusSeconds(1));
                exchange.getIn().setHeaders(headers);
                exchange.getIn().setBody(messageBatch);
            });

            assertTrue(messageLatch.await(10000, TimeUnit.MILLISECONDS));
            assertEquals(5, receivedMessageContexts.size());
            receivedMessageContexts.forEach(messageContext -> {
                ServiceBusReceivedMessage message = messageContext.getMessage();
                String messageBody = message.getBody().toString();
                assertTrue(MESSAGE_BODY_PATTERN.matcher(messageBody).matches());
                Map<String, Object> applicationProperties = message.getApplicationProperties();
                assertEquals(PROPAGATED_HEADERS, applicationProperties);
                assertInstanceOf(OffsetDateTime.class, message.getScheduledEnqueueTime());
            });
        }
    }
}
