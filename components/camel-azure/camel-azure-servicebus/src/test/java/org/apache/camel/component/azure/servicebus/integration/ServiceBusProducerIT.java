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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.azure.servicebus.ServiceBusConstants;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = BaseServiceBusTestSupport.CONNECTION_STRING_PROPERTY_NAME, matches = ".*",
                         disabledReason = "Service Bus connection string must be supplied to run this test, e.g:  mvn verify -D"
                                          + BaseServiceBusTestSupport.CONNECTION_STRING_PROPERTY_NAME + "=connectionString")
public class ServiceBusProducerIT extends BaseServiceBusTestSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceBusProducerIT.class);
    private static final String DIRECT_SEND_TO_QUEUE_URI = "direct:sendToQueue";
    private static final String DIRECT_SEND_TO_TOPIC_URI = "direct:sendToTopic";
    private static final String DIRECT_SEND_SCHEDULED_URI = "direct:sendScheduled";
    private static final String PROPAGATED_HEADER_KEY = "PropagatedCustomHeader";
    private static final String PROPAGATED_HEADER_VALUE = "propagated header value";
    private static final Pattern MESSAGE_BODY_PATTERN = Pattern.compile("^message-[0-4]$");
    private ProducerTemplate producerTemplate;
    private CountDownLatch messageLatch;
    private List<ServiceBusReceivedMessageContext> receivedMessageContexts;

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

                from(DIRECT_SEND_TO_TOPIC_URI)
                        .to("azure-servicebus:" + TOPIC_NAME + "?serviceBusType=topic");

                from(DIRECT_SEND_SCHEDULED_URI)
                        .to("azure-servicebus:" + QUEUE_NAME + "?producerOperation=scheduleMessages");
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
                producerTemplate.sendBodyAndHeader(DIRECT_SEND_TO_QUEUE_URI, message, PROPAGATED_HEADER_KEY,
                        PROPAGATED_HEADER_VALUE);
            }

            assertTrue(messageLatch.await(3000, TimeUnit.MILLISECONDS));
            assertEquals(5, receivedMessageContexts.size());
            receivedMessageContexts.forEach(messageContext -> {
                ServiceBusReceivedMessage message = messageContext.getMessage();
                String messageBody = message.getBody().toString();
                assertTrue(MESSAGE_BODY_PATTERN.matcher(messageBody).matches());
                Map<String, Object> applicationProperties = message.getApplicationProperties();
                assertEquals(1, applicationProperties.size());
                assertEquals(PROPAGATED_HEADER_VALUE, applicationProperties.get(PROPAGATED_HEADER_KEY));
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
                exchange.getIn().setHeader(PROPAGATED_HEADER_KEY, PROPAGATED_HEADER_VALUE);
                exchange.getIn().setBody(messageBatch);
            });

            assertTrue(messageLatch.await(3000, TimeUnit.MILLISECONDS));
            assertEquals(5, receivedMessageContexts.size());
            receivedMessageContexts.forEach(messageContext -> {
                ServiceBusReceivedMessage message = messageContext.getMessage();
                String messageBody = message.getBody().toString();
                assertTrue(MESSAGE_BODY_PATTERN.matcher(messageBody).matches());
                Map<String, Object> applicationProperties = message.getApplicationProperties();
                assertEquals(1, applicationProperties.size());
                assertEquals(PROPAGATED_HEADER_VALUE, applicationProperties.get(PROPAGATED_HEADER_KEY));
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
                producerTemplate.sendBodyAndHeader(DIRECT_SEND_TO_TOPIC_URI, message, PROPAGATED_HEADER_KEY,
                        PROPAGATED_HEADER_VALUE);
            }

            assertTrue(messageLatch.await(3000, TimeUnit.MILLISECONDS));
            assertEquals(5, receivedMessageContexts.size());
            receivedMessageContexts.forEach(messageContext -> {
                ServiceBusReceivedMessage message = messageContext.getMessage();
                String messageBody = message.getBody().toString();
                assertTrue(MESSAGE_BODY_PATTERN.matcher(messageBody).matches());
                Map<String, Object> applicationProperties = message.getApplicationProperties();
                assertEquals(1, applicationProperties.size());
                assertEquals(PROPAGATED_HEADER_VALUE, applicationProperties.get(PROPAGATED_HEADER_KEY));
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
                Map<String, Object> headers = new HashMap<>();
                headers.put(PROPAGATED_HEADER_KEY, PROPAGATED_HEADER_VALUE);
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
                assertEquals(1, applicationProperties.size());
                assertEquals(PROPAGATED_HEADER_VALUE, applicationProperties.get(PROPAGATED_HEADER_KEY));
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
                exchange.getIn().setHeader(ServiceBusConstants.SCHEDULED_ENQUEUE_TIME, OffsetDateTime.now().plusSeconds(1));
                exchange.getIn().setHeader(PROPAGATED_HEADER_KEY, PROPAGATED_HEADER_VALUE);
                exchange.getIn().setBody(messageBatch);
            });

            assertTrue(messageLatch.await(10000, TimeUnit.MILLISECONDS));
            assertEquals(5, receivedMessageContexts.size());
            receivedMessageContexts.forEach(messageContext -> {
                ServiceBusReceivedMessage message = messageContext.getMessage();
                String messageBody = message.getBody().toString();
                assertTrue(MESSAGE_BODY_PATTERN.matcher(messageBody).matches());
                Map<String, Object> applicationProperties = message.getApplicationProperties();
                assertEquals(1, applicationProperties.size());
                assertEquals(PROPAGATED_HEADER_VALUE, applicationProperties.get(PROPAGATED_HEADER_KEY));
                assertInstanceOf(OffsetDateTime.class, message.getScheduledEnqueueTime());
            });
        }
    }

    private void processMessage(ServiceBusReceivedMessageContext messageContext) {
        receivedMessageContexts.add(messageContext);
        messageLatch.countDown();
    }

    private ServiceBusProcessorClient createQueueProcessorClient() {
        return new ServiceBusClientBuilder()
                .connectionString(CONNECTION_STRING)
                .processor()
                .queueName(QUEUE_NAME)
                .processMessage(this::processMessage)
                .processError(serviceBusErrorContext -> LOGGER.error("Service Bus client error",
                        serviceBusErrorContext.getException()))
                .buildProcessorClient();
    }

    private ServiceBusProcessorClient createTopicProcessorClient() {
        return new ServiceBusClientBuilder()
                .connectionString(CONNECTION_STRING)
                .processor()
                .topicName(TOPIC_NAME)
                .subscriptionName(SUBSCRIPTION_NAME)
                .processMessage(this::processMessage)
                .processError(serviceBusErrorContext -> LOGGER.error("Service Bus client error",
                        serviceBusErrorContext.getException()))
                .buildProcessorClient();
    }
}
