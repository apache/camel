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

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import com.azure.core.amqp.models.AmqpAnnotatedMessage;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.azure.servicebus.ServiceBusConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.*;

public class ServiceBusConsumerIT extends BaseServiceBusTestSupport {
    private static final String SESSION_LOCKED_UNTIL_NOT_SET
            = "Floci AZ does not set the lock expiry (the x-opt-locked-until annotation) on the messages of a session"
              + " entity: set " + CONNECTION_STRING_PROPERTY_NAME + " to run this test against a Service Bus namespace";

    private final ServiceBusSenderClient queueSenderClient = new ServiceBusClientBuilder()
            .connectionString(connectionString())
            .sender()
            .queueName(QUEUE_NAME)
            .buildClient();

    private final ServiceBusSenderClient queueWithSessionsSenderClient = new ServiceBusClientBuilder()
            .connectionString(connectionString())
            .sender()
            .queueName(QUEUE_WITH_SESSIONS_NAME)
            .buildClient();

    private final ServiceBusSenderClient topicSenderClient = new ServiceBusClientBuilder()
            .connectionString(connectionString())
            .sender()
            .topicName(TOPIC_NAME)
            .buildClient();

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("azure-servicebus:" + QUEUE_NAME)
                        .to(MOCK_RESULT);

                from("azure-servicebus:" + QUEUE_WITH_SESSIONS_NAME + "?sessionEnabled=true")
                        .to(MOCK_RESULT);

                from("azure-servicebus:" + TOPIC_NAME + "?serviceBusType=topic&subscriptionName=" + SUBSCRIPTION_NAME)
                        .to(MOCK_RESULT);

                from("azure-servicebus:" + TOPIC_WITH_SESSIONS_NAME + "?serviceBusType=topic&subscriptionName="
                     + SUBSCRIPTION_WITH_SESSIONS_NAME + "?sessionEnabled=true")
                        .to(MOCK_RESULT);
            }
        };
    }

    @RouteFixture
    public void createRouteBuilder(CamelContext context) throws Exception {
        context.addRoutes(createRouteBuilder());
    }

    @Test
    public void serviceBusQueueIsConsumedByCamel() throws InterruptedException {
        String propagatedHeaderKey = "PropagatedCustomHeader";
        String propagatedHeaderValue = "propagated header value";

        MockEndpoint to = contextExtension.getMockEndpoint(MOCK_RESULT);

        to.expectedMessageCount(5);
        to.expectedBodiesReceivedInAnyOrder("message-0", "message-1", "message-2", "message-3", "message-4");
        to.expectedHeaderReceived(propagatedHeaderKey, propagatedHeaderValue);

        for (int k = 0; k < 5; k++) {
            String msg = "message-" + k;
            ServiceBusMessage serviceBusMessage = createMessage(msg);
            serviceBusMessage.getApplicationProperties().put(propagatedHeaderKey, propagatedHeaderValue);
            queueSenderClient.sendMessage(serviceBusMessage);
        }

        to.assertIsSatisfied(3000);

        Map<String, Object> headers = to.getExchanges().get(0).getIn().getHeaders();
        assertTrue(headers.containsKey(propagatedHeaderKey), "Should receive propagated header");
        assertBrokerPropertyHeadersPropagated(headers);
    }

    @Test
    public void serviceBusTopicIsConsumedByCamel() throws InterruptedException {
        String propagatedHeaderKey = "PropagatedCustomHeader";
        String propagatedHeaderValue = "propagated header value";

        MockEndpoint to = contextExtension.getMockEndpoint(MOCK_RESULT);

        to.expectedMessageCount(5);
        to.expectedBodiesReceivedInAnyOrder("message-0", "message-1", "message-2", "message-3", "message-4");
        to.expectedHeaderReceived(propagatedHeaderKey, propagatedHeaderValue);

        for (int k = 0; k < 5; k++) {
            String msg = "message-" + k;
            ServiceBusMessage serviceBusMessage = createMessage(msg);
            serviceBusMessage.getApplicationProperties().put(propagatedHeaderKey, propagatedHeaderValue);
            topicSenderClient.sendMessage(serviceBusMessage);
        }

        to.assertIsSatisfied(3000);

        Map<String, Object> headers = to.getExchanges().get(0).getIn().getHeaders();
        assertTrue(headers.containsKey(propagatedHeaderKey), "Should receive propagated header");
        assertBrokerPropertyHeadersPropagated(headers);
    }

    /*
        Tests for entities with session support
    */

    @Test
    @EnabledIfSystemProperty(named = CONNECTION_STRING_PROPERTY_NAME, matches = ".+",
                             disabledReason = SESSION_LOCKED_UNTIL_NOT_SET)
    public void serviceBusSessionEnabledQueueIsConsumedByCamel() throws InterruptedException {
        String propagatedHeaderKey = "PropagatedCustomHeader";
        String propagatedHeaderValue = "propagated header value";

        MockEndpoint to = contextExtension.getMockEndpoint(MOCK_RESULT);

        to.expectedMessageCount(5);
        to.expectedBodiesReceived("message-0", "message-1", "message-2", "message-3", "message-4");
        to.expectedHeaderReceived(propagatedHeaderKey, propagatedHeaderValue);

        for (int k = 0; k < 5; k++) {
            String msg = "message-" + k;
            ServiceBusMessage serviceBusMessage = createMessage(msg);
            serviceBusMessage.setSessionId("session-1");
            serviceBusMessage.getApplicationProperties().put(propagatedHeaderKey, propagatedHeaderValue);
            queueWithSessionsSenderClient.sendMessage(serviceBusMessage);
        }

        to.assertIsSatisfied(3000);

        Map<String, Object> headers = to.getExchanges().get(0).getIn().getHeaders();
        assertTrue(headers.containsKey(propagatedHeaderKey), "Should receive propagated header");
        assertBrokerPropertyHeadersPropagated(headers);
    }

    @Test
    public void serviceBusSessionEnabledTopicIsConsumedByCamel() throws InterruptedException {
        String propagatedHeaderKey = "PropagatedCustomHeader";
        String propagatedHeaderValue = "propagated header value";

        MockEndpoint to = contextExtension.getMockEndpoint(MOCK_RESULT);

        to.expectedMessageCount(5);
        to.expectedBodiesReceived("message-0", "message-1", "message-2", "message-3", "message-4");
        to.expectedHeaderReceived(propagatedHeaderKey, propagatedHeaderValue);

        for (int k = 0; k < 5; k++) {
            String msg = "message-" + k;
            ServiceBusMessage serviceBusMessage = createMessage(msg);
            serviceBusMessage.setSessionId("session-1");
            serviceBusMessage.getApplicationProperties().put(propagatedHeaderKey, propagatedHeaderValue);
            topicSenderClient.sendMessage(serviceBusMessage);
        }

        to.assertIsSatisfied(3000);

        Map<String, Object> headers = to.getExchanges().get(0).getIn().getHeaders();
        assertTrue(headers.containsKey(propagatedHeaderKey), "Should receive propagated header");
        assertBrokerPropertyHeadersPropagated(headers);
    }

    private static ServiceBusMessage createMessage(String body) {
        // the message ID is application-defined: Azure assigns one to a message sent without it, Floci AZ does not
        return new ServiceBusMessage(body).setMessageId(UUID.randomUUID().toString());
    }

    private void assertBrokerPropertyHeadersPropagated(Map<String, Object> headers) {
        assertInstanceOf(Map.class, headers.get(ServiceBusConstants.APPLICATION_PROPERTIES),
                "Should receive application properties");
        assertTrue(headers.containsKey(ServiceBusConstants.CONTENT_TYPE), "Should receive content type");
        assertInstanceOf(String.class, headers.get(ServiceBusConstants.MESSAGE_ID), "Should receive message ID");
        assertTrue(headers.containsKey(ServiceBusConstants.CORRELATION_ID), "Should receive correlation ID");
        assertTrue(headers.containsKey(ServiceBusConstants.DEAD_LETTER_ERROR_DESCRIPTION),
                "Should receive dead letter error description");
        assertTrue(headers.containsKey(ServiceBusConstants.DEAD_LETTER_REASON), "Should receive dead letter reason");
        assertTrue(headers.containsKey(ServiceBusConstants.DEAD_LETTER_SOURCE), "Should receive dead letter source");
        assertInstanceOf(Long.class, headers.get(ServiceBusConstants.DELIVERY_COUNT), "Should receive delivery count");
        assertTrue(headers.containsKey(ServiceBusConstants.SCHEDULED_ENQUEUE_TIME), "Should receive scheduled enqueue time");
        assertInstanceOf(Long.class, headers.get(ServiceBusConstants.ENQUEUED_SEQUENCE_NUMBER),
                "Should receive enqueued sequence number");
        assertInstanceOf(OffsetDateTime.class, headers.get(ServiceBusConstants.ENQUEUED_TIME), "Should receive enqueued time");
        assertInstanceOf(OffsetDateTime.class, headers.get(ServiceBusConstants.EXPIRES_AT), "Should receive expiry time");
        assertInstanceOf(String.class, headers.get(ServiceBusConstants.LOCK_TOKEN), "Should receive lock token");
        assertInstanceOf(OffsetDateTime.class, headers.get(ServiceBusConstants.LOCKED_UNTIL),
                "Should receive locked until time");
        assertTrue(headers.containsKey(ServiceBusConstants.PARTITION_KEY), "Should receive partition key");
        assertInstanceOf(AmqpAnnotatedMessage.class, headers.get(ServiceBusConstants.RAW_AMQP_MESSAGE),
                "Should receive raw AMQP message");
        assertTrue(headers.containsKey(ServiceBusConstants.REPLY_TO), "Should receive reply to property");
        assertTrue(headers.containsKey(ServiceBusConstants.REPLY_TO_SESSION_ID), "Should receive reply to session ID");
        assertInstanceOf(Long.class, headers.get(ServiceBusConstants.SEQUENCE_NUMBER), "Should receive sequence number");
        assertTrue(headers.containsKey(ServiceBusConstants.SESSION_ID), "Should receive session ID");
        assertTrue(headers.containsKey(ServiceBusConstants.SUBJECT), "Should receive subject");
        assertInstanceOf(Duration.class, headers.get(ServiceBusConstants.TIME_TO_LIVE), "Should receive time to live");
        assertTrue(headers.containsKey(ServiceBusConstants.TO), "Should receive to property");
    }
}
